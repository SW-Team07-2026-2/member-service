package com.indie.member.domain.user.entity;

import com.indie.member.domain.user.exception.UserErrorCode;
import com.indie.member.global.common.exception.GeneralException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** 닉네임 규칙 — 2~10자 · 공백 · 제어문자 거부 · 금칙어 1종. 중복은 허용합니다 (식별자는 id) */
public final class NicknamePolicy {

    static final int MIN = 2;
    static final int MAX = 10;

    // TODO(결정대기): 금칙어 목록 — 10개 내외 하드코딩 (§19-4)
    private static final List<String> BANNED = List.of(
            "관리자", "운영자", "운영팀", "고객센터", "admin", "official", "공식", "시발", "씨발", "병신");

    /** PATCH /api/members/me — 짧으면 400 NICKNAME_TOO_SHORT · 길면 NICKNAME_TOO_LONG · 그 밖의 위반은 INVALID_NICKNAME */
    public static String validate(String nickname) {
        if (nickname == null) throw invalid();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < MIN) throw new GeneralException(UserErrorCode.NICKNAME_TOO_SHORT);
        if (length > MAX) throw new GeneralException(UserErrorCode.NICKNAME_TOO_LONG);
        if (nickname.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
            throw invalid();
        }
        String lower = nickname.toLowerCase(Locale.ROOT);
        if (BANNED.stream().anyMatch(lower::contains)) {
            throw new GeneralException(UserErrorCode.INVALID_NICKNAME, "사용할 수 없는 닉네임입니다");
        }
        return nickname;
    }

    /** 가입 시 IdP 닉네임(카카오 nickname · 구글 name)을 기본값으로 — 공백 · 제어문자 제거, 짧으면 기본값, 길면 MAX 로 자릅니다 */
    public static String fromProvider(String raw) {
        String cleaned = raw == null ? "" : raw.codePoints()
                .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        if (cleaned.codePointCount(0, cleaned.length()) < MIN) cleaned = "user";
        return truncate(cleaned, MAX);
    }

    /** 충돌 시 숫자 4자리 — 합쳐서 MAX 를 넘지 않게 앞부분을 줄입니다 */
    public static String withSuffix(String nickname) {
        return truncate(nickname, MAX - 4) + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private static String truncate(String s, int limit) {
        return s.codePointCount(0, s.length()) > limit ? s.substring(0, s.offsetByCodePoints(0, limit)) : s;
    }

    private static GeneralException invalid() {
        return new GeneralException(UserErrorCode.INVALID_NICKNAME);
    }

    private NicknamePolicy() {}
}
