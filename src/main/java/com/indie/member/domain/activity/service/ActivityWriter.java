package com.indie.member.domain.activity.service;

import com.indie.member.domain.activity.repository.ActivityRepository;
import com.indie.member.domain.activity.entity.NewActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * activity 를 쓰는 유일한 경로 — Kafka 컨슈머만 씁니다.
 * ★ 멱등 소비는 event_id UNIQUE 로 (ActivityRepository.appendIfAbsent). 중복은 정상 경로입니다.
 */
@Service
public class ActivityWriter {

    private static final Logger log = LoggerFactory.getLogger(ActivityWriter.class);

    private final ActivityRepository activityRepository;

    public ActivityWriter(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void write(NewActivity activity) {
        if (!activityRepository.appendIfAbsent(activity)) {
            log.info("duplicate_event ignored eventId={}", activity.eventId());   // ★ 정상 경로
        }
    }
}
