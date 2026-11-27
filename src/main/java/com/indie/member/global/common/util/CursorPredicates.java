package com.indie.member.global.common.util;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.NumberExpression;

import java.time.OffsetDateTime;

/** QueryDSL 복합 커서 조건 - 목록 정렬이 ORDER BY at DESC, id DESC 일 때 */
public final class CursorPredicates {

    private CursorPredicates() {}

    /**
     * 시각과 ID를 기준으로 다음 페이지 조회 조건을 만듭니다.
     *
     * @param at 정렬 기준 시각 표현식
     * @param id CursorPredicates ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     */
    public static BooleanExpression before(ComparableExpression<OffsetDateTime> at, NumberExpression<Long> id, Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        return at.loe(cursor.at()).and(at.lt(cursor.at()).or(id.lt(cursor.id())));
    }
}
