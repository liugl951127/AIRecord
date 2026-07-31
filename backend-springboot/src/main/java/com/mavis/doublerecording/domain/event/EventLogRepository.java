package com.mavis.doublerecording.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    boolean existsByEventId(String eventId);

    @Query("SELECT e FROM EventLog e WHERE e.sessionId = :sessionId ORDER BY e.sequenceNo ASC")
    List<EventLog> findBySessionIdOrderBySequenceNo(@Param("sessionId") String sessionId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM EventLog e WHERE e.sessionId = :sessionId")
    Long maxSequenceNo(@Param("sessionId") String sessionId);
}
