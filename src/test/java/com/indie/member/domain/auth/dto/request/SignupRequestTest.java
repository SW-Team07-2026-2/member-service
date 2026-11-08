package com.indie.member.domain.auth.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 11/08 취향 선택 필수 — 아티스트 0개로는 가입할 수 없습니다 */
class SignupRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNoArtist() {
        assertThat(paths(artists(0))).containsExactly("artistIds");
    }

    @Test
    void acceptsOneToFifty() {
        assertThat(paths(artists(1))).isEmpty();
        assertThat(paths(artists(50))).isEmpty();
    }

    @Test
    void rejectsOverFifty() {
        assertThat(paths(artists(51))).containsExactly("artistIds");
    }

    private Set<String> paths(List<Long> artistIds) {
        return validator.validate(new SignupRequest("signup-token", artistIds, List.of())).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static List<Long> artists(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }
}
