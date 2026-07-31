package com.mavis.doublerecording.domain.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskRepository extends JpaRepository<RiskQuestionnaire, Long> {

    Optional<RiskQuestionnaire> findByCustomerId(String customerId);
}
