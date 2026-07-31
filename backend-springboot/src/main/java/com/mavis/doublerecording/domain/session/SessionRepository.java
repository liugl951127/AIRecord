package com.mavis.doublerecording.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<DoubleRecordingSession, Long> {

    Optional<DoubleRecordingSession> findBySessionId(String sessionId);

    @Query("SELECT s FROM DoubleRecordingSession s WHERE s.customerId = :customerId ORDER BY s.createdAt DESC")
    List<DoubleRecordingSession> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") String customerId);

    @Modifying
    @Query("UPDATE DoubleRecordingSession s SET s.currentState = :state, s.updatedAt = :now WHERE s.sessionId = :sessionId")
    int updateState(@Param("sessionId") String sessionId, @Param("state") String state, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE DoubleRecordingSession s SET s.currentNodeSeq = :nodeSeq, s.updatedAt = :now WHERE s.sessionId = :sessionId")
    int updateCurrentNode(@Param("sessionId") String sessionId, @Param("nodeSeq") int nodeSeq, @Param("now") LocalDateTime now);
}
