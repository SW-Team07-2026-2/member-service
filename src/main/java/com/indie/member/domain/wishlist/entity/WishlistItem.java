package com.indie.member.domain.wishlist.entity;

import java.time.OffsetDateTime;

public record WishlistItem(long productId, OffsetDateTime createdAt) {}
