package com.retap.fcmmock.controller;

import com.retap.fcmmock.service.FcmMockMetrics;
import com.retap.fcmmock.service.FcmMockService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
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

    @PostMapping("/v1/projects/{projectId}/messages:sendBatch")
    public ResponseEntity<Map<String, Object>> sendBatch(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> request
    ) {
        List<?> messages = extractMessages(request);
        if (messages.isEmpty() || messages.size() > FcmMockService.MAX_BATCH_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "messages size must be between 1 and " + FcmMockService.MAX_BATCH_SIZE
            );
        }

        FcmMockService.BatchSendResult result = fcmMockService.sendBatch(projectId, messages.size());

        List<Map<String, Object>> responses = result.responses().stream()
                .map(response -> response.success()
                        ? Map.<String, Object>of(
                                "success", true,
                                "messageId", response.value()
                        )
                        : Map.<String, Object>of(
                                "success", false,
                                "error", Map.of(
                                        "code", 500,
                                        "status", "INTERNAL",
                                        "message", response.value()
                                )
                        ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "successCount", result.successCount(),
                "failureCount", result.failureCount(),
                "responses", responses
        ));
    }

    @GetMapping("/metrics")
    public FcmMockMetrics.Snapshot metrics() {
        return fcmMockService.metricsSnapshot();
    }

    private List<?> extractMessages(Map<String, Object> request) {
        Object messages = request.get("messages");
        if (messages instanceof List<?> list) {
            return list;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messages must be an array");
    }
}
