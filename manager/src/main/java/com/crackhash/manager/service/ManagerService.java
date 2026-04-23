package com.crackhash.manager.service;

import com.crackhash.manager.model.Request;
import com.crackhash.manager.model.TaskStatus;
import com.crackhash.manager.model.WorkerTask;
import com.crackhash.manager.repository.HashRequestRepository;
import com.crackhash.manager.repository.WorkerTaskRepository;
import com.crackhash.requests.*;
import com.crackhash.manager.WorkerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private static final List<String> ALPHABET = List.of("a","b","c","d","e","f","g","h","i","j","k","l","m",
            "n","o","p","q","r","s","t","u","v","w","x","y","z",
            "0","1","2","3","4","5","6","7","8","9");
    private static final long TASK_TIMEOUT = 30000;

    private final HashRequestRepository requestRepository;
    private final WorkerTaskRepository workerTaskRepository;
    private static final Logger logger = LoggerFactory.getLogger(ManagerService.class);
    private final WorkerInfo workerInfo;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Boolean> workerHealth = new ConcurrentHashMap<>();

    private final RabbitTemplate rabbitTemplate;
    @Value("${rabbitmq.manager_to_worker_queue.name}")
    private String managerToWorkerQueue;
    @Value("${rabbitmq.exchange.name}")
    private String directExchange;


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
                restTemplate.getForEntity(workerUrl + "/internal/api/worker/health", String.class);
                logger.info("Worker {} is UP", workerUrl);
                workerHealth.put(workerUrl, true);
            } catch (Exception e) {
                workerHealth.put(workerUrl, false);
                logger.warn("Worker {} is DOWN", workerUrl);
            }
        }
    }

    public String processRequest(HashRequest request){
        Optional<Request> existingRequestOpt = requestRepository
                .findByHashAndMaxLength(request.getHash(), request.getMaxLength());
        if (existingRequestOpt.isPresent()) {
            Request existing = existingRequestOpt.get();
            logger.info("Found existing hash {} in database, requestId: {}", request.getHash(), existing.getRequestId());
            return existing.getRequestId();
        }
        String requestId = UUID.randomUUID().toString();
        logger.info("Received new hash cracking request with requestId: {}", requestId);
        saveRequestToDatabase(requestId, request);
        sendToWorkers(requestId, request);
        return requestId;
    }

    @Scheduled(fixedDelay = 5000)
    public void retryPendingTasks() {
        List<WorkerTask> pendingTasks = workerTaskRepository.findByStatus(TaskStatus.PENDING);
        for (WorkerTask task : pendingTasks) {
            try {
                WorkerRequest workerRequest = new WorkerRequest();
                workerRequest.setRequestId(task.getRequestId());
                workerRequest.setPartNumber(task.getPartNumber());
                sendTask(workerRequest, task.getRequestId(), task.getPartNumber(), task.getWorkerUrl());
                task.setStatus(TaskStatus.IN_PROGRESS);
                task.setUpdatedAt(new Date());
                workerTaskRepository.save(task);
                logger.info("Retried task {}", task.getPartNumber());
            } catch (Exception e) {
                logger.error("Retry failed for task {}", task.getPartNumber());
            }
        }
    }

    private void saveRequestToDatabase(String requestId, HashRequest request) {
        Request hashRequest = new Request(
                requestId,
                request.getHash(),
                request.getMaxLength(),
                Status.IN_PROGRESS,
                workerInfo.getWorkerCount()
        );
        requestRepository.save(hashRequest);
    }

    public void sendToWorkers(String requestId, HashRequest request){
        logger.info("BEGIN SEND");
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
            String workerUrl = aliveWorkers.get(i);;
            WorkerTask taskEntity = new WorkerTask(
                    requestId,
                    i,
                    workerUrl,
                    TaskStatus.PENDING,
                    new Date(),
                    new Date()
            );
            try {
                sendTask(workerRequest, requestId, i, workerUrl);
                taskEntity.setStatus(TaskStatus.IN_PROGRESS);
                taskEntity.setUpdatedAt(new Date());
                logger.info("Task sent successfully for part {}", i);
            } catch (Exception e) {
                logger.error("Failed to send task for part {}", i, e);
            }
            workerTaskRepository.save(taskEntity);
        }
    }

    protected void sendTask(WorkerRequest task, String requestId, int workerIndex, String workerUrl) {
        try {
            logger.info(
                    "Sending task {} for hash {} to worker {}", task, task.getHash(), workerUrl);
            rabbitTemplate.convertAndSend(
                    directExchange,
                    managerToWorkerQueue,
                    task,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
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
        Optional<Request> requestOpt = requestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            logger.warn("Request with requestId {} not found in DB", requestId);
            return new HashStatus(Status.ERROR, null, 0);
        }
        Request request = requestOpt.get();
        Status status = request.getStatus();
        List<String> wordsFound = request.getWords();
        int sumProgress = (request.getTotalParts() - request.getWorkersActive())*100/request.getTotalParts();
        for (String workerUrl : workerInfo.getWorkerUrls()) {
            try {
                Integer workerProgress = restTemplate.getForObject(
                        workerUrl + "/internal/api/worker/progress/" + requestId,
                        Integer.class
                );
                if (workerProgress != null) {
                    sumProgress += workerProgress;
                }
            } catch (Exception e) {
                logger.warn("Failed to get progress from worker {}", workerUrl);
            }
        }

        request.updateProgress(sumProgress);
        requestRepository.save(request);
        HashStatus hashStatus = new HashStatus(status, wordsFound, request.getProgress());
        logger.info("Request status for requestId {}: {}", requestId, hashStatus.getStatus());
        return hashStatus;
    }

    public synchronized void processWorkerResult(WorkerResponse response) {
        Optional<Request> requestOpt = requestRepository.findById(response.getRequestId());
        if (requestOpt.isEmpty()) {
            logger.warn("Received result for unknown requestId: {}", response.getRequestId());
            return;
        }
        Request request = requestOpt.get();
        request.updateWithWorkerResponse(response);
        request.setLastResponseTime(new Date());
        requestRepository.save(request);
        workerTaskRepository.deleteByRequestIdAndPartNumber(
                response.getRequestId(),
                response.getPartNumber()
        );
        logger.info("Task {} completed and removed", response.getPartNumber());
    }

    @PostConstruct
    public void recoverState() {
        List<WorkerTask> lostTasks =
                workerTaskRepository.findByStatus(TaskStatus.IN_PROGRESS);
        for (WorkerTask task : lostTasks) {
            task.setStatus(TaskStatus.PENDING);
            workerTaskRepository.save(task);
        }
        logger.info("Recovered {} tasks", lostTasks.size());
    }
}
