package jp.co.translacat.domain.documenttranslation.format;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.child;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.children;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.is;

/** Excel의 표시 셀만 번역하고 공유 문자열의 기존 참조와 수식 구조를 보존한다. */
final class WorkbookText {
    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final int MAX_SHEETS = 10;
    private static final int MAX_CELLS = 50_000;
    private static final int MAX_ROW = 1_048_576;
    private static final int MAX_COLUMN = 16_384;
    private static final Pattern CELL_REFERENCE = Pattern.compile("([A-Z]{1,3})([1-9][0-9]{0,6})");

    private final OfficeParts parts;
    private final OfficeTextPlan plan;
    private final SharedStrings shared;
    private final WorkbookLayout layout;
    private int cells;
    private boolean hasFormula;

    private WorkbookText(OfficeParts parts, OfficeTextPlan plan, SharedStrings shared, String main) {
        this.parts = parts;
        this.plan = plan;
        this.shared = shared;
        layout = new WorkbookLayout(parts, plan, main);
    }

    // 통합 문서 순으로 모든 시트를 확인하되 표시 시트에서만 텍스트를 선택한다.
    static void collect(OfficeParts parts, OfficeTextPlan plan, String main) throws IOException {
        Element workbook = parts.xml(main).getDocumentElement();
        List<Element> sheets = children(child(workbook, SHEET, "sheets"), SHEET, "sheet");
        if (sheets.size() > MAX_SHEETS) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_SHEET_LIMIT, "Excel worksheet limit exceeded");
        }

        SharedStrings shared = readSharedStrings(parts, main);
        WorkbookText collection = new WorkbookText(parts, plan, shared, main);
        Set<String> visited = new HashSet<>();
        for (int index = 0; index < sheets.size(); index++) {
            Element entry = sheets.get(index);
            String id = entry.getAttributeNS(OfficeParts.OFFICE_REL_ATTRIBUTE, "id");
            OfficeParts.Relationship relation = parts.relationships(main).get(id);
            if (relation == null || relation.external()) {
                throw new IOException("Excel worksheet relationship is missing");
            }

            String location = "sheet:" + (index + 1);
            if (!relation.type().equals(OfficeParts.OFFICE_RELS + "worksheet")) {
                plan.warn("UNSUPPORTED_CONTENT", location);
                continue;
            }
            String part = relation.target();
            if (!visited.add(part.toLowerCase(Locale.ROOT))) {
                throw new IOException("Excel registers the same worksheet several times");
            }

            String state = entry.getAttribute("state");
            Sheet sheet = collection.new Sheet(part, location);
            sheet.collect(state.isEmpty() || state.equals("visible"));
        }
    }

    // 원본 공유 문자열 인덱스는 추가 전 순서로 고정하고 관계가 없는 경우는 인라인 셀만 허용한다.
    private static SharedStrings readSharedStrings(OfficeParts parts, String main) throws IOException {
        SharedStrings table = null;
        for (var relation : parts.relationships(main).values()) {
            if (!relation.type().equals(OfficeParts.OFFICE_RELS + "sharedStrings")) {
                continue;
            }
            if (table != null || relation.external()) {
                throw new IOException("Excel shared strings relationship is invalid");
            }
            String part = relation.target();
            Document document = parts.xml(part);
            Element root = document.getDocumentElement();
            if (!is(root, SHEET, "sst")) {
                throw new IOException("Excel shared strings root is invalid");
            }
            table = new SharedStrings(part, document, root, children(root, SHEET, "si"));
        }
        return table;
    }

    // 원본 스타일 구역만 반환하고 발음 가이드가 있는 리치 문자열은 건너뛴다.
    private List<Element> stringRuns(Element container, String location) {
        if (!children(container, SHEET, "rPh").isEmpty() || child(container, SHEET, "phoneticPr") != null) {
            plan.warn("UNSUPPORTED_CONTENT", location);
            return List.of();
        }

        List<Element> result = new ArrayList<>();
        for (Element node : children(container)) {
            if (is(node, SHEET, "t")) {
                result.add(node);
            } else if (is(node, SHEET, "r")) {
                Element text = child(node, SHEET, "t");
                if (text != null) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    // Excel의 셀 문자열 한도는 바이트가 아닌 UTF-16 코드 단위와 줄 바꿈 수로 적용한다.
    private static void validateCellString(List<Element> pieces) throws IOException {
        int length = 0;
        long lineFeeds = 0;
        for (Element piece : pieces) {
            String text = piece.getTextContent();
            length += text.length();
            lineFeeds += text.chars().filter(character -> character == '\n').count();
        }
        if (length > 32_767 || lineFeeds > 253) {
            throw new IOException("Excel translated cell string is too long");
        }
    }

    // 수식 원문·캐시를 패치하지 않아도 문자열을 참조한 재계산 값은 달라질 수 있다.
    private void warnFormula() {
        if (hasFormula) {
            plan.warn("FORMULA_RESULT_MAY_CHANGE", "workbook");
        }
    }

    private final class Sheet {
        private final String part;
        private final String location;
        private final Document document;
        private final Element root;
        private final BitSet mergedRows = new BitSet();
        private WorkbookLayout.Columns columns;

        // 각 시트의 DOM을 따로 보유해 수정하지 않은 시트를 원본 바이트로 복사한다.
        private Sheet(String part, String location) throws IOException {
            this.part = part;
            this.location = location;
            document = parts.xml(part);
            root = document.getDocumentElement();
            if (!is(root, SHEET, "worksheet")) {
                throw new IOException("Excel worksheet root is invalid");
            }
        }

        // 행·열 숨김과 구조 보호 대상은 검증 한도에 포함하되 번역에서는 제외한다.
        private void collect(boolean visible) throws IOException {
            Addresses protection = visible ? protect() : null;
            BitSet hiddenColumns = visible ? hiddenColumns() : new BitSet();
            columns = visible ? new WorkbookLayout.Columns(root, MAX_COLUMN) : null;
            Element rows = child(root, SHEET, "sheetData");
            int rowNumber = 0;
            for (Element row : children(rows, SHEET, "row")) {
                int number = optionalNumber(row.getAttribute("r"), rowNumber + 1, MAX_ROW);
                if (number <= rowNumber || number < 1 || number > MAX_ROW) {
                    throw new IOException("Excel worksheet rows are out of order");
                }
                rowNumber = number;
                boolean display = visible && !OfficeParts.enabled(row.getAttribute("hidden"));
                if (display) {
                    protection.moveTo(rowNumber);
                }
                boolean wrapRow = display && !mergedRows.get(rowNumber) && columns.allowsAutoHeight(row);

                int nextColumn = 1;
                for (Element cell : children(row, SHEET, "c")) {
                    if (++cells > MAX_CELLS) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_SHEET_CELL_LIMIT,
                                "Excel worksheet cell limit exceeded");
                    }
                    String reference = cell.getAttribute("r");
                    Cell address = reference.isEmpty() ? new Cell(rowNumber, nextColumn) : cell(reference);
                    if (address.row() != rowNumber || address.column() < nextColumn || address.column() > MAX_COLUMN) {
                        throw new IOException("Excel worksheet cells are out of order");
                    }
                    nextColumn = address.column() + 1;
                    if (child(cell, SHEET, "f") != null) {
                        hasFormula = true;
                    } else if (display && !hiddenColumns.get(address.column())
                            && !protection.contains(address.column())) {
                        String position = reference.isEmpty() ? rowNumber + ":" + address.column() : reference;
                        collectCell(cell, address.column(), location + "/" + position, wrapRow);
                    }
                }
            }
        }

        // 병합 앵커, 테이블 헤더와 피벗 결과가 유지되도록 번역 금지 좌표 범위를 만든다.
        private Addresses protect() throws IOException {
            Addresses protectedCells = new Addresses();
            protectFormulaRanges(protectedCells);
            for (Element merge : children(child(root, SHEET, "mergeCells"), SHEET, "mergeCell")) {
                Range area = range(OfficeParts.required(merge, "ref"));
                mergedRows.set(area.firstRow(), area.lastRow() + 1);
                if (area.firstColumn() < area.lastColumn()) {
                    protectedCells.add(area.firstRow(), area.firstRow(), area.firstColumn() + 1, area.lastColumn());
                }
                if (area.firstRow() < area.lastRow()) {
                    protectedCells.add(area.firstRow() + 1, area.lastRow(), area.firstColumn(), area.lastColumn());
                }
            }

            for (var relationship : parts.relationships(part).values()) {
                if (relationship.type().equals(OfficeParts.OFFICE_RELS + "table")) {
                    Element table = parts.xml(relationship.target()).getDocumentElement();
                    if (!is(table, SHEET, "table")) {
                        throw new IOException("Excel table root is invalid");
                    }
                    Range area = range(OfficeParts.required(table, "ref"));
                    int headerRows = optionalNumber(table.getAttribute("headerRowCount"), 1,
                            area.lastRow() - area.firstRow() + 1);
                    if (headerRows > 0) {
                        protectedCells.add(area.firstRow(), area.firstRow() + headerRows - 1,
                                area.firstColumn(), area.lastColumn());
                    }
                } else if (relationship.type().equals(OfficeParts.OFFICE_RELS + "pivotTable")) {
                    Element pivot = parts.xml(relationship.target()).getDocumentElement();
                    Element pivotRange = child(pivot, SHEET, "location");
                    if (!is(pivot, SHEET, "pivotTableDefinition") || pivotRange == null) {
                        throw new IOException("Excel pivot table location is invalid");
                    }
                    Range area = range(OfficeParts.required(pivotRange, "ref"));
                    protectedCells.add(area.firstRow(), area.lastRow(), area.firstColumn(), area.lastColumn());
                }
            }
            protectedCells.sort();
            return protectedCells;
        }

        // 배열·공유·데이터 표 수식은 기준 셀 외의 셀에도 캐시 값을 저장할 수 있다.
        private void protectFormulaRanges(Addresses protectedCells) throws IOException {
            Element data = child(root, SHEET, "sheetData");
            if (data == null) {
                return;
            }
            var formulas = data.getElementsByTagNameNS(SHEET, "f");
            for (int index = 0; index < formulas.getLength(); index++) {
                Element formula = (Element) formulas.item(index);
                String reference = formula.getAttribute("ref");
                if (!reference.isEmpty() && formula.getParentNode() instanceof Element cell && is(cell, SHEET, "c")) {
                    Range area = range(reference);
                    protectedCells.add(area.firstRow(), area.lastRow(), area.firstColumn(), area.lastColumn());
                }
            }
        }

        // 시트에 저장된 열 숨김 플래그를 원래 열 번호에 적용한다.
        private BitSet hiddenColumns() throws IOException {
            BitSet hidden = new BitSet();
            for (Element column : children(child(root, SHEET, "cols"), SHEET, "col")) {
                if (!OfficeParts.enabled(column.getAttribute("hidden"))) {
                    continue;
                }
                int first = optionalNumber(OfficeParts.required(column, "min"), 0, MAX_COLUMN);
                int last = optionalNumber(OfficeParts.required(column, "max"), 0, MAX_COLUMN);
                if (first < 1 || last < first) {
                    throw new IOException("Excel hidden column range is invalid");
                }
                hidden.set(first, last + 1);
            }
            return hidden;
        }

        // 숫자와 수식 결과를 쓰지 않도록 원래 타입이 실제 문자열인 셀만 선택한다.
        private void collectCell(Element cell, int column, String address, boolean wrapRow) throws IOException {
            switch (cell.getAttribute("t")) {
                case "inlineStr" -> {
                    Element inline = child(cell, SHEET, "is");
                    collectString(cell, column, address, wrapRow, stringRuns(inline, address));
                }
                case "str" -> {
                    // 수식과 해당 참조 범위는 위에서 제외했다. 일반 문자열의 기존 v 저장 방식과 타입을 유지한다.
                    Element value = child(cell, SHEET, "v");
                    if (value != null) {
                        collectString(cell, column, address, wrapRow, List.of(value));
                    }
                }
                case "s" -> {
                    Element value = child(cell, SHEET, "v");
                    if (shared == null || value == null) {
                        throw new IOException("Excel shared cell has no shared string");
                    }
                    int index = optionalNumber(value.getTextContent(), -1, shared.originals.size() - 1);
                    if (index < 0) {
                        throw new IOException("Excel shared string index is missing");
                    }
                    shared.collect(WorkbookText.this, index,
                            new CellPatch(cell, value, this, column, address, wrapRow));
                }
                default -> { }
            }
        }

        // 셀의 기존 저장 표현을 바꾸지 않고 번역 결과의 길이를 확인한다.
        private void collectString(Element cell, int column, String address, boolean wrapRow, List<Element> runs)
                throws IOException {
            plan.add(address, runs, () -> {
                validateCellString(runs);
                warnFormula();
                applyLayout(cell, column, address, wrapRow, WorkbookLayout.measure(runs));
                parts.changed(part, document);
            });
        }

        // 시트별 열 너비와 자동 행 높이 제약을 기존 셀 스타일에 맞춰 확인한다.
        private void applyLayout(Element cell, int column, String address, boolean wrapRow,
                WorkbookLayout.TextWidth text) {
            layout.apply(cell, address, columns.width(column), wrapRow && columns.allowsStyle(column), text);
        }
    }

    private static final class SharedStrings {
        private final String part;
        private final Document document;
        private final Element root;
        private final List<Element> originals;
        private final Map<Integer, SharedEdit> edits = new HashMap<>();
        private int nextIndex;

        // 새 항목은 항상 기존 인덱스보다 뒤에 추가한다.
        private SharedStrings(String part, Document document, Element root, List<Element> originals) {
            this.part = part;
            this.document = document;
            this.root = root;
            this.originals = originals;
            nextIndex = originals.size();
        }

        // 같은 원본 공유 인덱스의 표시 셀만 연결하고 숨겨진 셀의 인덱스는 원본으로 유지한다.
        private void collect(WorkbookText workbook, int originalIndex, CellPatch patch) throws IOException {
            SharedEdit existing = edits.get(originalIndex);
            if (existing != null) {
                workbook.plan.charge(existing.usage());
                existing.cells().add(patch);
                return;
            }

            Element copy = (Element) originals.get(originalIndex).cloneNode(true);
            String address = patch.address();
            List<Element> runs = workbook.stringRuns(copy, address);
            List<CellPatch> cells = new ArrayList<>();
            cells.add(patch);
            OfficeTextPlan.Usage usage = workbook.plan.add(address, runs, () -> append(workbook, copy, runs, cells));
            edits.put(originalIndex, new SharedEdit(usage, cells));
        }

        // 기존 항목을 덮어쓰지 않고 번역본을 덧붙이며 기존 공유 문자열의 참조 개수는 유지한다.
        private void append(WorkbookText workbook, Element copy, List<Element> runs, List<CellPatch> cells)
                throws IOException {
            validateCellString(runs);
            Element extension = child(root, SHEET, "extLst");
            root.insertBefore(copy, extension);
            String index = Integer.toString(nextIndex++);
            if (root.hasAttribute("uniqueCount")) {
                root.setAttribute("uniqueCount", Integer.toString(nextIndex));
            }

            workbook.parts.changed(part, document);
            workbook.warnFormula();
            WorkbookLayout.TextWidth text = WorkbookLayout.measure(runs);
            for (CellPatch cell : cells) {
                cell.value().setTextContent(index);
                cell.sheet().applyLayout(cell.cell(), cell.column(), cell.address(), cell.wrapRow(), text);
                workbook.parts.changed(cell.sheet().part, cell.sheet().document);
            }
        }
    }

    // Excel의 표준 A1 주소를 행과 열 숫자로 변경한다.
    private static Cell cell(String reference) throws IOException {
        var match = CELL_REFERENCE.matcher(reference);
        if (!match.matches()) {
            throw new IOException("Invalid Excel cell reference");
        }

        String letters = match.group(1);
        int column = 0;
        for (int index = 0; index < letters.length(); index++) {
            column = column * 26 + letters.charAt(index) - 'A' + 1;
        }
        int row = Integer.parseInt(match.group(2));
        if (column > MAX_COLUMN || row > MAX_ROW) {
            throw new IOException("Excel cell reference is out of bounds");
        }
        return new Cell(row, column);
    }

    // XML에 기록된 범위의 시작과 끝은 정렬되어 있어야 한다.
    private static Range range(String reference) throws IOException {
        String[] ends = reference.split(":", -1);
        if (ends.length < 1 || ends.length > 2) {
            throw new IOException("Invalid Excel cell range");
        }
        Cell first = cell(ends[0]);
        Cell last = cell(ends[ends.length - 1]);
        if (first.row() > last.row() || first.column() > last.column()) {
            throw new IOException("Excel cell range is reversed");
        }
        return new Range(first.row(), last.row(), first.column(), last.column());
    }

    // XML 숫자 속성의 허용 범위를 한곳에서 확인한다.
    private static int optionalNumber(String value, int fallback, int maximum) throws IOException {
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.strip());
            if (parsed >= 0 && parsed <= maximum) {
                return parsed;
            }
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid Excel number");
        }
        throw new IOException("Excel number is out of bounds");
    }

    // 범위를 셀 단위로 확장하지 않고 행 이벤트와 열 합으로 보호된 셀을 계산한다.
    private static final class Addresses {
        private final List<Boundary> boundaries = new ArrayList<>();
        private final int[] columns = new int[MAX_COLUMN + 1];
        private int next;

        // 범위 시작과 종료 행에 열 증분 이벤트를 등록한다.
        private void add(int firstRow, int lastRow, int firstColumn, int lastColumn) {
            boundaries.add(new Boundary(firstRow, firstColumn, lastColumn, 1));
            boundaries.add(new Boundary(lastRow + 1, firstColumn, lastColumn, -1));
        }

        private void sort() {
            boundaries.sort(Comparator.comparingInt(Boundary::row));
        }

        // 현재 행까지 진입·종료 이벤트만 누적해 겹치는 범위도 유지한다.
        private void moveTo(int row) {
            while (next < boundaries.size() && boundaries.get(next).row() <= row) {
                Boundary boundary = boundaries.get(next++);
                addColumn(boundary.firstColumn(), boundary.change());
                addColumn(boundary.lastColumn() + 1, -boundary.change());
            }
        }

        // 구간 경계의 증분을 Fenwick 트리에 적용한다.
        private void addColumn(int column, int difference) {
            for (int index = column; index <= MAX_COLUMN; index += index & -index) {
                columns[index] += difference;
            }
        }

        // 현재 열에서 보호 범위가 하나라도 열려 있는지 확인한다.
        private boolean contains(int column) {
            int open = 0;
            for (int index = column; index > 0; index -= index & -index) {
                open += columns[index];
            }
            return open > 0;
        }
    }

    private record Cell(int row, int column) {
    }

    private record Range(int firstRow, int lastRow, int firstColumn, int lastColumn) {
    }

    private record Boundary(int row, int firstColumn, int lastColumn, int change) {
    }

    private record CellPatch(Element cell, Element value, Sheet sheet, int column, String address, boolean wrapRow) {
    }

    private record SharedEdit(OfficeTextPlan.Usage usage, List<CellPatch> cells) {
    }
}
