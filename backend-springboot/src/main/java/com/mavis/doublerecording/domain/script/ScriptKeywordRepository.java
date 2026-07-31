package com.mavis.doublerecording.domain.script;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptKeywordRepository extends JpaRepository<ScriptKeyword, Long> {

    List<ScriptKeyword> findByTemplateIdAndVersionAndNodeSeq(String templateId, String version, Integer nodeSeq);
}
