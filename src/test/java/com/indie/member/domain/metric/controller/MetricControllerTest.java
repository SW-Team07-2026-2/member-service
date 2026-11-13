package com.indie.member.domain.metric.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.metric.dto.request.MetricEvent;
import com.indie.member.domain.metric.dto.response.MetricAggregate;
import com.indie.member.domain.metric.dto.response.MetricRow;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.service.MetricService;
import com.indie.member.global.auth.CurrentUserArgumentResolver;
import com.indie.member.global.common.exception.GlobalExceptionHandler;
import com.indie.member.global.common.response.CursorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 메트릭 수집(공개) · AI 조회(내부) — 입력 형식과 응답 모양. 저장 · 집계는 MetricPersistenceTest */
class MetricControllerTest {

    private static final String PATH = "/api/members/me/metrics";
    private static final String INTERNAL = "/internal/members/42/metrics";
    private static final String EVENT = """
            {"metricId":"01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e","sessionId":"01932c6e-0000-7000-8000-000000000001",
             "targetType":"PHYSICAL_ALBUM","targetId":31,"metricType":"DETAIL_VIEW","metricValue":1,
             "properties":{"from":"HOME"},"occurredAt":"2026-11-12T15:30:00+09:00"}
            """;

    private final MetricService service = mock(MetricService.class);
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MetricController(service), new InternalMetricController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test
    void collectRequiresAuthenticatedMemberAndReturnsSavedCount() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(batch(EVENT))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        when(service.collect(eq(42L), anyList())).thenReturn(1);

        mvc.perform(post(PATH).header("X-User-Id", "42").contentType(MediaType.APPLICATION_JSON).content(batch(EVENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.saved").value(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MetricEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(service).collect(eq(42L), events.capture());
        MetricEvent event = events.getValue().get(0);
        assertThat(event.metricId()).isEqualTo(UUID.fromString("01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e"));
        assertThat(event.pageViewId()).isNull();
        assertThat(event.targetType()).isEqualTo(MetricTargetType.PHYSICAL_ALBUM);
        assertThat(event.metricType()).isEqualTo(MetricType.DETAIL_VIEW);
        assertThat(event.properties()).isEqualTo(Map.of("from", "HOME"));
        assertThat(event.occurredAt()).isEqualTo(OffsetDateTime.parse("2026-11-12T15:30:00+09:00"));
    }

    /** 형식 오류가 하나라도 있으면 묶음 전체를 400 으로 거절합니다 (저장하지 않음) */
    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidBatchBeforeSaving(String body) throws Exception {
        mvc.perform(post(PATH).header("X-User-Id", "42").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{}", "{\"events\":null}", "{\"events\":[]}", batch("null"),
                batch(String.join(",", Collections.nCopies(51, EVENT))),
                batch(EVENT.replace("\"01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e\"", "null")),
                batch(EVENT.replace("01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e", "not-a-uuid")),
                batch(EVENT.replace("PHYSICAL_ALBUM", "PRODUCT")),
                batch(EVENT.replace("DETAIL_VIEW", "FOLLOW")),
                batch(EVENT.replace("\"targetId\":31", "\"targetId\":0")),
                batch(EVENT.replace("\"targetId\":31", "\"targetId\":null")),
                batch(EVENT.replace("\"metricValue\":1", "\"metricValue\":-1")),
                batch(EVENT.replace("{\"from\":\"HOME\"}", "[1,2]")),
                batch(EVENT.replace("\"2026-11-12T15:30:00+09:00\"", "null")),
                batch(EVENT.replace("2026-11-12T15:30:00+09:00", "2026-11-12T15:30:00")));
    }

    /** AI 조회 — 기본은 합계. 종류는 쉼표로 여러 개, 기간은 오프셋 포함 ISO-8601. limit 은 1000 에서 끊습니다 */
    @Test
    void internalQueryParsesFiltersAndReturnsAggregates() throws Exception {
        OffsetDateTime last = OffsetDateTime.parse("2026-11-12T15:30:00+09:00");
        when(service.aggregate(eq(42L), any(), any(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of(new MetricAggregate(MetricTargetType.PHYSICAL_ALBUM, 31, MetricType.DETAIL_VIEW, 12, 12, last)));

        mvc.perform(get(INTERNAL).param("from", "2026-10-01T00:00:00+09:00").param("to", "2026-11-12T00:00:00Z")
                        .param("targetType", "PHYSICAL_ALBUM,GOODS").param("metricType", "DETAIL_VIEW").param("limit", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].targetType").value("PHYSICAL_ALBUM"))
                .andExpect(jsonPath("$.result.items[0].targetId").value(31))
                .andExpect(jsonPath("$.result.items[0].count").value(12))
                .andExpect(jsonPath("$.result.items[0].sum").value(12))
                .andExpect(jsonPath("$.result.items[0].lastOccurredAt").exists());

        verify(service).aggregate(42L, OffsetDateTime.parse("2026-10-01T00:00:00+09:00"), OffsetDateTime.parse("2026-11-12T00:00:00Z"),
                List.of(MetricTargetType.PHYSICAL_ALBUM, MetricTargetType.GOODS), List.of(MetricType.DETAIL_VIEW), 1000);
    }

    @Test
    void internalQueryReturnsRawRowsWhenAggregateIsFalse() throws Exception {
        MetricRow row = new MetricRow(UUID.fromString("01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e"), MetricTargetType.TRACK, 880,
                MetricType.PREVIEW_PLAY, 28000, "{\"from\":\"HOME\"}", OffsetDateTime.parse("2026-11-12T15:31:10+09:00"));
        when(service.rows(eq(42L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(100)))
                .thenReturn(new CursorPage<>(List.of(row), null, false));

        mvc.perform(get(INTERNAL).param("aggregate", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].metricId").value("01932c6e-7f3a-7b8e-9c1d-2f4a5b6c7d8e"))
                .andExpect(jsonPath("$.result.items[0].metricValue").value(28000))
                .andExpect(jsonPath("$.result.items[0].properties.from").value("HOME"))   // 저장된 JSON 그대로
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void internalQueryRejectsUnknownEnumAndBadCursor() throws Exception {
        mvc.perform(get(INTERNAL).param("metricType", "FOLLOW")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(INTERNAL).param("from", "어제")).andExpect(status().isBadRequest());
        mvc.perform(get(INTERNAL).param("aggregate", "false").param("cursor", "###")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        verifyNoInteractions(service);
    }

    private static String batch(String events) {
        return "{\"events\":[" + events + "]}";
    }
}
