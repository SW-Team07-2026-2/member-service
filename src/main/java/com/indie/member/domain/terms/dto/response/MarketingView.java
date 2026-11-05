package com.indie.member.domain.terms.dto.response;

import java.time.OffsetDateTime;

/** M18 — 화면이 at 으로 처리 날짜를 안내합니다 (정보통신망법 제50조) */
public record MarketingView(boolean agreed, OffsetDateTime at) {}
