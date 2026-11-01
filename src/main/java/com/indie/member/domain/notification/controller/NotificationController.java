package com.indie.member.domain.notification.controller;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.notification.service.NotificationService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.PageSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 공개 API 9 ~ 11 — 알림 목록 · 배지 · 읽음 */
@RestController
@RequestMapping("/api/members/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final PageSize pageSize;

    public NotificationController(NotificationService notificationService, PageSize pageSize) {
        this.notificationService = notificationService;
        this.pageSize = pageSize;
    }

    @GetMapping
    public ApiResponse<CursorPage<FeedItem>> list(@CurrentUser Long userId,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(notificationService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @GetMapping("/badge")
    public ApiResponse<Map<String, Integer>> badge(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("count", notificationService.badge(userId)));
    }

    @PostMapping("/read")
    public ApiResponse<Map<String, Object>> read(@CurrentUser Long userId) {
        notificationService.markRead(userId);
        return ApiResponse.onSuccess(Map.of("ok", true));
    }
}
