package com.htpt.migration.service;

import com.htpt.migration.model.CodePackage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class CodeExecutorService {

    private final Map<String, ExecutionContext> contexts = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Execute code trong sandbox
    public CompletableFuture<ExecutionResult> execute(String codeId, String code, String nodeId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing code {} on node {}", codeId, nodeId);

                Binding binding = new Binding();

                // Inject các biến môi trường
                binding.setVariable("nodeId", nodeId);
                binding.setVariable("output", new ArrayList<String>());
                binding.setVariable("data", new HashMap<String, Object>());

                GroovyShell shell = new GroovyShell(binding);

                // Execute với timeout
                long startTime = System.currentTimeMillis();
                Object result = shell.evaluate(code);
                long executionTime = System.currentTimeMillis() - startTime;

                // Lưu context
                ExecutionContext context = new ExecutionContext();
                context.binding = binding;
                context.result = result;
                context.status = "completed";
                context.startTime = startTime;
                contexts.put(codeId, context);

                log.info("Code {} executed successfully in {}ms", codeId, executionTime);

                return ExecutionResult.builder()
                    .codeId(codeId)
                    .nodeId(nodeId)
                    .result(result != null ? result.toString() : "null")
                    .executionTime(executionTime)
                    .status("completed")
                    .build();

            } catch (Exception e) {
                log.error("Execution error for code {}: {}", codeId, e.getMessage());

                return ExecutionResult.builder()
                    .codeId(codeId)
                    .nodeId(nodeId)
                    .error(e.getMessage())
                    .status("error")
                    .build();
            }
        }, executor);
    }

    // Thực thi với state đã có (strong mobility)
    public CompletableFuture<ExecutionResult> executeWithState(String codeId, String code,
            String nodeId, CodePackage.CodeState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Executing code {} with restored state on node {}", codeId, nodeId);

                Binding binding = new Binding();

                // Restore state
                if (state != null && state.getVariables() != null) {
                    state.getVariables().forEach(binding::setVariable);
                }

                // Inject môi trường
                binding.setVariable("nodeId", nodeId);

                GroovyShell shell = new GroovyShell(binding);

                long startTime = System.currentTimeMillis();
                Object result = shell.evaluate(code);
                long executionTime = System.currentTimeMillis() - startTime;

                // Lưu context
                ExecutionContext context = new ExecutionContext();
                context.binding = binding;
                context.result = result;
                context.status = "completed";
                context.startTime = startTime;
                contexts.put(codeId, context);

                log.info("Code {} with state executed in {}ms", codeId, executionTime);

                return ExecutionResult.builder()
                    .codeId(codeId)
                    .nodeId(nodeId)
                    .result(result != null ? result.toString() : "null")
                    .executionTime(executionTime)
                    .status("completed")
                    .build();

            } catch (Exception e) {
                log.error("Execution with state error: {}", e.getMessage());

                return ExecutionResult.builder()
                    .codeId(codeId)
                    .nodeId(nodeId)
                    .error(e.getMessage())
                    .status("error")
                    .build();
            }
        }, executor);
    }

    // Lấy state hiện tại
    public CodePackage.CodeState getState(String codeId) {
        ExecutionContext context = contexts.get(codeId);
        if (context == null) return null;

        Map<String, Object> variables = new HashMap<>();
        context.binding.getVariables().forEach((k, v) -> {
            if (isSerializable(v)) {
                variables.put(k.toString(), v);
            }
        });

        return CodePackage.CodeState.builder()
            .variables(variables)
            .executionPoint(0)
            .output(context.result != null ? context.result.toString() : "")
            .build();
    }

    // Dừng execution
    public void stop(String codeId) {
        ExecutionContext context = contexts.get(codeId);
        if (context != null) {
            context.status = "stopped";
            contexts.remove(codeId);
            log.info("Code {} stopped", codeId);
        }
    }

    private boolean isSerializable(Object obj) {
        return obj instanceof String
            || obj instanceof Number
            || obj instanceof Boolean
            || obj instanceof List
            || obj instanceof Map;
    }

    // Inner classes
    private static class ExecutionContext {
        Binding binding;
        Object result;
        String status;
        long startTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class ExecutionResult {
        private String codeId;
        private String nodeId;
        private String result;
        private String error;
        private long executionTime;
        private String status;
    }
}
