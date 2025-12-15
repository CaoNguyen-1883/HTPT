package com.htpt.migration.service;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import com.sun.management.OperatingSystemMXBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Map;
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

    @Value("${server.port:8081}")
    private int nodePort;

    @Value("${node.coordinator-url:http://localhost:8080}")
    private String coordinatorUrl;

    private final CodeExecutorService codeExecutorService;
    private StompSession stompSession;
    private volatile boolean running = true;
    private String detectedIp;

    // System metrics beans
    private final OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtimeBean =
        ManagementFactory.getRuntimeMXBean();

    @PostConstruct
    public void init() {
        // Auto-detect IP if not configured or is localhost
        detectedIp = detectRealIp();
        if ("localhost".equals(nodeHost) || "127.0.0.1".equals(nodeHost)) {
            nodeHost = detectedIp;
        }

        log.info("===========================================");
        log.info("WORKER NODE STARTING");
        log.info("  Node ID: {}", nodeId);
        log.info("  Host: {}", nodeHost);
        log.info("  Detected IP: {}", detectedIp);
        log.info("  Port: {}", nodePort);
        log.info("  Coordinator: {}", coordinatorUrl);
        log.info("===========================================");
        connectToCoordinator();
    }

    /**
     * Detect real IP address (prefer LAN IP over localhost)
     */
    private String detectRealIp() {
        try {
            // Try to find a non-loopback, non-virtual network interface
            Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Skip loopback and down interfaces
                if (iface.isLoopback() || !iface.isUp()) continue;
                // Skip virtual interfaces (docker, vmware, etc.)
                String name = iface.getName().toLowerCase();
                if (
                    name.contains("docker") ||
                    name.contains("veth") ||
                    name.contains("vmnet") ||
                    name.contains("vbox")
                ) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Prefer IPv4 addresses
                    if (addr.getHostAddress().contains(":")) continue; // Skip IPv6
                    if (!addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            // Fallback to localhost
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to detect IP: {}", e.getMessage());
            return "localhost";
        }
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
                            retryConnection();
                        }
                    }
                )
                .get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to connect to coordinator: {}", e.getMessage());
            retryConnection();
        }
    }

    private void retryConnection() {
        if (!running) return;

        log.info("Retrying connection in 5 seconds...");
        try {
            Thread.sleep(5000);
            if (running) {
                connectToCoordinator();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            // Lấy CPU usage thực (% CPU của process)
            // getCpuLoad() trả về giá trị 0.0 - 1.0 (hoặc -1 nếu không available)
            double cpuLoad = osBean.getCpuLoad();
            double cpuUsage;
            if (cpuLoad >= 0) {
                cpuUsage = cpuLoad * 100; // Convert to percentage
            } else {
                // Fallback: dùng process CPU load
                double processCpu = osBean.getProcessCpuLoad();
                cpuUsage = processCpu >= 0 ? processCpu * 100 : 0;
            }

            // Lấy Memory usage thực của JVM
            long maxMemory = Runtime.getRuntime().maxMemory();
            long usedMemory =
                Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory();
            double memoryUsage = ((double) usedMemory / maxMemory) * 100;

            // Lấy uptime thực (thời gian JVM đã chạy, tính bằng giây)
            long uptimeSeconds = runtimeBean.getUptime() / 1000;

            stompSession.send(
                "/app/node/metrics",
                Map.of(
                    "nodeId",
                    nodeId,
                    "cpu",
                    Math.round(cpuUsage * 100.0) / 100.0, // Round to 2 decimals
                    "memory",
                    Math.round(memoryUsage * 100.0) / 100.0,
                    "processes",
                    Thread.activeCount(),
                    "uptime",
                    uptimeSeconds
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
