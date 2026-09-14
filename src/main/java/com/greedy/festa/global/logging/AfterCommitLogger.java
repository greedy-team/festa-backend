package com.greedy.festa.global.logging;

import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 수행된 작업을 트랜잭션이 실제로 커밋된 뒤에 남긴다.
 *
 * <p>서비스 메서드 안에서 바로 찍으면 아직 커밋 전이라, 뒤이어 롤백된 작업이
 * "수행됨"으로 기록된다. 추적용 로그가 하지 않은 일을 했다고 말하는 셈이라
 * 되짚기의 근거가 못 된다.
 *
 * <p>로거를 넘겨받는 이유는 로그의 출처를 작업한 클래스로 유지하기 위해서다.
 * 여기서 자체 로거로 찍으면 모든 관리 작업이 이 클래스 이름으로 뭉쳐 나온다.
 *
 * <p>트랜잭션 밖에서 부르면 그대로 즉시 남긴다 — 커밋을 기다릴 대상이 없는데
 * 조용히 버리면 로그가 통째로 사라진다.
 *
 * <p>콜백은 커밋한 스레드에서 그대로 이어 돌므로 MDC(요청 번호·관리자)가 유지된다.
 */
public final class AfterCommitLogger {

    private AfterCommitLogger() {
    }

    public static void info(Logger log, String format, Object... arguments) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info(format, arguments);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(format, arguments);
            }
        });
    }
}
