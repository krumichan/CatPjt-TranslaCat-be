package jp.co.translacat.domain.documenttranslation.format;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.child;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.children;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.is;

/** Word 본문과 활성 섹션이 사용하는 머리말·꼬리말의 표시 텍스트를 선택한다. */
final class WordText {
    private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Set<String> SKIPPED_WORD_ELEMENTS = Set.of(
            "sdt", "ins", "del", "moveFrom", "moveTo", "altChunk", "drawing", "pict", "object",
            "txbxContent", "fldSimple", "footnoteReference", "endnoteReference", "commentReference");
    private static final int MAX_PARAGRAPHS = 1_500;
    private static final int MAX_TABLES = 100;

    private WordText() {
    }

    // 본문을 먼저 읽고, 여러 섹션에서 공유하는 같은 머리말·꼬리말은 한 번만 읽는다.
    static void collect(OfficeParts parts, OfficeTextPlan plan, String main) throws IOException {
        Document doc = parts.xml(main);
        Element body = child(doc.getDocumentElement(), WORD, "body");
        if (body == null) {
            throw new IOException("Word document body is missing");
        }

        Counts counts = new Counts();
        new Story(parts, plan, main, "body", doc, counts).collect(body);

        Set<String> stories = new HashSet<>();
        var sections = body.getElementsByTagNameNS(WORD, "sectPr");
        for (int index = 0; index < sections.getLength(); index++) {
            Element section = (Element) sections.item(index);
            if (!activeSection(section, body)) {
                continue;
            }
            for (Element reference : children(section)) {
                if (is(reference, WORD, "headerReference")) {
                    collectReference(parts, plan, main, stories, reference, "header", "hdr", counts);
                } else if (is(reference, WORD, "footerReference")) {
                    collectReference(parts, plan, main, stories, reference, "footer", "ftr", counts);
                }
            }
        }
    }

    // 수정 추적에 기록된 과거의 섹션과 현재 문서에서 번역하지 않는 하위 스토리를 제외한다.
    private static boolean activeSection(Element section, Element body) {
        for (var node = section.getParentNode(); node instanceof Element element && node != body;
                node = node.getParentNode()) {
            if (is(element, WORD, "sectPrChange") || skipped(element)) {
                return false;
            }
        }
        return true;
    }

    // 섹션에서 참조하는 story의 관계 유형과 Word XML 루트를 확인한다.
    private static void collectReference(OfficeParts parts, OfficeTextPlan plan, String main, Set<String> stories,
                                         Element reference, String type, String root, Counts counts)
            throws IOException {
        String id = reference.getAttributeNS(OfficeParts.OFFICE_REL_ATTRIBUTE, "id");
        String part = parts.target(main, id, type);
        if (!stories.add(part.toLowerCase(Locale.ROOT))) {
            return;
        }

        Document doc = parts.xml(part);
        Element story = doc.getDocumentElement();
        if (!is(story, WORD, root)) {
            throw new IOException("Word header or footer has an unexpected root");
        }
        new Story(parts, plan, part, type + ":" + stories.size(), doc, counts).collect(story);
    }

    private static boolean skipped(Element element) {
        return WORD.equals(element.getNamespaceURI()) && SKIPPED_WORD_ELEMENTS.contains(element.getLocalName());
    }

    private static final class Counts {
        private int paragraphs;
        private int tables;
    }

    private static final class Story {
        private final OfficeParts parts;
        private final OfficeTextPlan plan;
        private final String part;
        private final String location;
        private final Document document;
        private final Counts counts;
        private int paragraphs;
        private int fields;

        // 필드 깊이는 본문과 각 머리말·꼬리말별로 독립적으로 유지한다.
        private Story(OfficeParts parts, OfficeTextPlan plan, String part, String location,
                      Document document, Counts counts) {
            this.parts = parts;
            this.plan = plan;
            this.part = part;
            this.location = location;
            this.document = document;
            this.counts = counts;
        }

        // Word 구조에서 문단과 표의 정해진 스토리 자식만 따라간다.
        private void collect(Element container) throws IOException {
            int previousFields = fields;
            walk(container);
            if (fields != previousFields) {
                throw new IOException("Word story has an unclosed complex field");
            }
        }

        // 중첩 표 셀을 문서 순서로 읽어 번역과 구조 한도를 적용한다.
        private void walk(Element container) throws IOException {
            for (Element element : children(container)) {
                if (is(element, WORD, "p")) {
                    if (++counts.paragraphs > MAX_PARAGRAPHS) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_WORD_PARAGRAPH_LIMIT,
                                "Word paragraph limit exceeded");
                    }
                    paragraph(element, location + "/p:" + ++paragraphs);
                } else if (is(element, WORD, "tbl")) {
                    if (++counts.tables > MAX_TABLES) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_WORD_TABLE_LIMIT, "Word table limit exceeded");
                    }
                    walk(element);
                } else if (is(element, WORD, "tr") || is(element, WORD, "tc")) {
                    walk(element);
                } else if (skipped(element)) {
                    plan.warn("UNSUPPORTED_CONTENT", location);
                }
            }
        }

        // 링크와 필드 경계를 넘어 글자 스타일이 옮겨가지 않도록 별도 조각으로 다룬다.
        private void paragraph(Element paragraph, String address) throws IOException {
            List<Element> group = new ArrayList<>();
            for (Element element : children(paragraph)) {
                if (is(element, WORD, "r")) {
                    run(element, address, group);
                } else if (is(element, WORD, "hyperlink")) {
                    flush(group, address);
                    for (Element run : children(element, WORD, "r")) {
                        run(run, address, group);
                    }
                    flush(group, address);
                } else {
                    flush(group, address);
                    if (skipped(element)) {
                        plan.warn("UNSUPPORTED_CONTENT", address);
                    }
                }
            }
            flush(group, address);
        }

        // 복합 필드의 지시와 결과를 숨기고 탭·줄바꿈은 기존 위치에 그대로 둔다.
        private void run(Element run, String address, List<Element> group) throws IOException {
            Element properties = child(run, WORD, "rPr");
            Element vanish = child(properties, WORD, "vanish");
            boolean hidden = vanish != null && (vanish.getAttributeNS(WORD, "val").isEmpty()
                    || OfficeParts.enabled(vanish.getAttributeNS(WORD, "val")));
            for (Element token : children(run)) {
                if (is(token, WORD, "rPr")) {
                    continue;
                }
                if (is(token, WORD, "t") && fields == 0 && !hidden) {
                    group.add(token);
                    continue;
                }

                flush(group, address);
                if (is(token, WORD, "fldChar")) {
                    switch (token.getAttributeNS(WORD, "fldCharType")) {
                        case "begin" -> fields++;
                        case "end" -> {
                            if (fields == 0) {
                                throw new IOException("Word complex field has no start marker");
                            }
                            fields--;
                        }
                        default -> { }
                    }
                } else if (skipped(token)) {
                    plan.warn("UNSUPPORTED_CONTENT", address);
                }
            }
        }

        // 논리 조각을 비우고 실제로 바뀐 story만 출력 ZIP에 반영한다.
        private void flush(List<Element> group, String address) throws IOException {
            if (!group.isEmpty()) {
                plan.add(address, group, () -> parts.changed(part, document));
                group.clear();
            }
        }
    }
}
