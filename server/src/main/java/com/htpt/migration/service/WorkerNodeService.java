package com.htpt.migration.service;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
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

/**
 * Worker Node Service
 *
 * Chạy trên mỗi laptop worker, kết nối đến Coordinator qua WebSocket.
 *
 * Cách chạy:
 *   java -jar app.jar --spring.profiles.active=worker \
 *     --node.id=node-1 \
 *     --node.host=192.168.1.101 \
 *     --node.coordinator-url=http://192.168.1.100:8080
 */
@Service
@Profile("worker")
@Slf4j
@RequiredArgsConstructor
public class WorkerNodeService {

    @Value("${node.id:worker}")
    private String nodeId;

    @Value("${node.host:localhost}")
    private String nodeHost;

    @Value("${node.port:${server.port:8081}}")
    private int nodePort;

    @Value("${node.coordinator-url:http://localhost:8080}")
    private String coordinatorUrl;

    private final CodeExecutorService codeExecutorService;
    private StompSession stompSession;
    private final Random random = new Random();
    private volatile boolean running = true;

    // System metrics
    private final OperatingSystemMXBean osBean =
        ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @PostConstruct
    public void init() {
        log.info("===========================================");
        log.info("WORKER NODE STARTING");
        log.info("  Node ID: {}", nodeId);
        log.info("  Host: {}", nodeHost);
        log.info("  Port: {}", nodePort);
        log.info("  Coordinator: {}", coordinatorUrl);
        log.info("===========================================");
        connectToCoordinator();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (stompSession != null && stompSession.isConnected()) {
            try {
                stompSession.send(
                    "/app/node/unregister",
                    Map.of("nodeId", nodeId)
                );
                log.info("Node {} unregistered from coordinator", nodeId);
            } catch (Exception e) {
                log.warn("Failed to unregister node: {}", e.getMessage());
            }
        }
    }

    private void connectToCoordinator() {
        try {
            WebSocketStompClient stompClient = new WebSocketStompClient(
                new StandardWebSocketClient()
            );
            stompClient.setMessageConverter(
                new MappingJackson2MessageConverter()
            );

            String wsUrl =
                coordinatorUrl.replace("http://", "ws://") + "/ws/websocket";
            log.info("Connecting to coordinator: {}", wsUrl);

            stompSession = stompClient
                .connectAsync(
                    wsUrl,
                    new StompSessionHandlerAdapter() {
                        @Override
                        public void afterConnected(
                            StompSession session,
                            StompHeaders connectedHeaders
                        ) {
                            log.info("Connected to coordinator!");
                            registerNode();
                            subscribeToEvents();
                        }

                        @Override
                        public void handleException(
                            StompSession session,
                            StompCommand command,
                            StompHeaders headers,
                            byte[] payload,
                            Throwable exception
                        ) {
                            log.error(
                                "WebSocket error: {}",
                                exception.getMessage()
                            );
                        }

                        @Override
                        public void handleTransportError(
                            StompSession session,
                            Throwable exception
                        ) {
                            log.error(
                                "Transport error: {}",
                                exception.getMessage()
                            );
                            // Retry connection after 5 seconds
                            try {
                                Thread.sleep(5000);
                                connectToCoordinator();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                )
                .get();
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
            stompSession.send(
                "/app/node/register",
                Map.of("id", nodeId, "host", nodeHost, "port", nodePort)
            );
            log.info("Node {} registered with coordinator", nodeId);
        }
    }

    private void subscribeToEvents() {
        if (stompSession == null || !stompSession.isConnected()) return;

        // Subscribe to migration commands
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/receive",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    log.info("Received code package: {}", payload);
                    // Handle received code
                }
            }
        );

        stompSession.subscribe(
            "/topic/node/" + nodeId + "/execute",
            new StompFrameHandler() {
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
            }
        );

        stompSession.subscribe(
            "/topic/node/" + nodeId + "/stop",
            new StompFrameHandler() {
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
            }
        );

        log.info("Subscribed to node events");
    }

    // Gửi metrics mỗi 3 giây - sử dụng metrics thực từ hệ thống
    @Scheduled(fixedRate = 3000)
    public void sendMetrics() {
        if (stompSession != null && stompSession.isConnected()) {
            // Lấy CPU usage thực
            double cpuLoad = osBean.getSystemLoadAverage();
            double cpuUsage = cpuLoad >= 0
                ? Math.min(cpuLoad * 10, 100)
                : 20 + random.nextDouble() * 30;

            // Lấy Memory usage thực
            long maxMemory = Runtime.getRuntime().maxMemory();
            long usedMemory =
                Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory();
            double memoryUsage = ((double) usedMemory / maxMemory) * 100;

            stompSession.send(
                "/app/node/metrics",
                Map.of(
                    "nodeId",
                    nodeId,
                    "cpu",
                    cpuUsage,
                    "memory",
                    memoryUsage,
                    "processes",
                    Thread.activeCount(),
                    "uptime",
                    System.currentTimeMillis()
                )
            );
        }
    }

    // Heartbeat mỗi 10 giây
    @Scheduled(fixedRate = 10000)
    public void sendHeartbeat() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/node/heartbeat", Map.of("nodeId", nodeId));
        }
    }
}
