package com.mavis.doublerecording.domain.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskRepository extends JpaRepository<RiskQuestionnaire, Long> {

    Optional<RiskQuestionnaire> findByCustomerId(String customerId);

    /** 获取客户最近一次评估(按时间倒序) */
    Optional<RiskQuestionnaire> findTopByCustomerIdOrderByAssessTimeDesc(String customerId);
}
