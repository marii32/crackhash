package com.crackhash.manager;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Getter
@Configuration
public class WorkerInfo {

    @Value("${WORKER1_URL}")
    private String worker1Url;

    @Value("${WORKER2_URL}")
    private String worker2Url;

    @Value("${WORKER3_URL}")
    private String worker3Url;

    public List<String> getWorkerUrls() {
        return List.of(worker1Url, worker2Url, worker3Url);
    }

    public int getWorkerCount() {
        return getWorkerUrls().size();
    }
}