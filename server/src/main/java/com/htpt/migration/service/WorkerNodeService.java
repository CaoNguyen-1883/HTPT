package com.htpt.migration.service;

import com.htpt.migration.model.CodePackage;
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
import java.util.concurrent.ConcurrentHashMap;
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

    // Lưu trữ code packages đã nhận từ Coordinator
    private final Map<String, CodePackage> receivedCodePackages =
        new ConcurrentHashMap<>();

    // System metrics beans
    private final OperatingSystemMXBean osBean =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtimeBean =
        ManagementFactory.getRuntimeMXBean();

    /**
     * Thread-safe WebSocket message sender
     * Prevents race conditions when multiple threads send messages concurrently
     *
     * @param destination The STOMP destination (e.g., "/app/node/metrics")
     * @param payload The message payload
     */
    private synchronized void safeSend(String destination, Object payload) {
        if (stompSession == null || !stompSession.isConnected()) {
            log.warn(
                "Cannot send message to {} - session not connected",
                destination
            );
            return;
        }

        try {
            stompSession.send(destination, payload);
        } catch (Exception e) {
            log.error(
                "Failed to send message to {}: {}",
                destination,
                e.getMessage()
            );
            // Don't rethrow - let the caller continue gracefully
        }
    }

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
            safeSend("/app/node/unregister", Map.of("nodeId", nodeId));
            log.info("Node {} unregistered from coordinator", nodeId);
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

            // Hỗ trợ cả HTTP và HTTPS (ngrok, cloudflare tunnel)
            String wsUrl =
                coordinatorUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://") +
                "/ws/websocket";
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
            safeSend(
                "/app/node/register",
                Map.of("id", nodeId, "host", nodeHost, "port", nodePort)
            );
            log.info("Node {} registered with coordinator", nodeId);
        }
    }

    private void subscribeToEvents() {
        if (stompSession == null || !stompSession.isConnected()) return;

        // Subscribe to receive code package
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/receive",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> data = (Map<String, Object>) payload;

                    // Parse code package từ payload
                    String codeId = (String) data.get("id");
                    String codeName = (String) data.get("name");
                    String code = (String) data.get("code");
                    String entryPoint = (String) data.get("entryPoint");

                    // Lưu state nếu có (strong mobility)
                    Map<String, Object> stateData = (Map<
                        String,
                        Object
                    >) data.get("state");
                    CodePackage.CodeState state = null;
                    if (stateData != null) {
                        Map<String, Object> variables = (Map<
                            String,
                            Object
                        >) stateData.get("variables");
                        state = CodePackage.CodeState.builder()
                            .variables(variables)
                            .executionPoint(
                                stateData.get("executionPoint") != null
                                    ? ((Number) stateData.get(
                                              "executionPoint"
                                          )).intValue()
                                    : 0
                            )
                            .output((String) stateData.get("output"))
                            .build();
                    }

                    CodePackage codePackage = CodePackage.builder()
                        .id(codeId)
                        .name(codeName)
                        .code(code)
                        .entryPoint(entryPoint)
                        .currentNodeId(nodeId)
                        .state(state)
                        .build();

                    receivedCodePackages.put(codeId, codePackage);

                    // ========== LOG CHI TIẾT CODE PACKAGE NHẬN ĐƯỢC ==========
                    log.info(
                        "╔══════════════════════════════════════════════════════════════╗"
                    );
                    log.info(
                        "║          RECEIVED CODE PACKAGE FROM COORDINATOR              ║"
                    );
                    log.info(
                        "╠══════════════════════════════════════════════════════════════╣"
                    );
                    log.info("║ Code ID    : {}", codeId);
                    log.info("║ Code Name  : {}", codeName);
                    log.info("║ Entry Point: {}", entryPoint);
                    log.info("║ Target Node: {}", nodeId);
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    log.info("║ CODE CONTENT:");
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    String[] codeLines = code.split("\n");
                    for (int i = 0; i < codeLines.length; i++) {
                        log.info(
                            "║ {} │ {}",
                            String.format("%3d", i + 1),
                            codeLines[i]
                        );
                    }
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    log.info("║ MIGRATED STATE:");
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    if (state != null) {
                        log.info(
                            "║   [STRONG MOBILITY - State transferred from source]"
                        );
                        log.info(
                            "║   Execution Point: {}",
                            state.getExecutionPoint()
                        );
                        log.info("║   Variables:");
                        if (
                            state.getVariables() != null &&
                            !state.getVariables().isEmpty()
                        ) {
                            for (Map.Entry<String, Object> entry : state
                                .getVariables()
                                .entrySet()) {
                                log.info(
                                    "║     - {} = {} ({})",
                                    entry.getKey(),
                                    entry.getValue(),
                                    entry.getValue() != null
                                        ? entry
                                              .getValue()
                                              .getClass()
                                              .getSimpleName()
                                        : "null"
                                );
                            }
                        } else {
                            log.info("║     (no variables)");
                        }
                        log.info(
                            "║   Previous Output: {}",
                            state.getOutput() != null
                                ? state.getOutput()
                                : "(none)"
                        );
                    } else {
                        log.info("║   [WEAK MOBILITY - No state, fresh start]");
                    }
                    log.info(
                        "╚══════════════════════════════════════════════════════════════╝"
                    );
                }
            }
        );

        // Subscribe to execute command - THỰC SỰ EXECUTE CODE
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/execute",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> data = (Map<String, Object>) payload;
                    String codeId = (String) data.get("codeId");

                    CodePackage codePackage = receivedCodePackages.get(codeId);
                    if (codePackage == null) {
                        log.error("Code package not found: {}", codeId);
                        sendExecutionResult(
                            codeId,
                            null,
                            "Code package not found",
                            null
                        );
                        return;
                    }

                    // ========== LOG TRƯỚC KHI THỰC THI ==========
                    log.info(
                        "╔══════════════════════════════════════════════════════════════╗"
                    );
                    log.info(
                        "║            COORDINATOR DISPATCH - BEFORE EXECUTION           ║"
                    );
                    log.info(
                        "╠══════════════════════════════════════════════════════════════╣"
                    );
                    log.info("║ Code ID    : {}", codeId);
                    log.info("║ Code Name  : {}", codePackage.getName());
                    log.info("║ Entry Point: {}", codePackage.getEntryPoint());
                    log.info("║ Node       : {}", nodeId);
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    log.info("║ CODE PACKAGE:");
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    String[] codeLines = codePackage.getCode().split("\n");
                    for (int i = 0; i < codeLines.length; i++) {
                        log.info(
                            "║ {} │ {}",
                            String.format("%3d", i + 1),
                            codeLines[i]
                        );
                    }
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    log.info("║ STATE BEFORE EXECUTION:");
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    if (codePackage.getState() != null) {
                        CodePackage.CodeState state = codePackage.getState();
                        log.info(
                            "║   Execution Point: {}",
                            state.getExecutionPoint()
                        );
                        log.info("║   Variables:");
                        if (
                            state.getVariables() != null &&
                            !state.getVariables().isEmpty()
                        ) {
                            for (Map.Entry<String, Object> entry : state
                                .getVariables()
                                .entrySet()) {
                                log.info(
                                    "║     - {} = {}",
                                    entry.getKey(),
                                    entry.getValue()
                                );
                            }
                        } else {
                            log.info("║     (no variables)");
                        }
                        log.info(
                            "║   Previous Output: {}",
                            state.getOutput() != null
                                ? state.getOutput()
                                : "(none)"
                        );
                    } else {
                        log.info("║   (no state - fresh execution)");
                    }
                    log.info(
                        "╚══════════════════════════════════════════════════════════════╝"
                    );

                    // Thực sự execute code bằng CodeExecutorService
                    try {
                        CodeExecutorService.ExecutionResult result;

                        if (codePackage.getState() != null) {
                            // Strong mobility - execute với state đã có
                            log.info(
                                ">>> Executing with RESTORED STATE (Strong Mobility)..."
                            );
                            result = codeExecutorService
                                .executeWithState(
                                    codeId,
                                    codePackage.getCode(),
                                    nodeId,
                                    codePackage.getState()
                                )
                                .get(); // Block để lấy kết quả
                        } else {
                            // Weak mobility - execute từ đầu
                            log.info(
                                ">>> Executing from SCRATCH (Weak Mobility)..."
                            );
                            result = codeExecutorService
                                .execute(codeId, codePackage.getCode(), nodeId)
                                .get(); // Block để lấy kết quả
                        }

                        // ========== LOG SAU KHI THỰC THI ==========
                        log.info(
                            "╔══════════════════════════════════════════════════════════════╗"
                        );
                        log.info(
                            "║            COORDINATOR DISPATCH - AFTER EXECUTION            ║"
                        );
                        log.info(
                            "╠══════════════════════════════════════════════════════════════╣"
                        );
                        log.info("║ Code ID    : {}", codeId);
                        log.info("║ Status     : {}", result.getStatus());
                        log.info(
                            "║ Exec Time  : {}ms",
                            result.getExecutionTime()
                        );
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        log.info("║ EXECUTION RESULT:");
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        log.info("║   Return Value: {}", result.getResult());
                        if (
                            result.getError() != null &&
                            !result.getError().isEmpty()
                        ) {
                            log.error("║   Error: {}", result.getError());
                        }
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        log.info("║ CONSOLE OUTPUT:");
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        if (
                            result.getConsoleOutput() != null &&
                            !result.getConsoleOutput().isEmpty()
                        ) {
                            String[] outputLines = result
                                .getConsoleOutput()
                                .split("\n");
                            for (String line : outputLines) {
                                log.info("║   > {}", line);
                            }
                        } else {
                            log.info("║   (no console output)");
                        }
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        log.info("║ STATE AFTER EXECUTION:");
                        log.info(
                            "╠──────────────────────────────────────────────────────────────╣"
                        );
                        CodePackage.CodeState stateAfter =
                            codeExecutorService.getState(codeId);
                        if (stateAfter != null) {
                            log.info("║   Variables:");
                            if (
                                stateAfter.getVariables() != null &&
                                !stateAfter.getVariables().isEmpty()
                            ) {
                                for (Map.Entry<
                                    String,
                                    Object
                                > entry : stateAfter
                                    .getVariables()
                                    .entrySet()) {
                                    log.info(
                                        "║     - {} = {}",
                                        entry.getKey(),
                                        entry.getValue()
                                    );
                                }
                            } else {
                                log.info("║     (no variables captured)");
                            }
                            log.info(
                                "║   Output: {}",
                                stateAfter.getOutput() != null
                                    ? stateAfter.getOutput()
                                    : "(none)"
                            );
                        } else {
                            log.info("║   (no state captured)");
                        }
                        log.info(
                            "╚══════════════════════════════════════════════════════════════╝"
                        );

                        // Gửi kết quả về Coordinator
                        sendExecutionResult(
                            codeId,
                            result.getResult(),
                            result.getError(),
                            result.getConsoleOutput()
                        );
                    } catch (Exception e) {
                        log.error(
                            "╔══════════════════════════════════════════════════════════════╗"
                        );
                        log.error(
                            "║                    EXECUTION FAILED                          ║"
                        );
                        log.error(
                            "╠══════════════════════════════════════════════════════════════╣"
                        );
                        log.error("║ Code ID: {}", codeId);
                        log.error("║ Error  : {}", e.getMessage());
                        log.error(
                            "╚══════════════════════════════════════════════════════════════╝",
                            e
                        );
                        sendExecutionResult(codeId, null, e.getMessage(), null);
                    }
                }
            }
        );

        // Subscribe to stop command
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/stop",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> data = (Map<String, Object>) payload;
                    String codeId = (String) data.get("codeId");
                    log.info("Stop command received for code: {}", codeId);
                    codeExecutorService.stop(codeId);
                }
            }
        );

        // Subscribe to capture-state command - Capture state thực từ execution context
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/capture-state",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> data = (Map<String, Object>) payload;
                    String codeId = (String) data.get("codeId");

                    // Lấy state thực từ CodeExecutorService
                    CodePackage.CodeState realState =
                        codeExecutorService.getState(codeId);

                    // ========== LOG CAPTURE STATE ==========
                    log.info(
                        "╔══════════════════════════════════════════════════════════════╗"
                    );
                    log.info(
                        "║         CAPTURE STATE COMMAND FROM COORDINATOR               ║"
                    );
                    log.info(
                        "╠══════════════════════════════════════════════════════════════╣"
                    );
                    log.info("║ Code ID    : {}", codeId);
                    log.info("║ Source Node: {}", nodeId);
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );
                    log.info("║ CAPTURED STATE (to be sent to target):");
                    log.info(
                        "╠──────────────────────────────────────────────────────────────╣"
                    );

                    if (
                        realState != null &&
                        realState.getVariables() != null &&
                        !realState.getVariables().isEmpty()
                    ) {
                        log.info(
                            "║   Execution Point: {}",
                            realState.getExecutionPoint()
                        );
                        log.info(
                            "║   Variables ({} total):",
                            realState.getVariables().size()
                        );
                        for (Map.Entry<String, Object> entry : realState
                            .getVariables()
                            .entrySet()) {
                            log.info(
                                "║     - {} = {} ({})",
                                entry.getKey(),
                                entry.getValue(),
                                entry.getValue() != null
                                    ? entry
                                          .getValue()
                                          .getClass()
                                          .getSimpleName()
                                    : "null"
                            );
                        }
                        log.info(
                            "║   Console Output: {}",
                            realState.getOutput() != null
                                ? realState.getOutput()
                                : "(none)"
                        );
                        log.info(
                            "╚══════════════════════════════════════════════════════════════╝"
                        );

                        // Gửi state thực về Coordinator
                        sendCapturedState(codeId, realState);
                    } else {
                        log.warn(
                            "║   [WARNING] No execution context found for codeId: {}",
                            codeId
                        );
                        log.warn(
                            "║   Available contexts: {}",
                            codeExecutorService.getAvailableContextIds()
                        );
                        log.warn(
                            "║   Hint: Code must be executed before migration to capture state"
                        );
                        log.warn("║   Sending empty state...");
                        log.info(
                            "╚══════════════════════════════════════════════════════════════╝"
                        );

                        // Gửi empty state
                        sendCapturedState(
                            codeId,
                            CodePackage.CodeState.builder()
                                .variables(Map.of())
                                .executionPoint(0)
                                .output("")
                                .build()
                        );
                    }
                }
            }
        );

        // Subscribe to code-uploaded - khi code được upload lên node này
        stompSession.subscribe(
            "/topic/node/" + nodeId + "/code-uploaded",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    Map<String, Object> data = (Map<String, Object>) payload;
                    String codeId = (String) data.get("id");
                    String codeName = (String) data.get("name");
                    String code = (String) data.get("code");
                    String entryPoint = (String) data.get("entryPoint");

                    log.info("=== CODE UPLOADED TO THIS NODE ===");
                    log.info("  Code ID: {}", codeId);
                    log.info("  Name: {}", codeName);

                    CodePackage codePackage = CodePackage.builder()
                        .id(codeId)
                        .name(codeName)
                        .code(code)
                        .entryPoint(entryPoint)
                        .currentNodeId(nodeId)
                        .build();

                    receivedCodePackages.put(codeId, codePackage);

                    // Tự động execute code khi được upload
                    log.info("  Auto-executing uploaded code...");
                    try {
                        CodeExecutorService.ExecutionResult result =
                            codeExecutorService
                                .execute(codeId, code, nodeId)
                                .get();

                        log.info(
                            "  Initial execution completed: {}",
                            result.getStatus()
                        );
                        if (
                            result.getConsoleOutput() != null &&
                            !result.getConsoleOutput().isEmpty()
                        ) {
                            log.info(
                                "  Console:\n{}",
                                result.getConsoleOutput()
                            );
                        }
                        log.info("  Result: {}", result.getResult());
                    } catch (Exception e) {
                        log.error(
                            "  Initial execution failed: {}",
                            e.getMessage()
                        );
                    }
                    log.info("==================================");
                }
            }
        );

        log.info("Subscribed to node events");
    }

    /**
     * Gửi state đã capture về Coordinator
     */
    private void sendCapturedState(String codeId, CodePackage.CodeState state) {
        safeSend(
            "/app/node/state-captured",
            Map.of(
                "nodeId",
                nodeId,
                "codeId",
                codeId,
                "variables",
                state.getVariables() != null ? state.getVariables() : Map.of(),
                "executionPoint",
                state.getExecutionPoint(),
                "output",
                state.getOutput() != null ? state.getOutput() : "",
                "timestamp",
                System.currentTimeMillis()
            )
        );
        log.info("Captured state sent to coordinator");
    }

    /**
     * Gửi kết quả execution về Coordinator
     */
    private void sendExecutionResult(
        String codeId,
        String result,
        String error,
        String consoleOutput
    ) {
        safeSend(
            "/app/node/execution-complete",
            Map.of(
                "nodeId",
                nodeId,
                "codeId",
                codeId,
                "result",
                result != null ? result : "",
                "error",
                error != null ? error : "",
                "consoleOutput",
                consoleOutput != null ? consoleOutput : "",
                "status",
                error == null ? "completed" : "error",
                "timestamp",
                System.currentTimeMillis()
            )
        );
        log.info("Execution result sent to coordinator");
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

            safeSend(
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
            safeSend("/app/node/heartbeat", Map.of("nodeId", nodeId));
        }
    }
}
