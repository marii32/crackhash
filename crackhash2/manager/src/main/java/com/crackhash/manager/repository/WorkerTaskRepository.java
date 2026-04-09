package com.crackhash.manager.repository;

import com.crackhash.manager.model.TaskStatus;
import com.crackhash.manager.model.WorkerTask;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WorkerTaskRepository extends MongoRepository<WorkerTask, String> {

    List<WorkerTask> findByStatus(TaskStatus status);

    Optional<WorkerTask> findByRequestIdAndPartNumber(String requestId, int partNumber);

    void deleteByRequestIdAndPartNumber(String requestId, int partNumber);

    List<WorkerTask> findByRequestId(String requestId);
}