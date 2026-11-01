package com.indie.member.global.common.util;

import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void roundTripKeepsMicroseconds() {
        Cursor cursor = new Cursor(OffsetDateTime.parse("2026-10-21T05:44:09.171641Z"), 42);
        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void idOnlyCursor() {
        assertThat(Cursor.decode(new Cursor(null, 7).encode())).isEqualTo(new Cursor(null, 7));
    }

    @Test
    void blankMeansFirstPage() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode("")).isNull();
    }

    @Test
    void brokenCursorIs400() {
        assertThatThrownBy(() -> Cursor.decode("@@@")).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getStatus().value()).isEqualTo(400));
    }

    @Test
    void pageUsesExtraRowToDetectNext() {
        CursorPage<Integer> page = CursorPage.of(List.of(5, 4, 3), 2, i -> new Cursor(null, i));
        assertThat(page.items()).containsExactly(5, 4);
        assertThat(page.hasNext()).isTrue();
        assertThat(Cursor.decode(page.nextCursor()).id()).isEqualTo(4);
    }
}
