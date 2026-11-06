package com.indie.member.domain.terms.entity;

import java.util.Arrays;
import java.util.List;

/**
 * 약관 종류 — 선언 순서가 가입 화면 순서입니다.
 * consent=false 는 동의받지 않고 게시만 하는 문서 — 가입 화면(M24) · 가입 검사 · 동의 이력에 없고 M25 로만 내려갑니다
 */
public enum TermsCode {

    AGE14(true), TOS(true), PRIVACY(true), MARKETING(true),
    PRIVACY_POLICY(false);   // 11/06 개인정보처리방침 — 개인정보 보호법 제30조 게시 (푸터)

    private final boolean consent;

    TermsCode(boolean consent) {
        this.consent = consent;
    }

    /** 동의받는 약관 — 가입 화면 순서 */
    public static List<TermsCode> consents() {
        return Arrays.stream(values()).filter(c -> c.consent).toList();
    }
}
