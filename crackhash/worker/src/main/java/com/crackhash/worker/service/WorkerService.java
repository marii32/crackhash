package com.crackhash.worker.service;

import com.crackhash.requests.*;
import com.crackhash.worker.ManagerInfo;
import com.crackhash.worker.util.AlphabetGenerator;
import com.crackhash.worker.util.MD5Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private static final Logger logger = LoggerFactory.getLogger(WorkerService.class);

    @Autowired
    private RestTemplate restTemplate = new RestTemplate();
    private final ManagerInfo managerInfo;

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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<WorkerResponse> entity = new HttpEntity<>(response, headers);
            restTemplate.exchange(
                    managerInfo.getManagerUrl() + "/internal/api/manager/hash/crack/request",
                    HttpMethod.PATCH,
                    entity,
                    Void.class
            );
            logger.info("Successfully sent results to manager for requestId {}", request.getRequestId());
        } catch (Exception e) {
            logger.error(
                    "Error while sending results for requestId: {}",
                    request.getRequestId(),
                    e
            );
        }
        logger.info("Finished hash cracking for requestId: {}", request.getRequestId());
    }
}
