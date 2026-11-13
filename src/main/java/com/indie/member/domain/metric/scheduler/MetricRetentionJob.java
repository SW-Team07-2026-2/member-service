package com.indie.member.domain.metric.scheduler;

import com.indie.member.domain.metric.repository.MetricRepository;
import com.indie.member.domain.metric.service.MetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 행동 메트릭 보관 6개월 — 하루 1번 지난 행을 지웁니다.
 * BATCH 건씩 끊어 커밋합니다. replica 2 가 같이 돌아도 기간 조건 삭제라 결과가 같습니다 — 락을 두지 않습니다.
 */
@Component
public class MetricRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MetricRetentionJob.class);
    private static final int BATCH = 1000;
    private static final int MAX_ROUNDS = 500;             // 한 번 실행에 최대 50만 건 — 남으면 다음 날 이어서

    private final MetricRepository metricRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public MetricRetentionJob(MetricRepository metricRepository, TransactionTemplate transaction, Clock clock) {
        this.metricRepository = metricRepository;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Scheduled(cron = "0 40 4 * * *")
    public void purge() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMonths(MetricService.RETENTION_MONTHS);
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            Integer deleted = transaction.execute(status -> metricRepository.deleteOccurredBefore(cutoff, BATCH));
            if (deleted == null || deleted == 0) break;
            total += deleted;
        }
        log.info("metric_purged count={} cutoff={}", total, cutoff);
    }
}
