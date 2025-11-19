package com.htpt.migration.controller;

import com.htpt.migration.service.DemoScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoScenarioService demoScenarioService;

    @PostMapping("/mobile-agent")
    public ResponseEntity<?> runMobileAgentDemo() {
        demoScenarioService.runMobileAgentDemo();
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "scenario", "Mobile Agent",
            "message", "Demo started. Watch the dashboard for progress."
        ));
    }

    @PostMapping("/load-balancing")
    public ResponseEntity<?> runLoadBalancingDemo() {
        demoScenarioService.runLoadBalancingDemo();
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "scenario", "Load Balancing",
            "message", "Demo started. Watch the dashboard for progress."
        ));
    }

    @PostMapping("/fault-tolerance")
    public ResponseEntity<?> runFaultToleranceDemo() {
        demoScenarioService.runFaultToleranceDemo();
        return ResponseEntity.ok(Map.of(
            "status", "started",
            "scenario", "Fault Tolerance",
            "message", "Demo started. Watch the dashboard for progress."
        ));
    }
}
