package com.mavis.doublerecording.domain.video;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByVideoId(String videoId);

    Optional<Video> findBySessionId(String sessionId);
}
