package com.htpt.migration.config;

import com.htpt.migration.model.Node;
import com.htpt.migration.model.NodeMetrics;
import com.htpt.migration.service.CoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Component
@Profile("!worker")  // Chỉ chạy khi không phải worker
@Slf4j
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final CoordinatorService coordinatorService;
    private final Random random = new Random();

    @Value("${NODE_COUNT:5}")
    private int nodeCount;

    @Override
    public void run(String... args) {
        // Tạo demo nodes khi khởi động
        log.info("Initializing {} demo nodes...", nodeCount);

        for (int i = 1; i <= nodeCount; i++) {
            Node node = Node.builder()
                .id("node-" + i)
                .host("192.168.1.10" + i)
                .port(8080 + i)
                .role(Node.NodeRole.WORKER)
                .status(Node.NodeStatus.ONLINE)
                .connectedAt(Instant.now())
                .metrics(NodeMetrics.builder()
                    .cpuUsage(random.nextDouble() * 40 + 10)  // 10-50%
                    .memoryUsage(random.nextDouble() * 30 + 20)  // 20-50%
                    .activeProcesses(random.nextInt(3))
                    .uptime(System.currentTimeMillis())
                    .build())
                .build();

            coordinatorService.registerNode(node);
            log.info("Demo node registered: {}", node.getId());
        }

        log.info("Demo nodes initialized successfully!");
    }

    // Cập nhật metrics mỗi 3 giây để simulate hoạt động
    @Scheduled(fixedRate = 3000)
    public void updateDemoMetrics() {
        for (int i = 1; i <= 5; i++) {
            String nodeId = "node-" + i;
            Node node = coordinatorService.getNode(nodeId);

            if (node != null && node.getStatus() != Node.NodeStatus.OFFLINE) {
                NodeMetrics metrics = NodeMetrics.builder()
                    .cpuUsage(Math.max(5, Math.min(95,
                        node.getMetrics().getCpuUsage() + (random.nextDouble() - 0.5) * 10)))
                    .memoryUsage(Math.max(10, Math.min(90,
                        node.getMetrics().getMemoryUsage() + (random.nextDouble() - 0.5) * 5)))
                    .activeProcesses(node.getMetrics().getActiveProcesses())
                    .uptime(System.currentTimeMillis() - node.getConnectedAt().toEpochMilli())
                    .build();

                coordinatorService.updateMetrics(nodeId, metrics);
            }
        }
    }
}
