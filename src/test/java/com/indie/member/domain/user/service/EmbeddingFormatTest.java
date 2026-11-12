package com.indie.member.domain.user.service;

import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 회원 벡터 형식 — pgvector 텍스트 '[숫자,숫자,…]' 인지만 봅니다. 숫자 개수(차원)는 제한하지 않습니다 */
class EmbeddingFormatTest {

    @Test
    void acceptsPgvectorTextAndCountsNumbers() {
        assertThat(EmbeddingService.dimension("[1]")).isEqualTo(1);
        assertThat(EmbeddingService.dimension("[0.0123,-0.0456, 1e-3, +2, .5, 3.]")).isEqualTo(6);
        assertThat(EmbeddingService.dimension("[" + "0.1,".repeat(4095) + "0.1]")).isEqualTo(4096);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "1,2,3", "[1,2", "1,2]", "[1,,2]", "[1,2,]", "[a,b]", "[NaN]", "[Infinity]",
            "[1e999]", "[0x1p3]", "[1f]", "[1 2]", "{\"a\":1}", "[[1,2]]"})
    void rejectsAnythingElse(String text) {
        assertThatThrownBy(() -> EmbeddingService.dimension(text)).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo("INVALID_REQUEST"));
    }
}
