package com.crackhash.manager.repository;

import com.crackhash.manager.model.Request;
import com.crackhash.requests.Status;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HashRequestRepository extends MongoRepository<Request, String>{
    Optional<Request> findByHashAndMaxLength(String hash, int maxLength);
}
