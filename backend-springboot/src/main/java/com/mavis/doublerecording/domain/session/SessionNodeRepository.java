package com.mavis.doublerecording.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionNodeRepository extends JpaRepository<SessionNode, Long> {

    List<SessionNode> findBySessionIdOrderByNodeSeqAsc(String sessionId);

    Optional<SessionNode> findBySessionIdAndNodeSeq(String sessionId, Integer nodeSeq);
}
