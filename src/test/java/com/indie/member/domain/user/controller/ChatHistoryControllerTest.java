package com.indie.member.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.service.ChatHistoryService;
import com.indie.member.global.auth.CurrentUserArgumentResolver;
import com.indie.member.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatHistoryControllerTest {

    private static final String PATH = "/api/members/me/chat-history";
    private static final String MESSAGE = """
            {"role":"USER","content":"공연 추천해줘","spokenAt":"2026-11-06T15:30:00+09:00"}
            """;

    private final ChatHistoryService service = mock(ChatHistoryService.class);
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ChatHistoryController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test
    void bothEndpointsRequireAuthenticatedMember() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(batch(MESSAGE)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-User-Id", "invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void bodyAndQueryCannotSelectAnotherMember() throws Exception {
        ChatMessage message = mapper.readValue(MESSAGE, ChatMessage.class);
        when(service.get(42L)).thenReturn(List.of(message));
        when(service.append(eq(42L), anyList())).thenReturn(List.of(message));

        mvc.perform(get(PATH).header("X-User-Id", "42").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.messages[0].content").value("공연 추천해줘"));
        mvc.perform(post(PATH).header("X-User-Id", "42").param("userId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"messages\":[" + MESSAGE + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.messages[0].spokenAt").value("2026-11-06T15:30:00+09:00"));
        verify(service).get(42L);
        verify(service).append(42L, List.of(message));
        verifyNoMoreInteractions(service);
    }

    @Test
    void accepts200UnicodeCodePoints() throws Exception {
        when(service.append(eq(42L), anyList())).thenAnswer(call -> call.getArgument(1));
        String content = "🎸".repeat(200);
        String body = mapper.writeValueAsString(Map.of("messages", List.of(Map.of(
                "role", "ASSISTANT", "content", content, "spokenAt", "2026-11-06T06:30:00Z"))));

        mvc.perform(post(PATH).header("X-User-Id", "42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.messages[0].content").value(content));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidBatchBeforeSaving(String body) throws Exception {
        mvc.perform(post(PATH).header("X-User-Id", "42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"messages\":null}", "{\"messages\":[]}", batch("null"),
                batch(String.join(",", java.util.Collections.nCopies(51, MESSAGE))),
                batch(MESSAGE.replace("공연 추천해줘", "가".repeat(201))),
                batch(MESSAGE.replace("공연 추천해줘", "🎸".repeat(201))),
                batch(MESSAGE.replace("공연 추천해줘", "   ")),
                batch(MESSAGE.replace("\"공연 추천해줘\"", "null")),
                batch(MESSAGE.replace("\"공연 추천해줘\"", "123")),
                batch(MESSAGE.replace("USER", "SYSTEM")),
                batch(MESSAGE.replace("\"USER\"", "null")),
                batch(MESSAGE.replace("\"2026-11-06T15:30:00+09:00\"", "null")),
                batch(MESSAGE.replace("2026-11-06T15:30:00+09:00", "2026-11-06T15:30:00")),
                batch(MESSAGE.replace("2026-11-06T15:30:00+09:00", "2026-02-30T15:30:00Z")),
                batch(MESSAGE.replace("\"2026-11-06T15:30:00+09:00\"", "1793946600")));
    }

    private static String batch(String messages) {
        return "{\"messages\":[" + messages + "]}";
    }
}
