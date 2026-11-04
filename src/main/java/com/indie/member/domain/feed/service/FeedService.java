package com.indie.member.domain.feed.service;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.feed.repository.FeedRepository;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.response.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 피드 = 팔로우한 아티스트의 activity (§11-1) — 쿼리는 FeedRepository 구현에 */
@Service
public class FeedService {

    private final FeedRepository feedRepository;

    public FeedService(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<FeedItem> feed(long userId, Cursor cursor, int size) {
        return CursorPage.of(feedRepository.findFeed(userId, cursor, size + 1), size, r -> new Cursor(r.occurredAt(), r.id()));
    }
}
