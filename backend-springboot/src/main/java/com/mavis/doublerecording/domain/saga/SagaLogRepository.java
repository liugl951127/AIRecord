package com.mavis.doublerecording.domain.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {

    Optional<SagaLog> findBySagaId(String sagaId);

    Optional<SagaLog> findBySessionId(String sessionId);
}
