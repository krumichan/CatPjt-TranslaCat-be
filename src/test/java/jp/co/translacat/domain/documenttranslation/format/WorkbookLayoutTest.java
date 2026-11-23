package jp.co.translacat.domain.documenttranslation.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbookLayoutTest {
    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String WORKBOOK = "xl/workbook.xml";
    private static final String WORKSHEET = "xl/worksheets/sheet1.xml";
    private static final String STYLES = "xl/styles.xml";
    private static final String TRANSLATED = "A longer translated service message";

    @TempDir
    Path directory;

    @Test
    void wrapsChangedStringRepresentationsWithOneAppendedStyle() throws Exception {
        // 준비: 같은 기본 스타일을 쓰는 세 가지 문자열 표현과 폭이 넓은 공유 참조를 배치한다.
        Path source = workbook();
        Map<String, String> translations = Map.of("Inline", TRANSLATED, "Plain", TRANSLATED, "Shared", TRANSLATED);

        // 실행: 기록된 좁은 열 너비를 유지한 채 번역 셀만 처리한다.
        Path output = translate(source, translations, new OfficeTextPlan());

        // 검증: 복제 스타일 하나만 덧붙이고 원본 스타일, 수식과 인쇄 레이아웃은 보존한다.
        try (var original = new OfficeParts(source); var result = new OfficeParts(output)) {
            Element before = original.xml(WORKSHEET).getDocumentElement();
            Element after = result.xml(WORKSHEET).getDocumentElement();
            for (String address : List.of("A1", "B1", "C1")) {
                assertEquals("2", cell(after, address).getAttribute("s"));
            }
            assertFalse(cell(after, "D1").hasAttribute("s"));
            assertTrue(cell(before, "E1").isEqualNode(cell(after, "E1")));
            assertEquals("1", child(cell(after, "C1"), "v").getTextContent());
            assertEquals("1", child(cell(after, "D1"), "v").getTextContent());
            assertTrue(child(before, "cols").isEqualNode(child(after, "cols")));
            assertTrue(child(before, "pageSetup").isEqualNode(child(after, "pageSetup")));
            assertFalse(((Element) cell(after, "A1").getParentNode()).hasAttribute("ht"));

            Element table = child(result.xml(STYLES).getDocumentElement(), "cellXfs");
            List<Element> styles = OfficeParts.children(table, SHEET, "xf");
            List<Element> originals = OfficeParts.children(child(original.xml(STYLES).getDocumentElement(), "cellXfs"));
            assertEquals("3", table.getAttribute("count"));
            assertEquals(3, styles.size());
            assertTrue(originals.get(0).isEqualNode(styles.get(0)));
            assertTrue(originals.get(1).isEqualNode(styles.get(1)));

            Element clone = (Element) styles.get(2).cloneNode(true);
            Element alignment = child(clone, "alignment");
            assertEquals("1", alignment.getAttribute("wrapText"));
            assertEquals("1", clone.getAttribute("applyAlignment"));
            clone.removeChild(alignment);
            clone.removeAttribute("applyAlignment");
            assertTrue(originals.getFirst().isEqualNode(clone));
        }
    }

    @Test
    void preservesManualHeightMergedRowsAndShrinkToFitStylesWithWarnings() throws Exception {
        // 준비: 고정 높이, 병합 및 축소 맞춤이 있는 셀에 각각 긴 번역문을 준다.
        Path source = workbook();
        var plan = new OfficeTextPlan();
        Map<String, String> translations = Map.of("Manual", TRANSLATED, "Merged", TRANSLATED, "Shrink", TRANSLATED);

        // 실행: 텍스트를 교체하지만 원래 배치 속성은 건드리지 않는다.
        Path output = translate(source, translations, plan);

        // 검증: 각 위치는 검수 경고에 남고 기존 스타일 파트에는 복제본이 없다.
        try (var original = new OfficeParts(source); var result = new OfficeParts(output)) {
            Element sheet = result.xml(WORKSHEET).getDocumentElement();
            assertEquals("21", ((Element) cell(sheet, "A2").getParentNode()).getAttribute("ht"));
            assertEquals("A3:B3", child(child(sheet, "mergeCells"), "mergeCell").getAttribute("ref"));
            assertFalse(cell(sheet, "A2").hasAttribute("s"));
            assertFalse(cell(sheet, "A3").hasAttribute("s"));
            assertEquals("1", cell(sheet, "A4").getAttribute("s"));
            for (String address : List.of("A2", "A3", "A4")) {
                assertEquals(TRANSLATED, child(cell(sheet, address), "v").getTextContent());
                var warning = new DocumentFormatPort.Warning("LAYOUT_REVIEW", "sheet:1/" + address);
                assertTrue(plan.warnings().contains(warning));
            }
            assertTrue(original.xml(STYLES).isEqualNode(result.xml(STYLES)));
        }
    }

    // 실제 워크북 텍스트 경로에 합성 번역을 적용하고 XML 파트를 검증 가능한 사본으로 작성한다.
    private Path translate(Path source, Map<String, String> translations, OfficeTextPlan plan) throws IOException {
        Path output = directory.resolve("output.xlsx");
        try (var parts = new OfficeParts(source)) {
            // 번역하지 않는 조각에도 원문을 전달해 실제 처리기와 같은 입력 계약을 지킨다.
            parts.validateRelationships();
            WorkbookText.collect(parts, plan, WORKBOOK);
            Map<String, String> responses = new HashMap<>();
            for (var segment : plan.segments()) {
                for (var piece : segment.pieces()) {
                    responses.put(piece.id(), translations.getOrDefault(piece.text(), piece.text()));
                }
            }

            plan.apply(responses);
            parts.write(output);
        }
        return output;
    }

    // 좁은 열, 여러 문자열 저장 유형과 기존 레이아웃 제약을 작은 XLSX 파트로 구성한다.
    private Path workbook() throws IOException {
        String workbook = """
                <workbook xmlns="%s" xmlns:r="%s">
                  <sheets><sheet name="Sheet1" sheetId="1" r:id="sheet"/></sheets>
                </workbook>
                """.formatted(SHEET, OfficeParts.OFFICE_REL_ATTRIBUTE);
        String relationships = """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="sheet" Type="%1$sworksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="styles" Type="%1$sstyles" Target="styles.xml"/>
                  <Relationship Id="strings" Type="%1$ssharedStrings" Target="sharedStrings.xml"/>
                </Relationships>
                """.formatted(OfficeParts.OFFICE_RELS);
        String worksheet = """
                <worksheet xmlns="%s">
                  <sheetFormatPr defaultRowHeight="15"/>
                  <cols><col min="1" max="3" width="14" customWidth="1"/><col min="4" max="5" width="60"/></cols>
                  <sheetData>
                    <row r="1"><c r="A1" t="inlineStr"><is><t>Inline</t></is></c>
                      <c r="B1" t="str" s="0"><v>Plain</v></c>
                      <c r="C1" t="s"><v>0</v></c><c r="D1" t="s"><v>0</v></c><c r="E1"><f>2+3</f><v>5</v></c></row>
                    <row r="2" ht="21" customHeight="1"><c r="A2" t="str"><v>Manual</v></c></row>
                    <row r="3"><c r="A3" t="str"><v>Merged</v></c></row>
                    <row r="4"><c r="A4" t="str" s="1"><v>Shrink</v></c></row>
                  </sheetData>
                  <mergeCells count="1"><mergeCell ref="A3:B3"/></mergeCells>
                  <pageSetup paperSize="9" orientation="portrait"/>
                </worksheet>
                """.formatted(SHEET);
        String styles = """
                <styleSheet xmlns="%s">
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">
                      <alignment shrinkToFit="1"/>
                    </xf>
                  </cellXfs>
                </styleSheet>
                """.formatted(SHEET);
        String strings = """
                <sst xmlns="%s" count="2" uniqueCount="1"><si><t>Shared</t></si></sst>
                """.formatted(SHEET);

        // 합성 파트만 임시 ZIP에 보관하고 다른 테스트나 사용자 파일과 경로를 공유하지 않는다.
        Path path = directory.resolve("source.xlsx");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            Map<String, String> entries = Map.of(WORKBOOK, workbook, "xl/_rels/workbook.xml.rels", relationships,
                    WORKSHEET, worksheet, STYLES, styles, "xl/sharedStrings.xml", strings);
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    // 테스트에서 원래 셀 주소로 찾을 수 있는 요소를 반환한다.
    private static Element cell(Element sheet, String address) {
        var cells = sheet.getElementsByTagNameNS(SHEET, "c");
        for (int index = 0; index < cells.getLength(); index++) {
            Element cell = (Element) cells.item(index);
            if (address.equals(cell.getAttribute("r"))) {
                return cell;
            }
        }
        throw new AssertionError(address);
    }

    // 테스트의 파트에 필요한 동일 네임스페이스 자식 하나를 읽는다.
    private static Element child(Element container, String name) {
        return OfficeParts.child(container, SHEET, name);
    }
}
