package com.mavis.doublerecording.domain.script;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_script_keyword")
public class ScriptKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;

    @Column(name = "version", nullable = false, length = 16)
    private String version;

    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "keyword", nullable = false, length = 64)
    private String keyword;

    @Column(name = "priority", nullable = false, length = 8)
    private String priority;  // P0/P1/P2

    @Column(name = "match_type", length = 16)
    private String matchType = "EXACT";
}
