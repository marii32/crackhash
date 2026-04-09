package com.crackhash.manager.controller;

import com.crackhash.requests.HashRequest;
import com.crackhash.requests.HashStatus;
import com.crackhash.manager.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/hash")
@RequiredArgsConstructor
public class ManagerController {
    private final ManagerService managerService;

    @PostMapping("/crack")
    public ResponseEntity<Map<String, String>> crackHash(@RequestBody HashRequest request) {
        String requestId = managerService.processRequestT(request);
        return ResponseEntity.ok(Collections.singletonMap("requestId", requestId));
    }

    @GetMapping("/status")
    public ResponseEntity<HashStatus> getStatus(@RequestParam String requestId) {
        return ResponseEntity.ok(managerService.getRequestStatus(requestId));
    }
}
