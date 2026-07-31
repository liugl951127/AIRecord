package com.mavis.doublerecording.domain.script;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptNodeRepository extends JpaRepository<ScriptNode, Long> {

    List<ScriptNode> findByTemplateIdAndVersionOrderByNodeSeqAsc(String templateId, String version);

    Optional<ScriptNode> findByTemplateIdAndVersionAndNodeSeq(String templateId, String version, Integer nodeSeq);
}
