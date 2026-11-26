package jp.co.translacat.domain.documenttranslation.translation;

import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 문서 작업이 모델 실행을 요청하는 경계다. 파일 위치와 저장소에는 접근하지 않는다. */
public interface DocumentTranslationGateway {
    /**
     * 작업의 한 배치를 번역하고 조각 ID로 조회할 수 있는 검증된 결과를 돌려준다.
     *
     * @param jobId 문서 작업 ID
     * @param batchNo 작업 내 배치 번호
     * @param sourceLanguage 원본 언어 또는 자동 감지 값
     * @param targetLanguage 대상 언어
     * @param segments 각 조각에 고유 ID가 있는 번역 대상
     * @param deadline 호출자가 정한 작업 마감 시각
     * @return 조각별 번역과 실행 모델
     * @throws IOException 모델 호출 또는 응답 검증 실패
     * @throws InterruptedException 호출 스레드 중단
     */
    Batch translate(
            UUID jobId,
            int batchNo,
            String sourceLanguage,
            String targetLanguage,
            List<DocumentFormatPort.Segment> segments,
            Instant deadline) throws IOException, InterruptedException;

    /** 검증이 끝난 조각별 번역과 AI가 보고한 모델이다. */
    record Batch(Map<String, String> translatedPieces, String model) {
        public Batch {
            translatedPieces = Map.copyOf(translatedPieces);
        }
    }

    /**
     * 유효한 번역은 메모리에 보관하고 검사를 통과하지 못한 세그먼트만 다시 요청한다.
     *
     * @param accepted 구조와 내용 검사를 통과한 조각 및 응답 모델
     * @param rejected 같은 배치에서 다시 요청할 원문 세그먼트
     */
    record RepairPlan(Batch accepted, List<DocumentFormatPort.Segment> rejected) {
        public RepairPlan {
            rejected = List.copyOf(rejected);
        }
    }

    /** 워커의 재호출 및 공개 오류 분류에 쓰는 기술 오류다. */
    enum Reason {
        TIMEOUT,
        TRANSIENT,
        INVALID_RESPONSE,
        CONFIGURATION
    }

    /** 고정 오류와, 구조 검증을 마친 경우에만 반환할 수 있는 재시도 계획이다. */
    final class Failure extends IOException {
        private final Reason reason;
        private final Duration retryAfter;
        private final transient RepairPlan repairPlan;

        /**
         * 제한된 실패 분류와 선택적인 재시도 대기 시간을 기록한다.
         *
         * @param reason 모델 실행 실패 분류
         * @param retryAfter 서버가 제안한 대기 시간 또는 0
         */
        public Failure(Reason reason, Duration retryAfter) {
            this(reason, retryAfter, null);
        }

        // 구조가 올바른 응답에서 검사를 통과한 조각과 재시도 대상만 메모리에 보관한다.
        Failure(Reason reason, Duration retryAfter, RepairPlan repairPlan) {
            super(reason.name());
            this.reason = reason;
            this.retryAfter = retryAfter;
            this.repairPlan = repairPlan;
        }

        /** 검증된 부분 결과가 있으면 재시도 계획을 반환한다. */
        public RepairPlan repairPlan() {
            return repairPlan;
        }

        public Reason reason() {
            return reason;
        }

        public Duration retryAfter() {
            return retryAfter;
        }

        /** 같은 문서 배치를 다시 호출할 수 있는 기술 실패인지를 반환한다. */
        public boolean retryable() {
            return reason != Reason.CONFIGURATION;
        }
    }
}
