package com.indie.member.domain.feed.controller;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.feed.service.FeedService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.PageSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 API 8 — GET /api/members/feed?cursor=&size= */
@RestController
public class FeedController {

    private final FeedService feedService;
    private final PageSize pageSize;

    public FeedController(FeedService feedService, PageSize pageSize) {
        this.feedService = feedService;
        this.pageSize = pageSize;
    }

    @GetMapping("/api/members/feed")
    public ApiResponse<CursorPage<FeedItem>> feed(@CurrentUser Long userId,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(feedService.feed(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }
}
