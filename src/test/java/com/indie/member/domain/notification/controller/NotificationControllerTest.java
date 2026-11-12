package com.indie.member.domain.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.entity.NotificationTarget;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.service.NotificationService;
import com.indie.member.global.auth.CurrentUserArgumentResolver;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.exception.GlobalExceptionHandler;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.PageSize;
import com.indie.member.global.config.MemberProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림 API 의 응답 모양 — 프론트가 target · targetId 로 이동하고 isRead 로 표시합니다 */
class NotificationControllerTest {

    private static final String PATH = "/api/members/notifications";

    private final NotificationService service = mock(NotificationService.class);
    /** 운영(Spring Boot 기본값)과 같게 — 시각은 숫자가 아니라 ISO-8601 문자열 */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        PageSize pageSize = new PageSize(new MemberProperties(null, null, new MemberProperties.Paging(20, 50), null, null));
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service, pageSize))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test
    void listItemCarriesTargetAndReadFlag() throws Exception {
        NotificationItem news = new NotificationItem(101, NotificationType.PHYSICAL_ALBUM_RELEASED, NotificationTarget.PHYSICAL_ALBUM,
                31, 7L, "새소년 「비적응」 LP 컬러반 판매 시작 · 45,000원", false, OffsetDateTime.parse("2026-11-12T15:30:00+09:00"));
        NotificationItem personal = new NotificationItem(99, NotificationType.BOOKING_DONE, NotificationTarget.BOOKING,
                5521, null, "새소년 단독공연 예매 완료", true, OffsetDateTime.parse("2026-11-11T20:01:00+09:00"));
        when(service.list(42L, null, 20)).thenReturn(new CursorPage<>(List.of(news, personal), "MTAw", true));

        mvc.perform(get(PATH).header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].id").value(101))
                .andExpect(jsonPath("$.result.items[0].type").value("PHYSICAL_ALBUM_RELEASED"))
                .andExpect(jsonPath("$.result.items[0].target").value("PHYSICAL_ALBUM"))
                .andExpect(jsonPath("$.result.items[0].targetId").value(31))
                .andExpect(jsonPath("$.result.items[0].artistId").value(7))
                .andExpect(jsonPath("$.result.items[0].isRead").value(false))
                .andExpect(jsonPath("$.result.items[0].read").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].createdAt").value("2026-11-12T15:30:00+09:00"))
                .andExpect(jsonPath("$.result.items[1].artistId").value((Object) null))
                .andExpect(jsonPath("$.result.items[1].isRead").value(true))
                .andExpect(jsonPath("$.result.nextCursor").value("MTAw"))
                .andExpect(jsonPath("$.result.hasNext").value(true));
        verify(service).list(42L, null, 20);
    }

    @Test
    void readOneIsOwnedByCurrentMemberAndUnknownIdIs404() throws Exception {
        mvc.perform(post(PATH + "/101/read")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);

        mvc.perform(post(PATH + "/101/read").header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.notificationId").value(101))
                .andExpect(jsonPath("$.result.isRead").value(true));
        verify(service).markRead(42L, 101L);

        doThrow(new GeneralException(CommonErrorCode.NOT_FOUND)).when(service).markRead(42L, 999L);
        mvc.perform(post(PATH + "/999/read").header("X-User-Id", "42"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void readAllAndBadge() throws Exception {
        when(service.badge(42L)).thenReturn(3);

        mvc.perform(post(PATH + "/read").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result").doesNotExist());
        verify(service).markAllRead(42L);
        mvc.perform(get(PATH + "/badge").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.count").value(3));
    }
}
