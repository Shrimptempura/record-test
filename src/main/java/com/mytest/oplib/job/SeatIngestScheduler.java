package com.mytest.oplib.job;

import com.mytest.oplib.config.SeatIngestProps;
import com.mytest.oplib.service.SeatIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seat.ingest", name = "enabled", havingValue = "true") // enabled가 true일 때만 빈 등록
public class SeatIngestScheduler {

    private final SeatIngestProps props;
    private final SeatIngestService service;

    // 동시에 2번 돌지 않도록 잠금
    private final ReentrantLock lock = new ReentrantLock();

    @Scheduled(cron = "#{@seatIngestProps.cron}")
    public void run() {
        if (!lock.tryLock()) {
            log.warn("SeatIngestScheduler - 이전 작업 실행중, 이번 실행은 스킵");
            return;
        }

        try {
            log.info("SeatIngestScheduler - 시작: targets={}, numOfRows={}", props.targets(), props.numOfRows());

            for (String target : props.targets()) {
                // 포맷: pblibId:stdgCd:rdrmid (정리중)
                String[] arr = target.split(":");

                if (arr.length != 3) {
                    log.error("SeatIngestScheduler - 잘못된 타겟 포맷: {}", target);
                    continue;
                }

                String pblibId = arr[0];
                String stdgCd = arr[1];
                String rdrmid = arr[2];

                // 여기서 서비스 호출 (구현은 나중에)
                service.ingestOneTarget(pblibId, stdgCd, rdrmid, props.numOfRows());
                log.info("SeatIngestScheduler - 완료: pblibId: {}, stdgCd: {}, rdrmId: {}", pblibId, stdgCd, rdrmid);
            }
            log.info("SeatIngestScheduler - 전체 작업 완료");
        } catch (Exception e) {
            log.error("SeatIngestScheduler - 작업 중 오류 발생", e);
        } finally {
            lock.unlock();
        }
    }


}
