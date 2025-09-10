package com.mytest.oplib.job;

import com.mytest.oplib.config.SeatIngestProps;
import com.mytest.oplib.service.SeatIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 해당 스케줄러는 SeatIngestService를 시간마다 돌림
 * SeatIngestService는 OpenAPI에서 좌석현황 데이터를 가져와 DB에 저장하는 역할임
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seat.ingest", name = "enabled", havingValue = "true") // enabled가 true일 때만 빈 등록
public class SeatIngestScheduler {

    private final SeatIngestProps props;
    private final SeatIngestService service;

    // 동시에 2번 돌지 않도록 잠금
    private final ReentrantLock lock = new ReentrantLock();

    @Scheduled(cron = "${seat.ingest.cron}")
    public void run() {
        if (!lock.tryLock()) {
            log.warn("SeatIngestScheduler - 이전 작업 실행중, 이번 실행은 스킵");
            return;
        }

        try {
            log.info("SeatIngestScheduler - 시작 - numOfRows={}", props.numOfRows());

            // 타깃 없이 전체 수집
            service.ingestAll(null, null, null, props.numOfRows());
            log.info("SeatIngestScheduler - 전체 대상 수집 완료");
        } catch (Exception e) {
            log.error("SeatIngestScheduler - 작업 중 예외 발생", e);
        } finally {
            lock.unlock();
        }
    }


}
