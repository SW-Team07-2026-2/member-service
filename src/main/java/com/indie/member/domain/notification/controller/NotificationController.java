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

/** 공개 API — 알림 목록 · 배지 · 단건 읽음 · 전체 읽음. 개인 알림과 팔로잉 소식이 한 목록입니다 */
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

    @GetMapping
    public ApiResponse<CursorPage<NotificationItem>> list(@CurrentUser Long userId,
                                                          @RequestParam(required = false) String cursor,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(notificationService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @GetMapping("/badge")
    public ApiResponse<Map<String, Integer>> badge(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("count", notificationService.badge(userId)));
    }

    /** 단건 읽음 — 알림을 눌렀을 때. 멱등 (이미 읽은 알림도 200) */
    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> read(@CurrentUser Long userId, @PathVariable @Positive long id) {
        notificationService.markRead(userId, id);
        return ApiResponse.onSuccess(Map.of("notificationId", id, "isRead", true));
    }

    /** 전체 읽음 */
    @PostMapping("/read")
    public ApiResponse<Void> readAll(@CurrentUser Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.onSuccess(null);
    }
}
