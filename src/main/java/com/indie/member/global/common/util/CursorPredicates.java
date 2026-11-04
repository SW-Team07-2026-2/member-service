package com.indie.member.global.common.util;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.NumberExpression;

import java.time.OffsetDateTime;

/** QueryDSL 복합 커서 조건 — 목록 정렬이 ORDER BY at DESC, id DESC 일 때 */
public final class CursorPredicates {

    private CursorPredicates() {}

    /**
     * (at, id) < (cursor.at, cursor.id) 와 같은 뜻 — 같은 시각 여러 건에서 누락 · 중복이 없습니다.
     * at <= cursor.at 를 따로 겁니다 — OR 만 있으면 인덱스를 처음부터 훑고, 이게 있으면 커서 위치부터 읽습니다.
     * cursor 가 null(첫 페이지)이면 null — QueryDSL where 가 무시합니다.
     */
    public static BooleanExpression before(ComparableExpression<OffsetDateTime> at, NumberExpression<Long> id, Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        return at.loe(cursor.at()).and(at.lt(cursor.at()).or(id.lt(cursor.id())));
    }
}
