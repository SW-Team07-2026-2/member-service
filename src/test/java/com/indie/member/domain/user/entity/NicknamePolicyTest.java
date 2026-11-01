package com.indie.member.domain.user.entity;

import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknamePolicyTest {

    /** DoD 19 — 1자 · 11자 · 공백 → 400 INVALID_NICKNAME */
    @ParameterizedTest
    @ValueSource(strings = {"a", "가나다라마바사아자차카", "인디 러버", "탭\t문자", "운영자123"})
    void invalidNicknamesAreRejected(String nickname) {
        assertThatThrownBy(() -> NicknamePolicy.validate(nickname))
                .isInstanceOfSatisfying(GeneralException.class, e -> {
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(400);
                    assertThat(e.getErrorCode().getCode()).isEqualTo("INVALID_NICKNAME");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"동국", "인디러버", "abcdefghij"})
    void validNicknamesPass(String nickname) {
        assertThat(NicknamePolicy.validate(nickname)).isEqualTo(nickname);
    }

    /** 소셜 닉네임 · 이름이 10자 안이면 그대로 기본값 · 넘으면 10자로 · 충돌 suffix 를 붙여도 10자 */
    @Test
    void providerNicknameIsDefaultAndSuffixFitsMax() {
        assertThat(NicknamePolicy.fromProvider(null)).isEqualTo("user");
        assertThat(NicknamePolicy.fromProvider(" 동 국 ")).isEqualTo("동국");
        assertThat(NicknamePolicy.fromProvider("가나다라마바사아자차")).isEqualTo("가나다라마바사아자차");
        String longName = NicknamePolicy.fromProvider("가나다라마바사아자차카타파하");
        assertThat(longName).isEqualTo("가나다라마바사아자차");
        String suffixed = NicknamePolicy.withSuffix(longName);
        assertThat(suffixed).startsWith("가나다라마바").hasSize(10);
        assertThat(NicknamePolicy.validate(suffixed)).isNotNull();
    }
}
