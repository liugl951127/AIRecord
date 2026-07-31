package com.mavis.doublerecording.domain.saga;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {

    Optional<SagaLog> findBySagaId(String sagaId);

    Optional<SagaLog> findBySessionId(String sessionId);

    Page<SagaLog> findByState(String state, Pageable pageable);

    Page<SagaLog> findBySagaType(String sagaType, Pageable pageable);

    Page<SagaLog> findBySessionIdContaining(String sessionId, Pageable pageable);

    @Query("SELECT s FROM SagaLog s WHERE " +
           "(:state IS NULL OR s.state = :state) AND " +
           "(:sagaType IS NULL OR s.sagaType = :sagaType) AND " +
           "(:sessionId IS NULL OR s.sessionId LIKE %:sessionId%) AND " +
           "(:startTime IS NULL OR s.startedAt >= :startTime) " +
           "ORDER BY s.startedAt DESC")
    Page<SagaLog> search(@Param("state") String state,
                          @Param("sagaType") String sagaType,
                          @Param("sessionId") String sessionId,
                          @Param("startTime") LocalDateTime startTime,
                          Pageable pageable);

    @Query("SELECT s.state as state, COUNT(s) as count FROM SagaLog s " +
           "WHERE s.startedAt >= :startTime GROUP BY s.state")
    List<Object[]> countByStateSince(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT s.sagaType as type, COUNT(s) as count FROM SagaLog s " +
           "WHERE s.startedAt >= :startTime GROUP BY s.sagaType")
    List<Object[]> countByTypeSince(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT FUNCTION('DATE_FORMAT', s.startedAt, '%Y-%m-%d %H:00') as hour, COUNT(s) as count, " +
           "SUM(CASE WHEN s.state='COMPLETED' THEN 1 ELSE 0 END) as successCount " +
           "FROM SagaLog s WHERE s.startedAt >= :startTime GROUP BY FUNCTION('DATE_FORMAT', s.startedAt, '%Y-%m-%d %H:00')")
    List<Object[]> hourlyStatsSince(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT s.sagaId as sagaId, s.sagaType as sagaType, s.currentStep as currentStep, " +
           "s.sessionId as sessionId, s.errorMessage as errorMessage, s.startedAt as startedAt " +
           "FROM SagaLog s WHERE s.errorMessage LIKE '%CompensationFailed%' " +
           "OR s.state = 'COMPENSATING' ORDER BY s.startedAt DESC")
    List<SagaLog> findPendingManual();

    long countByState(String state);
}
