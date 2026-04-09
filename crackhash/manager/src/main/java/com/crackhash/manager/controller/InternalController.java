package com.crackhash.manager.controller;

import com.crackhash.manager.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.crackhash.requests.WorkerResponse;

@RestController
@RequestMapping("/internal/api/manager")
@RequiredArgsConstructor
public class InternalController {
    private final ManagerService managerService;

    @PatchMapping(
            value = "/hash/crack/request",
            consumes = "application/xml"
    )
    public ResponseEntity<Void> receiveWorkerResult(@RequestBody WorkerResponse response) {
        managerService.processWorkerResult(response);
        return ResponseEntity.ok().build();
    }
}
