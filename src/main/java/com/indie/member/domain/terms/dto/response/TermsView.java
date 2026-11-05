package com.indie.member.domain.terms.dto.response;

import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;

/** M24 한 줄 — content 는 마크다운 */
public record TermsView(Long termsId, TermsCode code, String version, String title, String content, boolean required) {

    public static TermsView of(TermsEntity t) {
        return new TermsView(t.getId(), t.getCode(), t.getVersion(), t.getTitle(), t.getContent(), t.isRequired());
    }
}
