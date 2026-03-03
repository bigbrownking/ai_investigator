package org.di.digital.repository;

import org.di.digital.model.QueueState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueStateRepository extends MongoRepository<QueueState, String> {
}