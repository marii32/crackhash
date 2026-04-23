package com.crackhash.worker.controller;

import com.crackhash.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/api/worker")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/progress/{id}")
    public double progress(@PathVariable String id) {
        System.out.println("HIT progress: " + id);
        return workerService.getProgress(id);
    }
}
