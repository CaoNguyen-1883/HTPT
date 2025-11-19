package com.htpt.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum LogLevel {
        INFO, SUCCESS, WARNING, ERROR, DEBUG
    }

    /**
     * Broadcast log message to all clients
     */
    public void broadcast(LogLevel level, String nodeId, String event, String message) {
        String timestamp = LocalDateTime.now().format(timeFormatter);

        String formattedMessage = String.format("[%s] [%s] - %s",
            nodeId != null ? nodeId : "SYSTEM",
            event,
            message);

        messagingTemplate.convertAndSend("/topic/logs", Map.of(
            "timestamp", timestamp,
            "level", level.name().toLowerCase(),
            "nodeId", nodeId != null ? nodeId : "SYSTEM",
            "event", event,
            "message", message,
            "formatted", formattedMessage
        ));

        // Also log to server console
        switch (level) {
            case ERROR -> log.error(formattedMessage);
            case WARNING -> log.warn(formattedMessage);
            case SUCCESS, INFO -> log.info(formattedMessage);
            case DEBUG -> log.debug(formattedMessage);
        }
    }

    // Convenience methods
    public void info(String nodeId, String event, String message) {
        broadcast(LogLevel.INFO, nodeId, event, message);
    }

    public void success(String nodeId, String event, String message) {
        broadcast(LogLevel.SUCCESS, nodeId, event, message);
    }

    public void warning(String nodeId, String event, String message) {
        broadcast(LogLevel.WARNING, nodeId, event, message);
    }

    public void error(String nodeId, String event, String message) {
        broadcast(LogLevel.ERROR, nodeId, event, message);
    }

    // Specific log methods for common events
    public void logCodeUpload(String nodeId, String codeId, String codeName) {
        info(nodeId, "UPLOAD", String.format("Code \"%s\" uploaded (id: %s)", codeName, codeId));
    }

    public void logExecutionStart(String nodeId, String codeId) {
        info(nodeId, "EXECUTE", String.format("Starting execution of %s", codeId));
    }

    public void logExecutionOutput(String nodeId, String codeId, Object output) {
        info(nodeId, "OUTPUT", String.format("Result: %s", formatOutput(output)));
    }

    public void logExecutionComplete(String nodeId, String codeId, long durationMs) {
        success(nodeId, "COMPLETE", String.format("Execution completed in %dms", durationMs));
    }

    public void logExecutionError(String nodeId, String codeId, String error) {
        error(nodeId, "ERROR", String.format("Execution failed: %s", error));
    }

    public void logMigrationStart(String migrationId, String source, String target, String type) {
        info("MIGRATION", "START", String.format("%s: %s → %s (%s)", migrationId, source, target, type));
    }

    public void logStateCheckpoint(String nodeId, Object state) {
        info(nodeId, "STATE", String.format("Checkpoint: %s", formatOutput(state)));
    }

    public void logMigrationTransfer(String migrationId) {
        info("MIGRATION", "TRANSFER", String.format("%s: Sending code + state...", migrationId));
    }

    public void logCodeReceive(String nodeId, boolean withState) {
        info(nodeId, "RECEIVE", withState ? "Code received with state" : "Code received");
    }

    public void logMigrationComplete(String migrationId, long durationMs) {
        success("MIGRATION", "DONE", String.format("%s completed in %dms", migrationId, durationMs));
    }

    public void logMigrationFailed(String migrationId, String error) {
        error("MIGRATION", "FAILED", String.format("%s failed: %s", migrationId, error));
    }

    public void logNodeHealth(String nodeId, String metric, double value) {
        if (value > 80) {
            warning(nodeId, "HEALTH", String.format("%s high: %.0f%%", metric, value));
        }
    }

    public void logNodeStatus(String nodeId, String status) {
        if ("OFFLINE".equals(status)) {
            error(nodeId, "STATUS", "Node went OFFLINE");
        } else if ("ONLINE".equals(status)) {
            success(nodeId, "STATUS", "Node is ONLINE");
        } else {
            info(nodeId, "STATUS", String.format("Status changed to %s", status));
        }
    }

    private String formatOutput(Object output) {
        if (output == null) return "null";

        String str = output.toString();
        // Truncate if too long
        if (str.length() > 200) {
            return str.substring(0, 197) + "...";
        }
        return str;
    }
}
