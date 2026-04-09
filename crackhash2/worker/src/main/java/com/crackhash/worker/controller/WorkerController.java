package com.crackhash.worker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/api/worker")
@RequiredArgsConstructor
public class WorkerController {

    @RestController
    public class HealthController {
        @GetMapping("/health")
        public String health() {
            return "OK";
        }
    }
}
