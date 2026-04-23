package com.crackhash.manager.model;

import com.crackhash.manager.WorkerInfo;
import com.crackhash.requests.Status;
import com.crackhash.requests.WorkerResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "requests")
public class Request {

    @Id
    private String requestId;
    private String hash;
    private int maxLength;
    private Status status;
    private List<String> words = new ArrayList<>();
    private int workersActive;
    private int totalParts;
    private Date lastResponseTime = new Date();
    private int progress = 0;

    public Request(String requestId, String hash, int maxLength, Status status, int workersActive) {
        this.requestId = requestId;
        this.hash = hash;
        this.maxLength = maxLength;
        this.status = status;
        this.workersActive = workersActive;
        this.totalParts = workersActive;
    }

    public void updateProgress(int newProgress){
        if (progress < newProgress) progress = newProgress;
        if(workersActive == 0){
            progress = 100;
        }
    }

    public void updateWithWorkerResponse(WorkerResponse result) {
        status = Status.HALF_READY;
        if (result.getAnswers() != null && result.getAnswers().getWords() != null) {
            words.addAll(result.getAnswers().getWords());
        }
        if (workersActive > 0) workersActive--;
        if(workersActive == 0){
            status = Status.READY;
            progress = 100;
        }
        lastResponseTime = new Date();
    }
}