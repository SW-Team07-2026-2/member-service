package com.indie.member.domain.terms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** terms — 약관 원문 · 버전 (V8). 운영은 마이그레이션으로만 들어오고 바뀌지 않습니다 — 새 버전은 새 행 (of 는 테스트 픽스처) */
@Entity
@Table(name = "terms")
public class TermsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TermsCode code;

    private String version;
    private String title;
    private String content;            // 마크다운
    private boolean required;
    private OffsetDateTime effectiveAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TermsEntity() {}

    public static TermsEntity of(TermsCode code, String version, String title, String content, boolean required,
                                 OffsetDateTime effectiveAt) {
        TermsEntity e = new TermsEntity();
        e.code = code;
        e.version = version;
        e.title = title;
        e.content = content;
        e.required = required;
        e.effectiveAt = effectiveAt;
        return e;
    }

    public Long getId() { return id; }
    public TermsCode getCode() { return code; }
    public String getVersion() { return version; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isRequired() { return required; }
}
