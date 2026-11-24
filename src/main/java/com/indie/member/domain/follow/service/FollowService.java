package com.indie.member.domain.follow.service;

import com.indie.member.domain.follow.entity.ArtistFollowerCount;
import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.entity.FollowSummary;
import com.indie.member.domain.follow.repository.FollowRepository;
import com.indie.member.global.event.EventPublisher;
import com.indie.member.global.event.MemberActivityEvent;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FollowService {

    /** 내부 API 한 번에 넘기는 팔로우 수 상한 */
    static final int MAX_SUMMARIES = 1000;

    /** 팔로워 수 내부 API 한 번에 받는 아티스트 수 상한 */
    public static final int MAX_FOLLOWER_COUNT_IDS = 500;

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

    /** 내부 API — 팔로우 전부 (11/19 팔로우 탭 정렬용 · 상한 MAX_SUMMARIES) */
    @Transactional(readOnly = true)
    public List<FollowSummary> summaries(long userId) {
        return followRepository.findSummaries(userId, MAX_SUMMARIES);
    }

    /** 내부 API — 요청한 아티스트마다 지금 팔로워 수 (요청 순서 · 중복 제거 · 팔로워가 없으면 0). 1~MAX_FOLLOWER_COUNT_IDS 개 */
    @Transactional(readOnly = true)
    public List<ArtistFollowerCount> followerCounts(List<Long> artistIds) {
        List<Long> ids = artistIds.stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_FOLLOWER_COUNT_IDS) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
        }
        Map<Long, Long> counts = followRepository.countFollowers(ids);
        return ids.stream().map(id -> new ArtistFollowerCount(id, counts.getOrDefault(id, 0L))).toList();
    }
}
