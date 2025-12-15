package com.htpt.migration.service;

import com.htpt.migration.model.CodePackage;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CodeExecutorService {

    private final Map<String, ExecutionContext> contexts =
        new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Execute code trong sandbox với capture console output
    public CompletableFuture<ExecutionResult> execute(
        String codeId,
        String code,
        String nodeId
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                // Capture stdout/stderr từ Groovy
                ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();
                PrintStream captureStream = new PrintStream(outputStream);
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;

                try {
                    log.info("Executing code {} on node {}", codeId, nodeId);

                    // Redirect System.out và System.err để capture println
                    System.setOut(captureStream);
                    System.setErr(captureStream);

                    Binding binding = new Binding();

                    // Inject các biến môi trường
                    binding.setVariable("nodeId", nodeId);
                    binding.setVariable("output", new ArrayList<String>());
                    binding.setVariable("data", new HashMap<String, Object>());
                    // Inject out stream để code có thể dùng: out.println("...")
                    binding.setVariable("out", captureStream);

                    GroovyShell shell = new GroovyShell(binding);

                    // Execute
                    long startTime = System.currentTimeMillis();
                    Object result = shell.evaluate(code);
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Restore original streams
                    System.setOut(originalOut);
                    System.setErr(originalErr);

                    // Get captured console output
                    String consoleOutput = outputStream.toString().trim();

                    // Lưu context
                    ExecutionContext context = new ExecutionContext();
                    context.binding = binding;
                    context.result = result;
                    context.consoleOutput = consoleOutput;
                    context.status = "completed";
                    context.startTime = startTime;
                    contexts.put(codeId, context);

                    log.info(
                        "Code {} executed successfully in {}ms",
                        codeId,
                        executionTime
                    );
                    if (!consoleOutput.isEmpty()) {
                        log.info("Console output:\n{}", consoleOutput);
                    }

                    return ExecutionResult.builder()
                        .codeId(codeId)
                        .nodeId(nodeId)
                        .result(result != null ? result.toString() : "null")
                        .consoleOutput(consoleOutput)
                        .executionTime(executionTime)
                        .status("completed")
                        .build();
                } catch (Exception e) {
                    // Restore original streams on error
                    System.setOut(originalOut);
                    System.setErr(originalErr);

                    String consoleOutput = outputStream.toString().trim();
                    log.error(
                        "Execution error for code {}: {}",
                        codeId,
                        e.getMessage()
                    );
                    if (!consoleOutput.isEmpty()) {
                        log.info(
                            "Console output before error:\n{}",
                            consoleOutput
                        );
                    }

                    return ExecutionResult.builder()
                        .codeId(codeId)
                        .nodeId(nodeId)
                        .consoleOutput(consoleOutput)
                        .error(e.getMessage())
                        .status("error")
                        .build();
                }
            },
            executor
        );
    }

    // Thực thi với state đã có (strong mobility)
    public CompletableFuture<ExecutionResult> executeWithState(
        String codeId,
        String code,
        String nodeId,
        CodePackage.CodeState state
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                // Capture stdout/stderr
                ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();
                PrintStream captureStream = new PrintStream(outputStream);
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;

                try {
                    log.info(
                        "Executing code {} with restored state on node {}",
                        codeId,
                        nodeId
                    );

                    System.setOut(captureStream);
                    System.setErr(captureStream);

                    Binding binding = new Binding();

                    // Restore state
                    if (state != null && state.getVariables() != null) {
                        state.getVariables().forEach(binding::setVariable);
                    }

                    // Inject môi trường
                    binding.setVariable("nodeId", nodeId);
                    binding.setVariable("out", captureStream);

                    GroovyShell shell = new GroovyShell(binding);

                    long startTime = System.currentTimeMillis();
                    Object result = shell.evaluate(code);
                    long executionTime = System.currentTimeMillis() - startTime;

                    // Restore streams
                    System.setOut(originalOut);
                    System.setErr(originalErr);

                    String consoleOutput = outputStream.toString().trim();

                    // Lưu context
                    ExecutionContext context = new ExecutionContext();
                    context.binding = binding;
                    context.result = result;
                    context.consoleOutput = consoleOutput;
                    context.status = "completed";
                    context.startTime = startTime;
                    contexts.put(codeId, context);

                    log.info(
                        "Code {} with state executed in {}ms",
                        codeId,
                        executionTime
                    );
                    if (!consoleOutput.isEmpty()) {
                        log.info("Console output:\n{}", consoleOutput);
                    }

                    return ExecutionResult.builder()
                        .codeId(codeId)
                        .nodeId(nodeId)
                        .result(result != null ? result.toString() : "null")
                        .consoleOutput(consoleOutput)
                        .executionTime(executionTime)
                        .status("completed")
                        .build();
                } catch (Exception e) {
                    System.setOut(originalOut);
                    System.setErr(originalErr);

                    String consoleOutput = outputStream.toString().trim();
                    log.error("Execution with state error: {}", e.getMessage());

                    return ExecutionResult.builder()
                        .codeId(codeId)
                        .nodeId(nodeId)
                        .consoleOutput(consoleOutput)
                        .error(e.getMessage())
                        .status("error")
                        .build();
                }
            },
            executor
        );
    }

    // Lấy state hiện tại
    public CodePackage.CodeState getState(String codeId) {
        ExecutionContext context = contexts.get(codeId);
        if (context == null) return null;

        Map<String, Object> variables = new HashMap<>();
        context.binding
            .getVariables()
            .forEach((k, v) -> {
                if (isSerializable(v)) {
                    variables.put(k.toString(), v);
                }
            });

        return CodePackage.CodeState.builder()
            .variables(variables)
            .executionPoint(0)
            .output(
                context.consoleOutput != null
                    ? context.consoleOutput
                    : (context.result != null ? context.result.toString() : "")
            )
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
        return (
            obj instanceof String ||
            obj instanceof Number ||
            obj instanceof Boolean ||
            obj instanceof List ||
            obj instanceof Map
        );
    }

    // Inner classes
    private static class ExecutionContext {

        Binding binding;
        Object result;
        String consoleOutput;
        String status;
        long startTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class ExecutionResult {

        private String codeId;
        private String nodeId;
        private String result;
        private String consoleOutput; // Captured println output
        private String error;
        private long executionTime;
        private String status;
    }
}
