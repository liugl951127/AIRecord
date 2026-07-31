package com.mavis.doublerecording.domain.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QualityRuleRepository extends JpaRepository<QualityRule, Long> {

    List<QualityRule> findByEnabledTrue();
}
