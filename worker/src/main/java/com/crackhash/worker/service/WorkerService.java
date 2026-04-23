package com.crackhash.worker.service;

import com.crackhash.requests.*;
import com.crackhash.worker.util.AlphabetGenerator;
import com.crackhash.worker.util.MD5Util;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ConcurrentHashMap<String, WorkerResponse> retryMap = new ConcurrentHashMap<>();
    private String curId;
    private int progress = 0;

    public int getProgress(String taskId) {
        if(taskId.equals(curId)){
            logger.info("ID: {} {}", taskId, progress);
            return  progress;
        }
        else return 0;
    }


    public void processTask(WorkerRequest request) {
        logger.info("Starting hash cracking for requestId: {} with task: {}",
                request.getRequestId(), request);
        String key = request.getRequestId()+ "_" + request.getPartNumber();
        WorkerResponse cached = retryMap.get(key);
        if (cached != null) {
            logger.info("Cache hit in memory for {}", key);
            retryMap.remove(key);
            sendResult(cached);
            return;
        }
        progress = 0;
        List<String> results = new ArrayList<>();
        AlphabetGenerator generator = new AlphabetGenerator(request.getAlphabet(), request.getMaxLength());
        curId = request.getRequestId();
        long totalWords = 0;
        for (int wordSize = 1; wordSize <= request.getMaxLength(); wordSize++) {
            long count = 1;
            for (int i = 0; i < wordSize; i++) {
                count *= request.getAlphabet().size();
            }
            totalWords += count;
        }
        long processed = 0;
        try {
            for (String word : generator.getPart(request.getPartNumber(), request.getPartCount())) {
                processed++;
                progress = (int) ((processed * 100.0) / totalWords);
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
            logger.error("RabbitMQ is down, saving to in-memory retryMap. key={}", key, e);
            retryMap.put(key, response);
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
}
