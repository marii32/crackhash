package com.crackhash.manager.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "worker_tasks")
public class WorkerTask{

    @Id
    private String id;
    private String requestId;
    private int partNumber;
    private String workerUrl;
    private TaskStatus status;
    private Date createdAt;
    private Date updatedAt;

    public WorkerTask(String requestId,
                            int partNumber,
                            String workerUrl,
                            TaskStatus status,
                            Date createdAt,
                            Date updatedAt) {
        this.requestId = requestId;
        this.partNumber = partNumber;
        this.workerUrl = workerUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
