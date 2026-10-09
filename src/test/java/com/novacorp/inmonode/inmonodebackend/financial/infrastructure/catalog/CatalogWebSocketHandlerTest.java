package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogChangedEvent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogWebSocketHandlerTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final Clock clock = mock(Clock.class);
    private final CatalogWebSocketHandler handler = new CatalogWebSocketHandler(clock, projects);

    private WebSocketSession session(String id, Long projectId) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("projectId", projectId));
        return session;
    }

    @Test void availabilityIsBroadcastOnlyToItsPublishedProjectWithoutPrivateData() throws Exception {
        var project = mock(Project.class);
        when(project.isPublished()).thenReturn(true);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        var first = session("first", 1L);
        var other = session("other", 2L);
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(other);
        clearInvocations(first, other);
        handler.afterCommit(new CatalogChangedEvent(1L, 3L, "BLOCKED", Instant.EPOCH));
        var captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(first).sendMessage(captor.capture());
        var json = captor.getValue().getPayload().toString();
        assertTrue(json.contains("LOT_UPDATED"));
        assertTrue(json.contains("BLOCKED"));
        assertFalse(json.contains("buyer"));
        assertFalse(json.contains("reservation"));
        verify(other, never()).sendMessage(any());
    }

    @Test void draftProjectsAreNeverBroadcast() throws Exception {
        when(projects.findById(1L)).thenReturn(Optional.of(mock(Project.class)));
        var session = session("one", 1L);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        handler.afterCommit(new CatalogChangedEvent(1L, 3L, "AVAILABLE", Instant.EPOCH));
        verify(session, never()).sendMessage(any());
    }

    @Test void thirtySecondsWithoutPingClosesTheConnection() throws Exception {
        var session = session("one", 1L);
        when(clock.millis()).thenReturn(0L);
        handler.afterConnectionEstablished(session);
        when(clock.millis()).thenReturn(29999L);
        handler.heartbeat();
        verify(session, never()).close(any());
        when(clock.millis()).thenReturn(30000L);
        handler.heartbeat();
        verify(session).close(CloseStatus.GOING_AWAY);
    }

    @Test void pingRenewsTheDeadlineAndClientUpdatesAreRejected() throws Exception {
        var session = session("one", 1L);
        when(clock.millis()).thenReturn(0L);
        handler.afterConnectionEstablished(session);
        when(clock.millis()).thenReturn(25000L);
        handler.handleTextMessage(session, new TextMessage("ping"));
        when(clock.millis()).thenReturn(40000L);
        handler.heartbeat();
        verify(session, never()).close(any());
        handler.handleTextMessage(session, new TextMessage("{\"status\":\"SOLD\"}"));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
}
