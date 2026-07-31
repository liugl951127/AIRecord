package com.mavis.doublerecording.domain.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QualityReportRepository extends JpaRepository<QualityReport, Long> {

    Optional<QualityReport> findByReportId(String reportId);

    Optional<QualityReport> findBySessionId(String sessionId);
}
