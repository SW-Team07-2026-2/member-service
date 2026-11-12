package com.indie.member.domain.notification.scheduler;

import com.indie.member.domain.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 알림 보관 3개월 — 하루 1번 지난 알림을 지웁니다.
 * 한 번에 다 지우지 않고 BATCH 건씩 끊어 커밋합니다 (긴 트랜잭션 · 큰 락을 피합니다).
 * replica 2 가 같이 돌아도 기간 조건 삭제라 결과가 같습니다 — 락을 두지 않습니다.
 */
@Component
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);
    static final int RETENTION_MONTHS = 3;
    private static final int BATCH = 1000;
    private static final int MAX_ROUNDS = 200;             // 한 번 실행에 최대 20만 건 — 남으면 다음 날 이어서

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public NotificationRetentionJob(NotificationRepository notificationRepository, TransactionTemplate transaction, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void purge() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMonths(RETENTION_MONTHS);
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Integer deleted = transaction.execute(status -> notificationRepository.deleteCreatedBefore(cutoff, BATCH));
            if (deleted == null || deleted == 0) break;
            total += deleted;
        }
        log.info("notification_purged count={} cutoff={}", total, cutoff);
    }
}
