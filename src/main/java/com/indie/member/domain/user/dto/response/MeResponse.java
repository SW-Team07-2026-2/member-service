package com.indie.member.domain.user.dto.response;

import com.indie.member.domain.user.entity.UserEntity;

public record MeResponse(Long userId, String nickname, String profileImageUrl, String email, String provider,
                         long followCount, long wishlistCount, long collectionCount) {
    public static MeResponse of(UserEntity u, long follows, long wishes, long collections) {
        return new MeResponse(u.getId(), u.getNickname(), u.getProfileImageUrl(), u.getEmail(),
                u.getProvider().name(), follows, wishes, collections);
    }
}
