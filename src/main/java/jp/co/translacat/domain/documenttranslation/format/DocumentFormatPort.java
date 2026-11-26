package jp.co.translacat.domain.documenttranslation.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Office 번역 텍스트와 형식을 읽고 사용자에게 반환할 파일을 만든다. */
public interface DocumentFormatPort {
    /** 파일을 다시 준비할 때 사용자에게 설명할 수 있는 접수 거부 유형이다. */
    enum Rejection {
        DOCUMENT_LIMIT_EXCEEDED,
        DOCUMENT_TEXT_LIMIT,
        DOCUMENT_SEGMENT_LIMIT,
        DOCUMENT_UNIT_TEXT_LIMIT,
        DOCUMENT_ZIP_ENTRY_LIMIT,
        DOCUMENT_EXPANDED_SIZE_LIMIT,
        DOCUMENT_XML_SIZE_LIMIT,
        DOCUMENT_XML_TEXT_LIMIT,
        DOCUMENT_WORD_PARAGRAPH_LIMIT,
        DOCUMENT_WORD_TABLE_LIMIT,
        DOCUMENT_SLIDE_LIMIT,
        DOCUMENT_SHEET_LIMIT,
        DOCUMENT_SHEET_CELL_LIMIT,
        UNSUPPORTED_PACKAGE
    }

    /** 손상 외의 이유로 형식 처리기가 입력을 거부했음을 보고한다. */
    final class Rejected extends IOException {
        private final Rejection reason;

        // 외부 입력 대신 처리기에서 정의한 사유를 전달한다.
        Rejected(Rejection reason, String message) {
            super(message);
            this.reason = reason;
        }

        /** 요청자가 다음 동작을 결정할 수 있는 접수 실패 유형을 반환한다. */
        public Rejection reason() {
            return reason;
        }
    }

    /**
     * 전달할 텍스트 조각과 검사 경고를 읽는다.
     *
     * @param source 작업자가 소유한 변경 불가 원본 사본
     * @param extension 점을 제외한 Office 확장자
     * @return 원본과 번역 조각을 연결한 처리 계획
     * @throws IOException 읽기 또는 문서 검증이 실패한 경우
     */
    Plan read(Path source, String extension) throws IOException;

    /**
     * 승인된 번역 조각을 처리 계획에 적용하고 결과 패키지를 검사한다.
     *
     * @param plan 원본을 읽어 만든 처리 계획
     * @param translatedPieces 모든 조각 ID에 대한 번역 문자열
     * @param output 작업자가 소유한 결과 경로
     * @return 출력 검증과 레이아웃 관련 경고
     * @throws IOException 입력, 검증 또는 쓰기 작업이 실패한 경우
     */
    List<Warning> write(Plan plan, Map<String, String> translatedPieces, Path output) throws IOException;

    /** 작업자가 원본 순서의 번역 요청을 만들 수 있는 읽기 전용 계획이다. */
    interface Plan {
        /** 조각의 원래 문맥을 유지한 번역 단위를 반환한다. */
        List<Segment> segments();

        /** 원본을 검사하는 동안 확인한 경고를 반환한다. */
        List<Warning> warnings();
    }

    /**
     * 문단 또는 셀의 문맥을 공유하는 순서 있는 번역 단위다.
     *
     * @param id 계획 내 세그먼트 ID
     * @param pieces 원본 순서의 텍스트 조각
     */
    record Segment(String id, List<Piece> pieces) {
        public Segment {
            pieces = List.copyOf(pieces);
        }
    }

    /**
     * 출력에서 같은 위치로 돌아갈 문자열 조각이다.
     *
     * @param id 계획 전체에서 고유한 조각 ID
     * @param text 번역 대상 문자열
     */
    record Piece(String id, String text) {
    }

    /**
     * 사용자가 결과를 검수할 때 확인할 비식별 위치를 설명한다.
     *
     * @param code 화면에 전달하는 고정 경고 코드
     * @param location 사용자 입력 내용이 없는 문서 내부 위치
     */
    record Warning(String code, String location) {
    }
}
