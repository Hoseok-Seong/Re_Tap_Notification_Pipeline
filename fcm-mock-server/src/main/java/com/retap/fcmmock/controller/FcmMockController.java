package com.retap.fcmmock.controller;

import com.retap.fcmmock.service.FcmMockMetrics;
import com.retap.fcmmock.service.FcmMockService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FcmMockController {

    private final FcmMockService fcmMockService;

    public FcmMockController(FcmMockService fcmMockService) {
        this.fcmMockService = fcmMockService;
    }

    @PostMapping("/v1/projects/{projectId}/messages:send")
    public ResponseEntity<Map<String, Object>> send(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        FcmMockService.SendResult result = fcmMockService.send(projectId);
        if (result.success()) {
            return ResponseEntity.ok(Map.of("name", result.value()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", Map.of(
                                "code", 500,
                                "status", "INTERNAL",
                                "message", result.value()
                        )
                ));
    }

    @GetMapping("/metrics")
    public FcmMockMetrics.Snapshot metrics() {
        return fcmMockService.metricsSnapshot();
    }
}
