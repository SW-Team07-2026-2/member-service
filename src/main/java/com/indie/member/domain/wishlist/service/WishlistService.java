package com.indie.member.domain.wishlist.service;

import com.indie.member.global.event.EventPublisher;
import com.indie.member.global.event.MemberActivityEvent;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.domain.wishlist.repository.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public WishlistService(WishlistRepository wishlistRepository, EventPublisher eventPublisher, Clock clock) {
        this.wishlistRepository = wishlistRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void add(long userId, long productId) {
        if (wishlistRepository.add(userId, productId)) {
            eventPublisher.publish(MemberActivityEvent.TOPIC,
                    MemberActivityEvent.payload(userId, "WISHLIST_ADD", productId, OffsetDateTime.now(clock)));
        }
    }

    @Transactional
    public void remove(long userId, long productId) {
        if (wishlistRepository.remove(userId, productId)) {
            eventPublisher.publish(MemberActivityEvent.TOPIC,
                    MemberActivityEvent.payload(userId, "WISHLIST_REMOVE", productId, OffsetDateTime.now(clock)));
        }
    }

    /** 커서 (created_at, product_id) */
    @Transactional(readOnly = true)
    public CursorPage<WishlistItem> list(long userId, Cursor cursor, int size) {
        return CursorPage.of(wishlistRepository.findPage(userId, cursor, size + 1), size,
                r -> new Cursor(r.createdAt(), r.productId()));
    }
}
