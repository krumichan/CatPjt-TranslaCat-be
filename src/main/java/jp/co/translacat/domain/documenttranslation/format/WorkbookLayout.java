package jp.co.translacat.domain.documenttranslation.format;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.child;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.children;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.enabled;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.is;

/** 번역 셀의 기존 스타일을 보존하면서 안전한 자동 줄 바꿈만 추가한다. */
final class WorkbookLayout {
    private static final String SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final double DEFAULT_WIDTH = 8.43;
    private static final int MAX_CELL_STYLES = 65_490;
    private static final Set<String> HORIZONTAL = Set.of("", "general", "left", "center", "right");
    private static final Set<String> VERTICAL = Set.of("", "top", "center", "bottom");

    private final OfficeParts parts;
    private final OfficeTextPlan plan;
    private final String workbook;
    private Styles styles;
    private boolean stylesRead;

    // 통합 문서의 모든 시트가 동일한 추가 스타일을 재사용한다.
    WorkbookLayout(OfficeParts parts, OfficeTextPlan plan, String workbook) {
        this.parts = parts;
        this.plan = plan;
        this.workbook = workbook;
    }

    // 실제 번역이 바뀌고 기존 너비로 넘칠 수 있는 셀만 변경하며 렌더링 확인 위치를 남긴다.
    void apply(Element cell, String location, double width, boolean autoHeight, TextWidth text) {
        if (!text.multiline() && text.longestLine() <= width) {
            return;
        }
        plan.warn("LAYOUT_REVIEW", location);
        if (!autoHeight || width <= 0 || text.customRun()) {
            return;
        }

        // 잘못되었거나 빠진 스타일 관계 때문에 기존의 문자열 번역을 실패시키지 않는다.
        if (!stylesRead) {
            styles = readStyles();
            stylesRead = true;
        }
        if (styles != null) {
            styles.wrap(cell);
        }
    }

    // 연결된 기존 스타일 파트 하나만 사용하고 새 파트나 관계는 추가하지 않는다.
    private Styles readStyles() {
        List<OfficeParts.Relationship> relations = parts.relationships(workbook).values().stream()
                .filter(relation -> relation.type().equals(OfficeParts.OFFICE_RELS + "styles"))
                .toList();
        if (relations.size() != 1 || relations.getFirst().external()) {
            return null;
        }

        try {
            String part = relations.getFirst().target();
            Document document = parts.xml(part);
            Element root = document.getDocumentElement();
            Element cellStyles = child(root, SHEET, "cellXfs");
            List<Element> formats = children(cellStyles, SHEET, "xf");
            if (!is(root, SHEET, "styleSheet") || formats.isEmpty() || formats.size() != children(cellStyles).size()
                    || number(cellStyles.getAttribute("count"), formats.size()) != formats.size()) {
                return null;
            }
            List<Element> bases = children(child(root, SHEET, "cellStyleXfs"), SHEET, "xf");
            return new Styles(part, document, cellStyles, formats, bases);
        } catch (IOException ignored) {
            return null;
        }
    }

    // OOXML의 열 너비 단위에 맞춰 가장 긴 줄을 추정하며 서식이 있는 리치 텍스트는 표시만 한다.
    static TextWidth measure(List<Element> runs) {
        int longest = 0;
        int line = 0;
        boolean multiline = false;
        boolean customRun = false;
        for (Element run : runs) {
            if (run.getParentNode() instanceof Element parent && is(parent, SHEET, "r")) {
                customRun |= child(parent, SHEET, "rPr") != null;
            }
            String value = run.getTextContent();
            for (int offset = 0; offset < value.length(); ) {
                int character = value.codePointAt(offset);
                offset += Character.charCount(character);
                if (character == '\n' || character == '\r') {
                    longest = Math.max(longest, line);
                    line = 0;
                    multiline = true;
                } else {
                    line += displayUnits(character);
                }
            }
        }
        return new TextWidth(Math.max(longest, line), multiline, customRun);
    }

    // 전각 문자와 결합 부호를 구분해 단순 글자 수보다 좁은 열의 잘림을 가깝게 추정한다.
    private static int displayUnits(int character) {
        return switch (Character.getType(character)) {
            case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
                    Character.FORMAT -> 0;
            default -> {
                if (character == '\t') {
                    yield 4;
                }
                if (character >= 0xFF01 && character <= 0xFF60) {
                    yield 2;
                }
                yield switch (Character.UnicodeScript.of(character)) {
                    case HAN, HANGUL, HIRAGANA, KATAKANA -> 2;
                    default -> 1;
                };
            }
        };
    }

    // 선택 속성의 기본값과 지원하지 않는 값이 서로 구분되도록 읽는다.
    private static int number(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // Excel의 너비 범위 안에서만 자동 줄 바꿈에 사용할 수 있다.
    private static double width(String value, double fallback) {
        try {
            double width = value.isEmpty() ? fallback : Double.parseDouble(value);
            return width >= 0 && width <= 255 ? width : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    record TextWidth(int longestLine, boolean multiline, boolean customRun) {
    }

    static final class Columns {
        private final double[] widths;
        private final BitSet styled = new BitSet();
        private final boolean autoRows;
        private boolean supported;

        // 명시 너비와 시트 기본 너비를 읽기만 하며 열 및 행의 레이아웃은 원본에 그대로 둔다.
        Columns(Element worksheet, int maxColumn) {
            Element format = child(worksheet, SHEET, "sheetFormatPr");
            autoRows = child(worksheet, SHEET, "sheetProtection") == null && (format == null
                    || (!enabled(format.getAttribute("customHeight")) && !enabled(format.getAttribute("zeroHeight"))));
            double standardWidth = DEFAULT_WIDTH;
            if (format != null) {
                standardWidth = WorkbookLayout.width(format.getAttribute("defaultColWidth"),
                        number(format.getAttribute("baseColWidth"), 8) + 0.43);
            }
            widths = new double[maxColumn + 1];
            supported = standardWidth > 0;
            Arrays.fill(widths, supported ? standardWidth : DEFAULT_WIDTH);

            // 서로 겹치거나 해석할 수 없는 열 범위에서는 열 스타일을 추정하지 않는다.
            int previous = 0;
            for (Element column : children(child(worksheet, SHEET, "cols"), SHEET, "col")) {
                int first = number(column.getAttribute("min"), -1);
                int last = number(column.getAttribute("max"), -1);
                double columnWidth = WorkbookLayout.width(column.getAttribute("width"), standardWidth);
                if (first <= previous || last < first || last > maxColumn || columnWidth < 0) {
                    supported = false;
                    break;
                }
                Arrays.fill(widths, first, last + 1, columnWidth);
                if (number(column.getAttribute("style"), 0) != 0) {
                    styled.set(first, last + 1);
                }
                previous = last;
            }
        }

        // 명시 크기와 행 수준 스타일을 유지하고 일반 자동 행에서만 높이 재계산을 허용한다.
        boolean allowsAutoHeight(Element row) {
            return autoRows && !row.hasAttribute("ht") && !enabled(row.getAttribute("customHeight"))
                    && !enabled(row.getAttribute("customFormat")) && !enabled(row.getAttribute("collapsed"))
                    && number(row.getAttribute("s"), 0) == 0;
        }

        // 상속된 열 스타일과 해석할 수 없는 열 레이아웃은 변경하지 않는다.
        boolean allowsStyle(int column) {
            return supported && !styled.get(column);
        }

        // 실제 열 레이아웃에 기록된 너비 또는 시트의 기본 너비를 제공한다.
        double width(int column) {
            return widths[column];
        }
    }

    private final class Styles {
        private final String part;
        private final Document document;
        private final Element table;
        private final List<Element> originals;
        private final List<Element> bases;
        private final Map<Integer, Integer> wrapped = new HashMap<>();

        // 원본 인덱스는 고정하고 새 스타일은 기존 목록 뒤에서만 번호를 매긴다.
        private Styles(String part, Document document, Element table, List<Element> originals, List<Element> bases) {
            this.part = part;
            this.document = document;
            this.table = table;
            this.originals = originals;
            this.bases = bases;
        }

        // 원본 및 공유 XF는 수정하지 않고 해당 셀에만 줄 바꿈 복제본을 연결한다.
        private void wrap(Element cell) {
            int index = number(cell.getAttribute("s"), 0);
            if (index < 0 || index >= originals.size()) {
                return;
            }
            Integer target = wrapped.get(index);
            if (target == null) {
                target = append(index);
                if (target == null) {
                    return;
                }
                wrapped.put(index, target);
            }
            cell.setAttribute("s", Integer.toString(target));
        }

        // 새 정렬 속성만 추가해 기존 글꼴, 채우기, 테두리와 보호 속성을 동일하게 보존한다.
        private Integer append(int index) {
            Element source = originals.get(index);
            Element alignment = child(source, SHEET, "alignment");
            int next = originals.size() + wrapped.size();
            if (next >= MAX_CELL_STYLES || !simpleFormat(source, alignment)) {
                return null;
            }

            Element copy = (Element) source.cloneNode(true);
            alignment = child(copy, SHEET, "alignment");
            if (alignment == null) {
                String prefix = source.getPrefix();
                alignment = document.createElementNS(SHEET, prefix == null ? "alignment" : prefix + ":alignment");
                copy.insertBefore(alignment, copy.getFirstChild());
            }
            alignment.setAttribute("wrapText", "1");
            copy.setAttribute("applyAlignment", "1");
            table.appendChild(copy);
            if (table.hasAttribute("count")) {
                table.setAttribute("count", Integer.toString(next + 1));
            }
            parts.changed(part, document);
            return next;
        }

        // 병용이 곤란한 사용자 지정 형식, 상속 정렬, 회전, 들여쓰기, 축소는 그대로 둔다.
        private boolean simpleFormat(Element source, Element alignment) {
            int format = number(source.getAttribute("numFmtId"), 0);
            int base = number(source.getAttribute("xfId"), 0);
            if ((format != 0 && format != 49) || base < 0 || (source.hasAttribute("xfId") && base >= bases.size())
                    || (base < bases.size() && child(bases.get(base), SHEET, "alignment") != null)) {
                return false;
            }
            if (alignment == null) {
                return true;
            }
            String apply = source.getAttribute("applyAlignment");
            if ((!apply.isEmpty() && !enabled(apply)) || enabled(alignment.getAttribute("wrapText"))
                    || !HORIZONTAL.contains(alignment.getAttribute("horizontal"))
                    || !VERTICAL.contains(alignment.getAttribute("vertical"))) {
                return false;
            }
            int supportedAttributes = (alignment.hasAttribute("horizontal") ? 1 : 0)
                    + (alignment.hasAttribute("vertical") ? 1 : 0) + (alignment.hasAttribute("wrapText") ? 1 : 0);
            return alignment.getAttributes().getLength() == supportedAttributes;
        }
    }
}
