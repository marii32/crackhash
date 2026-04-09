package com.crackhash.worker.controller;

import com.crackhash.requests.WorkerRequest;
import com.crackhash.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/api/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;
    @PostMapping(
            value = "/hash/crack/task",
            consumes = MediaType.APPLICATION_XML_VALUE
    )

    public ResponseEntity<Void> crackHash(@RequestBody WorkerRequest request) {
        workerService.processTask(request);
        return ResponseEntity.ok().build();
    }

    @RestController
    public class HealthController {
        @GetMapping("/health")
        public String health() {
            return "OK";
        }
    }
}
