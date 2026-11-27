package com.indie.member.domain.notification.controller;

import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.service.NotificationService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 프론트 -> Gateway -> Member 알림 목록, 미확인 수와 읽음 처리 API. */
@RestController
@Validated
@RequestMapping("/api/members/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final PageSize pageSize;

    public NotificationController(NotificationService notificationService, PageSize pageSize) {
        this.notificationService = notificationService;
        this.pageSize = pageSize;
    }

    /**
     * 알림 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     * @param size 페이지당 조회 개수
     */
    @GetMapping
    public ApiResponse<CursorPage<NotificationItem>> list(@CurrentUser Long userId,
                                                          @RequestParam(required = false) String cursor,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(notificationService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /**
     * 알림 미확인 개수를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/badge")
    public ApiResponse<Map<String, Integer>> badge(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("count", notificationService.badge(userId)));
    }

    /**
     * 지정한 알림을 읽음 처리합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param notificationId 알림 ID
     */
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Map<String, Object>> read(@CurrentUser Long userId, @PathVariable @Positive long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.onSuccess(Map.of("notificationId", notificationId, "isRead", true));
    }

    /**
     * 회원의 모든 알림을 읽음 처리합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @PostMapping("/read")
    public ApiResponse<Void> readAll(@CurrentUser Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.onSuccess(null);
    }
}
