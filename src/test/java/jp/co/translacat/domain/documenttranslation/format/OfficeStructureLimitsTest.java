package jp.co.translacat.domain.documenttranslation.format;

import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort.Rejected;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort.Rejection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 합성 Office 패키지에서 형식별 구조 한도의 경곗값과 공개 실패 사유를 확인한다. */
class OfficeStructureLimitsTest {
    private static final String OFFICE = "http://schemas.openxmlformats.org/";
    private static final String CONTENT_TYPES = OFFICE + "package/2006/content-types";
    private static final String PACKAGE_RELATIONSHIPS = OFFICE + "package/2006/relationships";
    private static final String DOCUMENT_RELATIONSHIPS = OFFICE + "officeDocument/2006/relationships";
    private static final String OFFICE_MIME = "application/vnd.openxmlformats-officedocument.";
    private static final String SHEET = OFFICE + "spreadsheetml/2006/main";
    private static final String SLIDE = OFFICE + "presentationml/2006/main";
    private static final String DRAWING = OFFICE + "drawingml/2006/main";
    private static final String WORD = OFFICE + "wordprocessingml/2006/main";
    private static final String SAMPLE_TEXT = "試験";
    private static final String OVERSIZED_SENTENCE = "試".repeat(2_001);
    private static final int ROWS_PER_CELL_LIMIT_SHEET = 6_250;
    private static final int EXTRA_CELL_ROW = ROWS_PER_CELL_LIMIT_SHEET + 1;
    private static final String EXTRA_CELL_ADDRESS = "A" + EXTRA_CELL_ROW;
    private static final String EXTRA_STYLED_ROW = "<row r=\"%s\"><c r=\"%s\" s=\"1\"/></row>"
            .formatted(EXTRA_CELL_ROW, EXTRA_CELL_ADDRESS);

    @TempDir
    private Path directory;

    private final OfficeDocumentProcessor processor = new OfficeDocumentProcessor();

    // 숨겨진 시트의 서식 전용 셀까지 통합 문서 전체의 셀 한도에 포함하는지 확인한다.
    @Test
    void countsStoredCellsAcrossVisibleAndHiddenSheets() throws IOException {
        // 준비: 시트마다 25,000셀을 작성하고, 초과본의 숨겨진 시트에 서식 전용 셀 하나를 추가한다.
        String visible = worksheet(ROWS_PER_CELL_LIMIT_SHEET, SAMPLE_TEXT, "");
        String hiddenAtLimit = worksheet(ROWS_PER_CELL_LIMIT_SHEET, "", "");
        String hiddenOverLimit = worksheet(ROWS_PER_CELL_LIMIT_SHEET, "", EXTRA_STYLED_ROW);
        Path atLimit = workbook("cells-at", 2, visible, hiddenAtLimit, true);
        Path overLimit = workbook("cells-over", 2, visible, hiddenOverLimit, true);

        assertBoundary(atLimit, overLimit, "xlsx", Rejection.DOCUMENT_SHEET_CELL_LIMIT, List.of(SAMPLE_TEXT));
    }

    // 표시 시트의 앞쪽 셀에서 긴 문장을 읽으면 뒤쪽의 전체 셀 한도보다 먼저 거부하는지 확인한다.
    @Test
    void rejectsOversizedSentenceBeforeReachingWorkbookCellLimit() throws IOException {
        // 준비: 첫 시트 D1에 2,001 코드 포인트를 넣고 두 시트에 50,001개의 셀을 저장한다.
        String first = worksheet(ROWS_PER_CELL_LIMIT_SHEET, OVERSIZED_SENTENCE, "");
        String second = worksheet(ROWS_PER_CELL_LIMIT_SHEET, "", EXTRA_STYLED_ROW);
        Path source = workbook("first-text-over", 2, first, second, false);

        // 실행: 셀 수와 문장 길이 한도를 함께 넘는 통합 문서를 검사한다.
        Rejected rejected = assertThrows(Rejected.class, () -> processor.read(source, "xlsx"));

        // 검증: 먼저 읽은 긴 문장의 정확한 오류를 반환한다.
        assertEquals(Rejection.DOCUMENT_UNIT_TEXT_LIMIT, rejected.reason());
    }

    // 셀 한도를 넘기는 표시 셀에 긴 문장이 있을 때는 셀 개수를 먼저 검사하는지 확인한다.
    @Test
    void rejectsWorkbookCellLimitBeforeReadingOversizedLastCell() throws IOException {
        // 준비: 동일한 문장을 두 번째 표시 시트의 50,001번째 저장 셀에만 배치한다.
        String first = worksheet(ROWS_PER_CELL_LIMIT_SHEET, SAMPLE_TEXT, "");
        String extraTextRow = "<row r=\"%s\">%s</row>"
                .formatted(EXTRA_CELL_ROW, inlineTextCell(EXTRA_CELL_ADDRESS, OVERSIZED_SENTENCE));
        String second = worksheet(ROWS_PER_CELL_LIMIT_SHEET, "", extraTextRow);
        Path source = workbook("last-text-over", 2, first, second, false);

        // 실행: 마지막 셀에 도달할 때 두 한도가 함께 위반되는 입력을 검사한다.
        Rejected rejected = assertThrows(Rejected.class, () -> processor.read(source, "xlsx"));

        // 검증: 셀 개수 검사에서 거부하고 같은 셀의 문장 길이 오류를 표시하지 않는다.
        assertEquals(Rejection.DOCUMENT_SHEET_CELL_LIMIT, rejected.reason());
    }

    // 텍스트가 없는 숨겨진 시트도 시트 수에 포함하는지 확인한다.
    @Test
    void countsHiddenSheetsWithoutTranslationTargets() throws IOException {
        // 준비: 첫 시트에만 원문을 넣고 나머지는 숨겨진 빈 시트로 둔다.
        String visible = worksheet(1, SAMPLE_TEXT, "");
        String hidden = worksheet(0, "", "");
        Path atLimit = workbook("sheets-at", 10, visible, hidden, true);
        Path overLimit = workbook("sheets-over", 11, visible, hidden, true);

        assertBoundary(atLimit, overLimit, "xlsx", Rejection.DOCUMENT_SHEET_LIMIT, List.of(SAMPLE_TEXT));
    }

    // 30장을 읽고 31장을 받으면 슬라이드 수 초과 사유를 반환하는지 확인한다.
    @Test
    void rejectsSlideCountOnlyAboveThirty() throws IOException {
        // 준비: 첫 슬라이드에 원문 도형을 두고 빈 슬라이드로 개수를 채운다.
        Path atLimit = presentation("slides-at", 30);
        Path overLimit = presentation("slides-over", 31);

        assertBoundary(atLimit, overLimit, "pptx", Rejection.DOCUMENT_SLIDE_LIMIT, List.of(SAMPLE_TEXT));
    }

    // 빈 문단도 문단 수에 포함하며 텍스트 구간 한도와 구분하는지 확인한다.
    @Test
    void rejectsWordParagraphCountOnlyAboveFifteenHundred() throws IOException {
        // 준비: 첫 문단만 번역 대상으로 두어 두 파일의 텍스트 분량을 같게 한다.
        String text = "<w:p><w:r><w:t>" + SAMPLE_TEXT + "</w:t></w:r></w:p>";
        Path atLimit = wordDocument("paragraphs-at", text + "<w:p/>".repeat(1_499));
        Path overLimit = wordDocument("paragraphs-over", text + "<w:p/>".repeat(1_500));

        assertBoundary(atLimit, overLimit, "docx", Rejection.DOCUMENT_WORD_PARAGRAPH_LIMIT, List.of(SAMPLE_TEXT));
    }

    // 셀 내부 문단 수가 충분히 낮을 때 표 수 한도를 독립적으로 확인한다.
    @Test
    void rejectsWordTableCountOnlyAboveOneHundred() throws IOException {
        // 준비: 각 표에 빈 문단을 하나 두고 번역 원문은 본문에 한 번만 넣는다.
        String text = "<w:p><w:r><w:t>" + SAMPLE_TEXT + "</w:t></w:r></w:p>";
        String table = """
                <w:tbl>
                  <w:tblPr/>
                  <w:tblGrid><w:gridCol w:w="1440"/></w:tblGrid>
                  <w:tr><w:tc><w:p/></w:tc></w:tr>
                </w:tbl>
                """;
        Path atLimit = wordDocument("tables-at", text + table.repeat(100));
        Path overLimit = wordDocument("tables-over", text + table.repeat(101));

        assertBoundary(atLimit, overLimit, "docx", Rejection.DOCUMENT_WORD_TABLE_LIMIT, List.of(SAMPLE_TEXT));
    }

    // 허용본의 추출 텍스트와 초과본의 정확한 형식 실패 사유를 대조한다.
    private void assertBoundary(Path atLimit, Path overLimit, String extension, Rejection reason,
            List<String> expectedText) throws IOException {
        // 실행: 작업자가 사용하는 형식 처리기로 두 입력을 검사한다.
        DocumentFormatPort.Plan plan = processor.read(atLimit, extension);
        Rejected rejected = assertThrows(Rejected.class, () -> processor.read(overLimit, extension));

        // 검증: 원문은 그대로 추출되고 초과본에서만 해당 구조 한도로 거부된다.
        List<String> actualText = plan.segments().stream()
                .flatMap(segment -> segment.pieces().stream())
                .map(DocumentFormatPort.Piece::text)
                .toList();
        assertEquals(expectedText, actualText);
        assertEquals(reason, rejected.reason());
    }

    // 첫 시트를 표시하고 나머지 시트의 표시 상태를 지정한 뒤 각 관계와 서식 파트를 연결한다.
    private Path workbook(String label, int sheetCount, String firstSheet, String followingSheet,
            boolean hideFollowingSheets) throws IOException {
        // 시트마다 고유한 파트와 관계를 만들어 통합 문서 순서로 등록한다.
        Map<String, String> parts = new LinkedHashMap<>();
        StringBuilder sheets = new StringBuilder();
        StringBuilder links = new StringBuilder();
        StringBuilder types = new StringBuilder();
        for (int number = 1; number <= sheetCount; number++) {
            String target = "worksheets/sheet" + number + ".xml";
            String part = "xl/" + target;
            String visibility = number > 1 && hideFollowingSheets ? " state=\"hidden\"" : "";
            sheets.append("<sheet name=\"Sheet%1$s\" sheetId=\"%1$s\" r:id=\"sheet%1$s\"%2$s/>"
                    .formatted(number, visibility));
            links.append(relationship("sheet" + number, "worksheet", target));
            types.append(contentType(part, "spreadsheetml.worksheet+xml"));
            parts.put(part, number == 1 ? firstSheet : followingSheet);
        }

        // 서식 전용 셀에서 참조하는 두 번째 셀 서식과 시트 목록을 통합 문서에 연결한다.
        links.append(relationship("styles", "styles", "styles.xml"));
        types.append(contentType("xl/styles.xml", "spreadsheetml.styles+xml"));
        parts.put("xl/styles.xml", """
                <styleSheet xmlns="%s">
                  <fonts count="1"><font/></fonts>
                  <fills count="2"><fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill></fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                  </cellXfs>
                </styleSheet>
                """.formatted(SHEET));
        parts.put("xl/workbook.xml", "<workbook xmlns=\"%s\" xmlns:r=\"%s\"><sheets>%s</sheets></workbook>"
                .formatted(SHEET, DOCUMENT_RELATIONSHIPS, sheets));
        parts.put("xl/_rels/workbook.xml.rels", relationships(links.toString()));
        return writePackage(label, "xlsx", "xl/workbook.xml", "spreadsheetml.sheet.main+xml", parts, types.toString());
    }

    // 행당 셀 네 개를 작성하고 선택한 첫 행 텍스트나 마지막 행 XML을 반영한다.
    private static String worksheet(int fullRows, String firstRowText, String trailingRow) {
        StringBuilder rows = new StringBuilder();
        for (int row = 1; row <= fullRows; row++) {
            String lastCell = row == 1 && !firstRowText.isEmpty()
                    ? inlineTextCell("D1", firstRowText)
                    : "<c r=\"D" + row + "\"><v>7</v></c>";
            rows.append("""
                    <row r="%1$s"><c r="A%1$s"><v>1</v></c><c r="B%1$s"><f>A%1$s*2</f><v>2</v></c>
                    <c r="C%1$s" s="1"/>%2$s</row>
                    """.formatted(row, lastCell));
        }
        rows.append(trailingRow);
        return "<worksheet xmlns=\"%s\"><sheetData>%s</sheetData></worksheet>".formatted(SHEET, rows);
    }

    // XML 이스케이프가 필요 없는 합성 텍스트를 시트의 인라인 문자열 셀에 넣는다.
    private static String inlineTextCell(String address, String text) {
        return "<c r=\"%s\" t=\"inlineStr\"><is><t>%s</t></is></c>".formatted(address, text);
    }

    // 첫 슬라이드의 도형만 번역하며 모든 슬라이드는 고유한 관계와 파트를 가진다.
    private Path presentation(String label, int count) throws IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        StringBuilder slideIds = new StringBuilder();
        StringBuilder links = new StringBuilder();
        StringBuilder types = new StringBuilder();
        for (int number = 1; number <= count; number++) {
            String target = "slides/slide" + number + ".xml";
            String part = "ppt/" + target;
            slideIds.append("<p:sldId id=\"%s\" r:id=\"slide%s\"/>".formatted(255 + number, number));
            links.append(relationship("slide" + number, "slide", target));
            types.append(contentType(part, "presentationml.slide+xml"));
            parts.put(part, slide(number == 1));
        }

        // 슬라이드 파트와 순서를 동일한 프레젠테이션 주 파트에 등록한다.
        parts.put("ppt/presentation.xml", """
                <p:presentation xmlns:p="%s" xmlns:r="%s">
                  <p:sldIdLst>%s</p:sldIdLst>
                  <p:sldSz cx="9144000" cy="6858000"/>
                  <p:notesSz cx="6858000" cy="9144000"/>
                </p:presentation>
                """.formatted(SLIDE, DOCUMENT_RELATIONSHIPS, slideIds));
        parts.put("ppt/_rels/presentation.xml.rels", relationships(links.toString()));
        return writePackage(label, "pptx", "ppt/presentation.xml", "presentationml.presentation.main+xml",
                parts, types.toString());
    }

    // 첫 슬라이드에만 기본 텍스트 도형을 만들고 나머지 트리는 비운다.
    private static String slide(boolean withText) {
        String shape = withText ? """
                <p:sp>
                  <p:nvSpPr><p:cNvPr id="2" name="Text"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                  <p:spPr/>
                  <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>%s</a:t></a:r></a:p></p:txBody>
                </p:sp>
                """.formatted(SAMPLE_TEXT) : "";
        return """
                <p:sld xmlns:p="%s" xmlns:a="%s">
                  <p:cSld><p:spTree>
                    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
                    <p:grpSpPr/>%s
                  </p:spTree></p:cSld>
                </p:sld>
                """.formatted(SLIDE, DRAWING, shape);
    }

    // Word 본문을 넣은 최소 DOCX 패키지를 저장한다.
    private Path wordDocument(String label, String body) throws IOException {
        String main = "word/document.xml";
        String document = "<w:document xmlns:w=\"%s\"><w:body>%s</w:body></w:document>".formatted(WORD, body);
        return writePackage(label, "docx", main, "wordprocessingml.document.main+xml", Map.of(main, document), "");
    }

    // 공통 OPC 메타데이터와 형식별 파트를 임시 ZIP 파일로 저장한다.
    private Path writePackage(String label, String extension, String main, String mainType, Map<String, String> parts,
            String partTypes) throws IOException {
        // 주 파트의 콘텐츠 형식과 루트 관계를 명시해 실제 패키지 검사를 통과하도록 한다.
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <Types xmlns="%s">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  %s%s
                </Types>
                """.formatted(CONTENT_TYPES, contentType(main, mainType), partTypes));
        entries.put("_rels/.rels", relationships(relationship("main", "officeDocument", main)));
        entries.putAll(parts);

        // 테스트마다 다른 임시 경로에 UTF-8 XML을 기록한다.
        Path path = directory.resolve(label + "." + extension);
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    private static String contentType(String part, String type) {
        return "<Override PartName=\"/%s\" ContentType=\"%s%s\"/>".formatted(part, OFFICE_MIME, type);
    }

    private static String relationships(String links) {
        return "<Relationships xmlns=\"%s\">%s</Relationships>".formatted(PACKAGE_RELATIONSHIPS, links);
    }

    private static String relationship(String id, String type, String target) {
        return "<Relationship Id=\"%s\" Type=\"%s/%s\" Target=\"%s\"/>"
                .formatted(id, DOCUMENT_RELATIONSHIPS, type, target);
    }
}
