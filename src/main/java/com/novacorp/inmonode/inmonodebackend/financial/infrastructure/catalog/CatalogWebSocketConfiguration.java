package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.catalog;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Configuration
@EnableWebSocket
public class CatalogWebSocketConfiguration implements WebSocketConfigurer {
    private final CatalogWebSocketHandler handler;
    private final ProjectRepository projects;
    private final String[] allowedOrigins;

    public CatalogWebSocketConfiguration(CatalogWebSocketHandler handler, ProjectRepository projects,
            @Value("${authorization.cors.allowed-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.projects = projects;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins).map(String::trim).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/projects/{projectId}/events")
                .setAllowedOrigins(allowedOrigins).addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        var parts = request.getURI().getPath().split("/");
                        try {
                            var projectId = Long.valueOf(parts[parts.length - 2]);
                            if (projects.findById(projectId).filter(Project::isPublished).isEmpty()) {
                                response.setStatusCode(HttpStatus.NOT_FOUND);
                                return false;
                            }
                            attributes.put("projectId", projectId);
                            return true;
                        } catch (NumberFormatException e) {
                            response.setStatusCode(HttpStatus.BAD_REQUEST);
                            return false;
                        }
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) {}
                });
    }
}
