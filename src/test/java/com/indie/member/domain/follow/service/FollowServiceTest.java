package com.indie.member.domain.follow.service;

import com.indie.member.domain.follow.entity.ArtistFollowerCount;
import com.indie.member.domain.follow.repository.FollowRepository;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.event.EventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 아티스트 팔로워 수 내부 API - 요청 순서, 중복 제거, 팔로워가 없으면 0, 1~500 개 */
class FollowServiceTest {

    private final FollowRepository repository = mock(FollowRepository.class);
    private final FollowService service = new FollowService(repository, mock(EventPublisher.class), Clock.systemUTC());

    @Test
    void followerCountsKeepRequestOrderAndFillZero() {
        when(repository.countFollowers(List.of(3L, 1L, 2L))).thenReturn(Map.of(1L, 5L, 3L, 2L));

        assertThat(service.followerCounts(List.of(3L, 1L, 3L, 2L))).containsExactly(
                new ArtistFollowerCount(3, 2), new ArtistFollowerCount(1, 5), new ArtistFollowerCount(2, 0));
    }

    @Test
    void followerCountsRejectEmptyOrTooMany() {
        when(repository.countFollowers(anyCollection())).thenReturn(Map.of());
        List<Long> max = LongStream.rangeClosed(1, FollowService.MAX_FOLLOWER_COUNT_IDS).boxed().toList();
        List<Long> over = LongStream.rangeClosed(1, FollowService.MAX_FOLLOWER_COUNT_IDS + 1).boxed().toList();

        assertThat(service.followerCounts(max)).hasSize(FollowService.MAX_FOLLOWER_COUNT_IDS);
        assertThatThrownBy(() -> service.followerCounts(List.of())).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.followerCounts(over)).isInstanceOf(GeneralException.class);
    }
}
