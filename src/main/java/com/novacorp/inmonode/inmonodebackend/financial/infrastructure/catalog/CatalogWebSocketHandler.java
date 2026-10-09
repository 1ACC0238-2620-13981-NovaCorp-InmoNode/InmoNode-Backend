package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogChangedEvent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/** Public availability events contain no buyer, reservation or payment data. */
@Component
public class CatalogWebSocketHandler extends TextWebSocketHandler {
    private record Client(WebSocketSession session, Long projectId, java.util.concurrent.atomic.AtomicLong lastPing) {}
    private final ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<>();
    private final Clock clock;
    private final ProjectRepository projects;

    public CatalogWebSocketHandler(Clock clock, ProjectRepository projects) {
        this.clock = clock;
        this.projects = projects;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (clients.size() >= 1000) { session.close(CloseStatus.SERVICE_OVERLOAD); return; }
        var wrapped = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        var projectId = (Long) session.getAttributes().get("projectId");
        clients.put(session.getId(), new Client(wrapped, projectId, new java.util.concurrent.atomic.AtomicLong(clock.millis())));
        wrapped.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"heartbeatSeconds\":30}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        var client = clients.get(session.getId());
        if (client == null) return;
        if (!"ping".equalsIgnoreCase(message.getPayload()) && !"pong".equalsIgnoreCase(message.getPayload())) {
            close(client, CloseStatus.POLICY_VIOLATION);
            return;
        }
        client.lastPing().set(clock.millis());
        if ("ping".equalsIgnoreCase(message.getPayload())) client.session().sendMessage(new TextMessage("pong"));
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        var client = clients.get(session.getId());
        if (client != null) client.lastPing().set(clock.millis());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { clients.remove(session.getId()); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) { 
        var client = clients.get(session.getId());
        if (client != null) close(client, CloseStatus.SERVER_ERROR);
    }

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        for (var client : clients.values()) {
            if (clock.millis() - client.lastPing().get() >= 30_000) close(client, CloseStatus.GOING_AWAY);
            else try { client.session().sendMessage(new PingMessage(ByteBuffer.wrap(new byte[]{1}))); }
            catch (IOException | RuntimeException e) { close(client, CloseStatus.SERVER_ERROR); }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(CatalogChangedEvent event) {
        if (event.lotId() == null || "DRAFT".equals(event.status()) || clients.isEmpty()) return;
        if (projects.findById(event.projectId()).filter(Project::isPublished).isEmpty()) return;
        var message = new TextMessage("{\"type\":\"LOT_UPDATED\",\"projectId\":" + event.projectId()
                + ",\"lotId\":" + event.lotId() + ",\"status\":\"" + event.status()
                + "\",\"changedAt\":\"" + event.changedAt() + "\"}");
        for (var client : clients.values()) {
            if (event.projectId().equals(client.projectId())) {
                try { client.session().sendMessage(message); }
                catch (IOException | RuntimeException e) { close(client, CloseStatus.SERVER_ERROR); }
            }
        }
    }

    private void close(Client client, CloseStatus status) {
        clients.remove(client.session().getId());
        try { client.session().close(status); } catch (IOException ignored) { /* Already disconnected. */ }
    }
}
