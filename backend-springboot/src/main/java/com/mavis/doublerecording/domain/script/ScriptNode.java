package com.mavis.doublerecording.domain.script;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_script_node",
       uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version", "node_seq"}))
public class ScriptNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;

    @Column(name = "version", nullable = false, length = 16)
    private String version;

    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    @Column(name = "node_title", nullable = false, length = 64)
    private String nodeTitle;

    @Column(name = "speaker", nullable = false, length = 16)
    private String speaker;

    @Column(name = "script_content", nullable = false, columnDefinition = "TEXT")
    private String scriptContent;

    @Column(name = "required_duration_sec", columnDefinition = "INT DEFAULT 0")
    private Integer requiredDurationSec = 0;

    @Column(name = "trigger_action", length = 32)
    private String triggerAction;

    @Column(name = "next_node_rule", length = 256)
    private String nextNodeRule;
}
