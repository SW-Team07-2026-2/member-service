package com.indie.member.domain.activity.repository;

import com.indie.member.domain.activity.entity.NewActivity;
import org.springframework.stereotype.Repository;

/** ActivityRepository 구현 — Spring Data 네이티브 INSERT ON CONFLICT (event_id) DO NOTHING */
@Repository
public class ActivityRepositoryAdapter implements ActivityRepository {

    private final ActivityJpaRepository jpa;

    public ActivityRepositoryAdapter(ActivityJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean appendIfAbsent(NewActivity a) {
        return jpa.insertIgnore(a.eventId(), a.artistId(), a.targetUserId(), a.type().name(), a.refId(),
                a.title(), a.thumbnailUrl(), a.occurredAt()) == 1;
    }
}
