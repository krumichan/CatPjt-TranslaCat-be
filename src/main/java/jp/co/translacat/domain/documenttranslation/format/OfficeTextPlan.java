package jp.co.translacat.domain.documenttranslation.format;

import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort.Piece;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort.Segment;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort.Warning;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.io.IOException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 문서에서 발견한 논리 단위와 그 조각이 돌아갈 원본 위치를 관리한다. */
final class OfficeTextPlan {
    private static final int MAX_SEGMENTS = 2_000;
    private static final int MAX_CODE_POINTS = 30_000;
    private static final int MAX_SEGMENT_CODE_POINTS = 2_000;
    private static final int MAX_WARNINGS = 50;
    private static final String WORD_TEXT_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String CELL_TEXT_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private final List<Segment> segments = new ArrayList<>();
    private final List<TextGroup> groups = new ArrayList<>();
    private final Map<String, Piece> originals = new LinkedHashMap<>();
    private final LinkedHashSet<Warning> warnings = new LinkedHashSet<>();
    private int totalCodePoints;
    private int totalSegments;

    // 스타일과 컴포넌트 순서가 같은 위치에만 결과를 기록할 수 있도록 각 텍스트 요소를 조각에 묶는다.
    Usage add(String location, List<Element> elements, ChangedAction changed) throws IOException {
        List<NodeEdit> edits = new ArrayList<>();
        List<Piece> pending = new ArrayList<>();
        int pendingLength = 0;
        int groupLength = 0;
        int start = segments.size();

        for (Element element : elements) {
            String source = element.getTextContent();
            if (!OfficeParts.children(element).isEmpty()) {
                throw new IOException("Office text element contains nontext content");
            }
            if (!source.codePoints().anyMatch(Character::isLetter)) {
                continue;
            }

            List<String> ids = new ArrayList<>();
            for (String value : splitLongText(source)) {
                int length = value.codePointCount(0, value.length());
                if (pendingLength + length > MAX_SEGMENT_CODE_POINTS || pending.size() == 64) {
                    addSegment(pending);
                    pending.clear();
                    pendingLength = 0;
                }

                String id = "p" + (originals.size() + 1);
                Piece piece = new Piece(id, value);
                originals.put(id, piece);
                ids.add(id);
                pending.add(piece);
                pendingLength += length;
                groupLength += length;
            }
            edits.add(new NodeEdit(element, List.copyOf(ids)));
        }

        if (!pending.isEmpty()) {
            addSegment(pending);
        }
        if (!edits.isEmpty()) {
            groups.add(new TextGroup(location, groupLength, edits, changed));
        }

        // 공유 문자열을 중복 제거하기 전 원래 텍스트와 논리 단위가 차지하는 비용을 더한다.
        Usage usage = new Usage(segments.size() - start, groupLength);
        charge(usage);
        return usage;
    }

    // 실제 대상 셀이 같은 공유 문자열을 재사용하더라도 입력 한도에서 제외하지 않는다.
    void charge(Usage usage) throws IOException {
        totalSegments += usage.segments();
        totalCodePoints += usage.codePoints();
        if (totalSegments > MAX_SEGMENTS || totalCodePoints > MAX_CODE_POINTS) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_LIMIT_EXCEEDED, "Office translation text budget exceeded");
        }
    }

    // 사용자에게 도움이 되는 처음 몇 개의 서로 다른 위치만 남겨 응답 크기를 제한한다.
    void warn(String code, String location) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(new Warning(code, location));
        }
    }

    List<Segment> segments() {
        return List.copyOf(segments);
    }

    List<Warning> warnings() {
        return List.copyOf(warnings);
    }

    // 모델이 누락하거나 추가한 조각을 거절한 뒤, 허용된 기존 노드와 파트에만 결과를 반영한다.
    List<Warning> apply(Map<String, String> translated) throws IOException {
        if (translated == null || !originals.keySet().equals(translated.keySet())) {
            throw new IOException("Office translation pieces do not match the plan");
        }
        for (var result : translated.entrySet()) {
            String value = result.getValue();
            if (value == null || value.isBlank() || value.length() > 32_768
                    || !value.codePoints().allMatch(OfficeTextPlan::isXmlCharacter)) {
                throw new IOException("Office translation contains an invalid text piece");
            }
        }

        for (TextGroup group : groups) {
            int translatedLength = 0;
            boolean changed = false;
            for (NodeEdit edit : group.edits()) {
                StringBuilder output = new StringBuilder();
                for (String id : edit.pieceIds()) {
                    output.append(translated.get(id));
                }
                String value = output.toString();
                translatedLength += value.codePointCount(0, value.length());
                if (!value.equals(edit.element().getTextContent())) {
                    replaceText(edit.element(), value);
                    changed = true;
                }
            }

            // 글자가 크게 늘어난 위치는 기존 폭과 자동 맞춤을 그대로 두므로 시각 검수 대상으로 표시한다.
            if (changed) {
                group.changed().apply();
                if (translatedLength > group.originalLength() + Math.max(8, group.originalLength() / 3)) {
                    warn("LAYOUT_REVIEW", group.location());
                }
            }
        }
        return warnings();
    }

    // 기존 요소와 주석을 두고 문자 노드에만 결과를 기록한다.
    private static void replaceText(Element element, String value) {
        Node firstText = null;
        for (Node node = element.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (firstText == null) {
                    firstText = element.getOwnerDocument().createTextNode(value);
                    element.replaceChild(firstText, node);
                } else {
                    element.removeChild(node);
                }
            }
            node = next;
        }
        if (firstText == null) {
            element.appendChild(element.getOwnerDocument().createTextNode(value));
        }

        // Word와 Excel에서 번역으로 생긴 바깥쪽 공백도 표시되도록 텍스트 요소의 공백 정책을 보완한다.
        String namespace = element.getNamespaceURI();
        if ("t".equals(element.getLocalName()) && (WORD_TEXT_NS.equals(namespace) || CELL_TEXT_NS.equals(namespace))
                && (Character.isWhitespace(value.codePointAt(0))
                        || Character.isWhitespace(value.codePointBefore(value.length())))) {
            element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve");
        }
    }

    // 긴 단일 서식 조각은 문장 경계만 이용하여 나누고 원래 문자열 순서를 유지한다.
    private static List<String> splitLongText(String source) throws IOException {
        if (source.codePointCount(0, source.length()) <= MAX_SEGMENT_CODE_POINTS) {
            return List.of(source);
        }

        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(source);
        int start = iterator.first();
        int end;
        while ((end = iterator.next()) != BreakIterator.DONE) {
            if (source.codePointCount(start, end) > MAX_SEGMENT_CODE_POINTS) {
                throw new DocumentFormatPort.Rejected(
                        DocumentFormatPort.Rejection.DOCUMENT_LIMIT_EXCEEDED, "Office translation contains an oversized sentence");
            }
            sentences.add(source.substring(start, end));
            start = end;
        }
        return sentences;
    }

    // 순번은 병렬 공급자 반환 순서와 상관없이 문서 탐색 순서에만 따라 결정된다.
    private void addSegment(List<Piece> pieces) {
        segments.add(new Segment("s" + (segments.size() + 1), pieces));
    }

    // XML 1.0 텍스트로 표현할 수 없는 제어 문자와 고립된 surrogate를 거절한다.
    private static boolean isXmlCharacter(int value) {
        return value == 0x09 || value == 0x0A || value == 0x0D
                || value >= 0x20 && value <= 0xD7FF
                || value >= 0xE000 && value <= 0xFFFD
                || value >= 0x10000 && value <= 0x10FFFF;
    }

    record Usage(int segments, int codePoints) {
    }

    private record NodeEdit(Element element, List<String> pieceIds) {
    }

    private record TextGroup(String location, int originalLength, List<NodeEdit> edits, ChangedAction changed) {
    }

    @FunctionalInterface
    interface ChangedAction {
        // 기존 파트 중 영향을 받은 파트만 등록하고 형식 한도를 적용한다.
        void apply() throws IOException;
    }
}
