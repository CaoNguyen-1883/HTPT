package com.htpt.migration;

import java.net.InetAddress;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MigrationApplication {

    @Autowired
    private Environment env;

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String[] profiles = env.getActiveProfiles();
        String port = env.getProperty("server.port", "8080");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            // ignore
        }

        System.out.println();
        System.out.println(
            "╔═══════════════════════════════════════════════════════════╗"
        );
        System.out.println(
            "║          CODE MIGRATION SYSTEM - STARTED                  ║"
        );
        System.out.println(
            "╠═══════════════════════════════════════════════════════════╣"
        );
        System.out.println(
            "║  Profile(s): " + padRight(Arrays.toString(profiles), 44) + "║"
        );
        System.out.println(
            "║  Local URL:  " + padRight("http://localhost:" + port, 44) + "║"
        );
        System.out.println(
            "║  Network:    " +
                padRight("http://" + hostAddress + ":" + port, 44) +
                "║"
        );
        System.out.println(
            "╠═══════════════════════════════════════════════════════════╣"
        );

        if (
            Arrays.asList(profiles).contains("coordinator") ||
            Arrays.asList(profiles).contains("demo")
        ) {
            System.out.println(
                "║  Mode: COORDINATOR                                        ║"
            );
            System.out.println(
                "║  API Docs:   " +
                    padRight(
                        "http://" + hostAddress + ":" + port + "/api/nodes",
                        44
                    ) +
                    "║"
            );
        } else if (Arrays.asList(profiles).contains("worker")) {
            String nodeId = env.getProperty("node.id", "worker");
            String coordUrl = env.getProperty(
                "node.coordinator-url",
                "http://localhost:8080"
            );
            System.out.println(
                "║  Mode: WORKER                                             ║"
            );
            System.out.println("║  Node ID:    " + padRight(nodeId, 44) + "║");
            System.out.println(
                "║  Coordinator:" + padRight(coordUrl, 44) + "║"
            );
        }

        System.out.println(
            "╚═══════════════════════════════════════════════════════════╝"
        );
        System.out.println();
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }
}
