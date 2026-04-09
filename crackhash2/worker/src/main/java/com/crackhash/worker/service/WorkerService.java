package com.crackhash.worker.service;

import com.crackhash.requests.*;
import com.crackhash.worker.ManagerInfo;
import com.crackhash.worker.util.AlphabetGenerator;
import com.crackhash.worker.util.MD5Util;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger logger = LoggerFactory.getLogger(WorkerService.class);

    @Autowired
    private final RabbitTemplate rabbitTemplate;
    @Value("${rabbitmq.worker_to_manager_queue.name}")
    private String workerToManagerQueue;
    @Value("${rabbitmq.exchange.name}")
    private String directExchange;
    private final Queue<WorkerResponse> retryQueue = new ConcurrentLinkedQueue<>();

    public void processTask(WorkerRequest request) {
        logger.info("Starting hash cracking for requestId: {} with task: {}",
                request.getRequestId(), request);
        List<String> results = new ArrayList<>();
        AlphabetGenerator generator = new AlphabetGenerator(request.getAlphabet(), request.getMaxLength());
        try {
            for (String word : generator.getPart(
                    request.getPartNumber(),
                    request.getPartCount()
            )) {
                logger.debug("Checking word: {}", word);
                if (MD5Util.hash(word).equals(request.getHash())) {
                    logger.info("Found matching word: {}", word);
                    results.add(word);
                }
            }
        } catch (Exception e) {
            logger.error(
                    "Error while generating combinations or cracking hash for requestId: {}",
                    request.getRequestId(),
                    e
            );
        }
        if (results.isEmpty()) {
            logger.info("No matching words found for requestId: {}", request.getRequestId());
        }
        WorkerResponse response = new WorkerResponse();
        response.setRequestId(request.getRequestId());
        response.setPartNumber(request.getPartNumber());
        WorkerResponse.Answers answers = new WorkerResponse.Answers();
        answers.setWords(results);
        response.setAnswers(answers);
        try {
            sendResult(response);
            logger.info("Successfully sent results to manager for requestId {}", request.getRequestId());
        } catch (Exception e) {
            logger.error("RabbitMQ is down, adding to retry queue");
            retryQueue.add(response);
        }
        logger.info("Finished hash cracking for requestId: {}", request.getRequestId());
    }

    private void sendResult(WorkerResponse response) {
        rabbitTemplate.convertAndSend(
                directExchange,
                workerToManagerQueue,
                response,
                message -> {
                    message.getMessageProperties().setDeliveryMode(
                            org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                    );
                    return message;
                }
        );
    }

    @Scheduled(fixedDelay = 1000)
    public void retrySendingResults() {
        if (retryQueue.isEmpty()) return;
        logger.info("Retrying sending results, queue size: {}", retryQueue.size());
        Iterator<WorkerResponse> iterator = retryQueue.iterator();
        while (iterator.hasNext()) {
            WorkerResponse response = iterator.next();
            try {
                sendResult(response);
                iterator.remove();
                logger.info("Successfully resent result {}", response.getRequestId());
            } catch (Exception e) {
                logger.warn("RabbitMQ still down...");
                break;
            }
        }
    }
}
