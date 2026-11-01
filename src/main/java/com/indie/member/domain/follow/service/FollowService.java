package com.indie.member.domain.follow.service;

import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.repository.FollowRepository;
import com.indie.member.global.event.EventPublisher;
import com.indie.member.global.event.MemberActivityEvent;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.response.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public FollowService(FollowRepository followRepository, EventPublisher eventPublisher, Clock clock) {
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** ★ 팔로우와 이벤트를 같은 트랜잭션에 — 실제로 새로 팔로우한 경우에만 발행 */
    @Transactional
    public void follow(long userId, long artistId) {
        if (followRepository.add(userId, artistId)) {
            eventPublisher.publish(MemberActivityEvent.TOPIC,
                    MemberActivityEvent.payload(userId, "FOLLOW", artistId, OffsetDateTime.now(clock)));
        }
    }

    @Transactional
    public void unfollow(long userId, long artistId) {
        if (followRepository.remove(userId, artistId)) {
            eventPublisher.publish(MemberActivityEvent.TOPIC,
                    MemberActivityEvent.payload(userId, "UNFOLLOW", artistId, OffsetDateTime.now(clock)));
        }
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(long userId, long artistId) {
        return followRepository.exists(userId, artistId);
    }

    /** 커서 (followed_at, artist_id). 아티스트 이름은 catalog 스키마라 조인할 수 없어 ID 만 돌려줍니다 */
    @Transactional(readOnly = true)
    public CursorPage<FollowItem> list(long userId, Cursor cursor, int size) {
        return CursorPage.of(followRepository.findPage(userId, cursor, size + 1), size,
                r -> new Cursor(r.followedAt(), r.artistId()));
    }
}
