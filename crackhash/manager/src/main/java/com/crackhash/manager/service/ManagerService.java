package com.crackhash.manager.service;

import com.crackhash.requests.*;
import com.crackhash.manager.WorkerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private static final List<String> ALPHABET = List.of("a","b","c","d","e","f","g","h","i","j","k","l","m",
            "n","o","p","q","r","s","t","u","v","w","x","y","z",
            "0","1","2","3","4","5","6","7","8","9");

    private final BlockingQueue<QueuedTask> taskQueue = new LinkedBlockingQueue<>();
    private volatile boolean taskInProgress = false;
    private static final Logger logger = LoggerFactory.getLogger(ManagerService.class);
    private final WorkerInfo workerInfo;
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Map<String, HashStatus> requests = new ConcurrentHashMap<>();
    private final Map<String, String> solvedRequests = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, String> requestHashes = new ConcurrentHashMap<>();
    private final Map<String, Integer> workerResponses = new ConcurrentHashMap<>();
    private final Map<String, Boolean> workerHealth = new ConcurrentHashMap<>();
    private final Map<String, Long> requestStartTime = new ConcurrentHashMap<>();
    private final Map<String, HashRequest> originalRequests = new ConcurrentHashMap<>();

    private static final long TASK_TIMEOUT = 30000;

    @PostConstruct
    public void initWorkers() {
        for (String url : workerInfo.getWorkerUrls()) {
            workerHealth.put(url, true);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void checkWorkersHealth() {
        logger.info("CHECK");
        for (String workerUrl : workerInfo.getWorkerUrls()) {
            try {
                restTemplate.getForEntity(workerUrl + "/health", String.class);
                logger.info("Worker {} is UP", workerUrl);
                workerHealth.put(workerUrl, true);
            } catch (Exception e) {
                workerHealth.put(workerUrl, false);
                logger.warn("Worker {} is DOWN", workerUrl);
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : requestStartTime.entrySet()) {
            String requestId = entry.getKey();
            long startTime = entry.getValue();
            if (now - startTime > TASK_TIMEOUT) {
                HashStatus status = requests.get(requestId);
                if (status == null || status.getStatus() == Status.READY) {
                    continue;
                }
                int responses = workerResponses.getOrDefault(requestId, 0);
                if (responses > 0) {
                    status.setStatus(Status.HALF_READY);
                } else {
                    status.setStatus(Status.ERROR);
                }
                logger.warn("Task {} timed out", requestId);
                taskInProgress = false;
                requestStartTime.remove(requestId);
                workerResponses.remove(requestId);
                checkRequest();
            }
        }
    }

    private String cacheKey(HashRequest request) {
        return request.getHash() + ":" + request.getMaxLength();
    }

    private static class QueuedTask {
        final String requestId;
        final HashRequest request;
        QueuedTask(String requestId, HashRequest request) {
            this.requestId = requestId;
            this.request = request;
        }
    }

    public String processRequestT(HashRequest request){
        String key = cacheKey(request);
        String existingRequestId = solvedRequests.get(key);
        if (existingRequestId != null) {
            HashStatus status = requests.get(existingRequestId);
            if (status != null && status.getStatus() == Status.READY) {
                logger.info("Cache hit for hash {}", request.getHash());
                return existingRequestId;
            }
        }
        String requestId = UUID.randomUUID().toString();
        logger.info("Received new hash cracking request with requestId: {}", requestId);
        requests.put(requestId, new HashStatus(Status.IN_PROGRESS, new ArrayList<>()));
        taskQueue.add(new QueuedTask(requestId, request));
        originalRequests.put(requestId, request);
        checkRequest();
        return requestId;
    }

    public void checkRequest(){
        if(!taskInProgress){
            logger.info("NO TASK");
            QueuedTask queuedTask = taskQueue.poll();
            if (queuedTask != null) {
                sendToWorkers(queuedTask.requestId,queuedTask.request);
            }
        }
    }

    public void sendToWorkers(String requestId, HashRequest request){
        taskInProgress = true;
        logger.info("BEGIN SEND");
        requestStartTime.put(requestId, System.currentTimeMillis());
        workerResponses.put(requestId, 0);
        List<String> aliveWorkers = workerInfo.getWorkerUrls()
                .stream()
                .filter(url -> workerHealth.getOrDefault(url, false))
                .toList();
        if (aliveWorkers.isEmpty()) {
            logger.error("No alive workers available");
            return;
        }
        for (int i = 0; i < aliveWorkers.size(); i++) {
            WorkerRequest workerRequest = new WorkerRequest();
            workerRequest.setRequestId(requestId);
            workerRequest.setHash(request.getHash());
            workerRequest.setMaxLength(request.getMaxLength());
            workerRequest.setPartNumber(i);
            workerRequest.setPartCount(aliveWorkers.size());
            workerRequest.setAlphabet(ALPHABET);
            String workerUrl = aliveWorkers.get(i);
            int workerIndex = i;
            executorService.submit(() -> sendTask(workerRequest, requestId, workerIndex, workerUrl));
        }
    }

    protected void sendTask(
            WorkerRequest task,
            String requestId,
            int workerIndex,
            String workerUrl
    ) {
        try {
            logger.info(
                    "Sending task {} for hash {} to worker {}",
                    task,
                    task.getHash(),
                    workerUrl
            );
            restTemplate.postForEntity(
                    workerUrl + "/internal/api/worker/hash/crack/task",
                    task,
                    Void.class
            );
        } catch (Exception e) {
            logger.error(
                    "Error sending task to worker {} for requestId {}: {}",
                    workerIndex,
                    requestId,
                    e.getMessage(),
                    e
            );
        }
    }

    public HashStatus getRequestStatus(String requestId) {
        HashStatus status = requests.getOrDefault(requestId, new HashStatus(Status.ERROR, null));
        logger.info("Request status for requestId {}: {}", requestId, status.getStatus());
        return status;
    }

    public void processWorkerResult(WorkerResponse response) {
        HashStatus status = requests.get(response.getRequestId());
        if (status != null) {
            if (response.getAnswers() != null &&
                    response.getAnswers().getWords() != null) {
                status.getData().addAll(response.getAnswers().getWords());
            }
            int responses = workerResponses.merge(response.getRequestId(), 1, Integer::sum);
            int totalWorkers = workerInfo.getWorkerCount();
            if (responses < totalWorkers) {
                status.setStatus(Status.HALF_READY);
                logger.info(
                        "Worker result received for requestId {}, {}/{} workers finished",
                        response.getRequestId(),
                        responses,
                        totalWorkers
                );
                return;
            }
            status.setStatus(Status.READY);
            logger.info(
                    "All workers finished for requestId {}, marking READY",
                    response.getRequestId()
            );
            taskInProgress = false;
            workerResponses.remove(response.getRequestId());
            requestStartTime.remove(response.getRequestId());
            HashRequest original = originalRequests.get(response.getRequestId());
            if (original != null) {
                String key = cacheKey(original);
                solvedRequests.put(key, response.getRequestId());
            }
            checkRequest();
        } else {
            logger.warn(
                    "Received result for unknown requestId: {}",
                    response.getRequestId()
            );
        }
    }
}
