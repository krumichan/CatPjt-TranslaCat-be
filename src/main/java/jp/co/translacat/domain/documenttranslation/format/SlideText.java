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

/** PowerPoint 표시 슬라이드의 기본 도형과 표 셀에서 번역할 문단을 찾는다. */
final class SlideText {
    private static final String PRESENTATION = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String TABLE = "http://schemas.openxmlformats.org/drawingml/2006/table";
    private static final int MAX_SLIDES = 30;

    private SlideText() {
    }

    // 파일 이름이 아닌 실제 프레젠테이션 순서와 관계에 따라 슬라이드를 읽는다.
    static void collect(OfficeParts parts, OfficeTextPlan plan, String main) throws IOException {
        Document presentation = parts.xml(main);
        Element list = child(presentation.getDocumentElement(), PRESENTATION, "sldIdLst");
        List<Element> slides = children(list, PRESENTATION, "sldId");
        if (slides.size() > MAX_SLIDES) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_SLIDE_LIMIT, "PowerPoint slide limit exceeded");
        }

        Set<String> visited = new HashSet<>();
        for (int index = 0; index < slides.size(); index++) {
            String id = slides.get(index).getAttributeNS(OfficeParts.OFFICE_REL_ATTRIBUTE, "id");
            String part = parts.target(main, id, "slide");
            if (!visited.add(part.toLowerCase(Locale.ROOT))) {
                throw new IOException("PowerPoint registers the same slide several times");
            }
            Document doc = parts.xml(part);
            Element slide = doc.getDocumentElement();
            if (!is(slide, PRESENTATION, "sld")) {
                throw new IOException("Invalid PowerPoint slide root");
            }

            String show = slide.getAttribute("show");
            if (!show.equals("0") && !show.equalsIgnoreCase("false")) {
                Element core = child(slide, PRESENTATION, "cSld");
                Element tree = child(core, PRESENTATION, "spTree");
                new Slide(parts, plan, part, doc, "slide:" + (index + 1))
                        .collectShapes(tree, SlideLayoutFit.Scale.IDENTITY);
            }
        }
    }

    private static final class Slide {
        private final OfficeParts parts;
        private final OfficeTextPlan plan;
        private final String part;
        private final Document document;
        private final String location;
        private int shapes;

        // 같은 슬라이드에 속한 변경을 하나의 XML 파트로 모은다.
        private Slide(OfficeParts parts, OfficeTextPlan plan, String part, Document document, String location) {
            this.parts = parts;
            this.plan = plan;
            this.part = part;
            this.document = document;
            this.location = location;
        }

        // 그룹 확장, 기본 도형, 기본 표를 따라가며 노트나 다른 GraphicData에는 들어가지 않는다.
        private void collectShapes(Element tree, SlideLayoutFit.Scale group) throws IOException {
            for (Element shape : children(tree)) {
                if (is(shape, PRESENTATION, "sp")) {
                    String address = location + "/shape:" + ++shapes;
                    if (!hidden(shape, "nvSpPr")) {
                        Element body = child(shape, PRESENTATION, "txBody");
                        paragraphs(body, address);
                        SlideLayoutFit.track(plan, shape, body, group, address);
                    }
                } else if (is(shape, PRESENTATION, "grpSp")) {
                    if (!hidden(shape, "nvGrpSpPr")) {
                        collectShapes(shape, group.forGroup(shape));
                    }
                } else if (is(shape, PRESENTATION, "graphicFrame")) {
                    String address = location + "/shape:" + ++shapes;
                    if (!hidden(shape, "nvGraphicFramePr")) {
                        table(shape, address);
                    }
                }
            }
        }

        // 그림·차트·SmartArt가 아니라 OOXML 테이블만 표시 텍스트의 원래 위치로 취급한다.
        private void table(Element frame, String address) throws IOException {
            Element graphic = child(frame, DRAWING, "graphic");
            Element data = child(graphic, DRAWING, "graphicData");
            Element table = child(data, DRAWING, "tbl");
            if (table == null || !TABLE.equals(data.getAttribute("uri"))) {
                plan.warn("UNSUPPORTED_CONTENT", address);
                return;
            }

            int rowIndex = 0;
            for (Element row : children(table, DRAWING, "tr")) {
                rowIndex++;
                int column = 0;
                for (Element cell : children(row, DRAWING, "tc")) {
                    String cellAddress = address + "/cell:" + rowIndex + ":" + ++column;
                    if (!OfficeParts.enabled(cell.getAttribute("hMerge"))
                            && !OfficeParts.enabled(cell.getAttribute("vMerge"))) {
                        paragraphs(child(cell, DRAWING, "txBody"), cellAddress);
                    }
                }
            }
        }

        // 숨겨진 도형의 내용과 자식 그룹은 슬라이드에 나타나지 않으므로 건너뛴다.
        private static boolean hidden(Element shape, String propertiesName) {
            Element properties = child(child(shape, PRESENTATION, propertiesName), PRESENTATION, "cNvPr");
            return properties != null && OfficeParts.enabled(properties.getAttribute("hidden"));
        }

        // 텍스트 run만 읽고 줄바꿈·필드·링크의 위치와 도형 크기를 그대로 둔다.
        private void paragraphs(Element body, String address) throws IOException {
            int number = 0;
            for (Element paragraph : children(body, DRAWING, "p")) {
                String paragraphAddress = address + "/p:" + ++number;
                List<Element> group = new ArrayList<>();
                for (Element token : children(paragraph)) {
                    Element text = is(token, DRAWING, "r") ? child(token, DRAWING, "t") : null;
                    if (text != null) {
                        group.add(text);
                    } else {
                        flush(group, paragraphAddress);
                    }
                }
                flush(group, paragraphAddress);
            }
        }

        // 텍스트를 포함한 문단 조각만 원래 슬라이드에 연결한다.
        private void flush(List<Element> group, String address) throws IOException {
            if (!group.isEmpty()) {
                plan.add(address, group, () -> parts.changed(part, document));
                group.clear();
            }
        }
    }
}
