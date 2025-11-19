package com.htpt.migration.service;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
@Profile("worker")
@Slf4j
@RequiredArgsConstructor
public class WorkerNodeService {

    @Value("${NODE_ID:worker}")
    private String nodeId;

    @Value("${NODE_HOST:localhost}")
    private String nodeHost;

    @Value("${NODE_PORT:8080}")
    private int nodePort;

    @Value("${COORDINATOR_URL:http://localhost:8080}")
    private String coordinatorUrl;

    private final CodeExecutorService codeExecutorService;
    private StompSession stompSession;
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        connectToCoordinator();
    }

    private void connectToCoordinator() {
        try {
            WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            String wsUrl = coordinatorUrl.replace("http://", "ws://") + "/ws/websocket";
            log.info("Connecting to coordinator: {}", wsUrl);

            stompSession = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    log.info("Connected to coordinator!");
                    registerNode();
                    subscribeToEvents();
                }

                @Override
                public void handleException(StompSession session, StompCommand command,
                        StompHeaders headers, byte[] payload, Throwable exception) {
                    log.error("WebSocket error: {}", exception.getMessage());
                }

                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    log.error("Transport error: {}", exception.getMessage());
                    // Retry connection after 5 seconds
                    try {
                        Thread.sleep(5000);
                        connectToCoordinator();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }).get();

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to connect to coordinator: {}", e.getMessage());
            // Retry connection after 5 seconds
            try {
                Thread.sleep(5000);
                connectToCoordinator();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void registerNode() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/node/register", Map.of(
                "id", nodeId,
                "host", nodeHost,
                "port", nodePort
            ));
            log.info("Node {} registered with coordinator", nodeId);
        }
    }

    private void subscribeToEvents() {
        if (stompSession == null || !stompSession.isConnected()) return;

        // Subscribe to migration commands
        stompSession.subscribe("/topic/node/" + nodeId + "/receive", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("Received code package: {}", payload);
                // Handle received code
            }
        });

        stompSession.subscribe("/topic/node/" + nodeId + "/execute", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> data = (Map<String, Object>) payload;
                String codeId = (String) data.get("codeId");
                log.info("Execute command received for code: {}", codeId);
                // Execute code
            }
        });

        stompSession.subscribe("/topic/node/" + nodeId + "/stop", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> data = (Map<String, Object>) payload;
                String codeId = (String) data.get("codeId");
                log.info("Stop command received for code: {}", codeId);
                codeExecutorService.stop(codeId);
            }
        });

        log.info("Subscribed to node events");
    }

    // Gửi metrics mỗi 3 giây
    @Scheduled(fixedRate = 3000)
    public void sendMetrics() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/node/metrics", Map.of(
                "nodeId", nodeId,
                "cpu", 20 + random.nextDouble() * 30,  // 20-50%
                "memory", 30 + random.nextDouble() * 20,  // 30-50%
                "processes", random.nextInt(3),
                "uptime", System.currentTimeMillis()
            ));
        }
    }

    // Heartbeat mỗi 10 giây
    @Scheduled(fixedRate = 10000)
    public void sendHeartbeat() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/node/heartbeat", Map.of(
                "nodeId", nodeId
            ));
        }
    }
}
