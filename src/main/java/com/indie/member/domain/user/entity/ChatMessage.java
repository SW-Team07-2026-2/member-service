package com.indie.member.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.CodePointLength;

import java.time.OffsetDateTime;

public record ChatMessage(@NotNull Role role,
                          @NotBlank @CodePointLength(max = 200) String content,
                          @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime spokenAt) {

    public enum Role { USER, ASSISTANT }

    /** 숫자를 문자열이나 epoch 시각으로 자동 변환하지 않습니다. 발화 시각에는 오프셋이 필요합니다. */
    @JsonCreator
    public static ChatMessage fromJson(@JsonProperty("role") String role,
                                       @JsonProperty("content") JsonNode content,
                                       @JsonProperty("spokenAt") String spokenAt) {
        if (content != null && !content.isNull() && !content.isTextual()) {
            throw new IllegalArgumentException("content must be a string");
        }
        return new ChatMessage(role == null ? null : Role.valueOf(role),
                content == null || content.isNull() ? null : content.textValue(),
                spokenAt == null ? null : OffsetDateTime.parse(spokenAt));
    }
}
