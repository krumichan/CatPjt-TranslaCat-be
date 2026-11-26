package jp.co.translacat.domain.documenttranslation.format;

import org.w3c.dom.Element;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.child;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.children;
import static jp.co.translacat.domain.documenttranslation.format.OfficeParts.is;

/** 도형과 글자 서식은 그대로 두고, 읽을 수 있는 범위에서 가로 텍스트의 자동 맞춤을 추가한다. */
final class SlideLayoutFit {
    private static final String PRESENTATION = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final double EMU_PER_POINT = 12_700;
    private static final double DEFAULT_SIDE_INSET = 91_440;
    private static final double DEFAULT_VERTICAL_INSET = 45_720;
    private static final double MIN_FONT_POINTS = 9;
    private static final double WIDTH_RESERVE = 1.05;
    private static final double LINE_HEIGHT = 1.24;
    private static final int FULL_SCALE = 100_000;

    private SlideLayoutFit() {
    }

    // 번역한 run이 실제로 바뀐 도형만 모든 문단 갱신이 끝난 뒤 평가한다.
    static void track(OfficeTextPlan plan, Element shape, Element body, Scale group, String address) {
        var nodes = children(body, DRAWING, "p").stream()
                .flatMap(paragraph -> children(paragraph, DRAWING, "r").stream())
                .map(run -> child(run, DRAWING, "t"))
                .filter(text -> text != null)
                .toList();
        if (nodes.isEmpty()) {
            return;
        }
        List<String> original = nodes.stream().map(Element::getTextContent).toList();
        plan.afterTranslation(() -> {
            List<String> translated = nodes.stream().map(Element::getTextContent).toList();
            if (!original.equals(translated) && !apply(shape, body, group)) {
                plan.warn("LAYOUT_REVIEW", address);
            }
        });
    }

    // 배치에 필요한 글자 크기를 추정하고 가장 큰 안전 범위의 축소율만 기록한다.
    static boolean apply(Element shape, Element body, Scale group) {
        Element properties = child(body, DRAWING, "bodyPr");
        Area area = area(shape, properties, group);
        if (area == null) {
            return false;
        }

        List<Paragraph> paragraphs = measure(body);
        if (paragraphs.isEmpty()) {
            return false;
        }
        boolean wrap = !"none".equals(properties.getAttribute("wrap"));
        if (fits(paragraphs, area, wrap, FULL_SCALE)) {
            return true;
        }

        // 최소 크기에서도 맞지 않는 도형은 읽기 어려운 크기로 만들지 않고 검수 대상으로 남긴다.
        double minimumFont = paragraphs.stream().mapToDouble(Paragraph::minimumFont).min().orElse(0);
        int lower = (int) Math.ceil(MIN_FONT_POINTS * FULL_SCALE / minimumFont);
        if (lower >= FULL_SCALE || !fits(paragraphs, area, wrap, lower)) {
            return false;
        }
        int higher = FULL_SCALE;
        while (higher - lower > 1) {
            int middle = (lower + higher) / 2;
            if (fits(paragraphs, area, wrap, middle)) {
                lower = middle;
            } else {
                higher = middle;
            }
        }

        // 글자 run과 도형 좌표를 바꾸지 않도록 본문 속성에만 표준 자동 맞춤을 추가한다.
        String prefix = properties.getPrefix();
        String name = prefix == null ? "normAutofit" : prefix + ":normAutofit";
        Element fit = properties.getOwnerDocument().createElementNS(DRAWING, name);
        fit.setAttribute("fontScale", Integer.toString(lower));
        properties.insertBefore(fit, properties.getFirstChild());
        return true;
    }

    // 비어 있거나 명시 자동 맞춤·회전·특수 배치가 있는 도형은 계산 대상에서 제외한다.
    private static Area area(Element shape, Element properties, Scale group) {
        if (properties == null || !group.valid()) {
            return null;
        }
        Element nonVisual = child(child(shape, PRESENTATION, "nvSpPr"), PRESENTATION, "nvPr");
        Element transform = child(child(shape, PRESENTATION, "spPr"), DRAWING, "xfrm");
        if (child(nonVisual, PRESENTATION, "ph") != null || !unrotated(transform)
                || !unrotated(properties) || !horizontal(properties)
                || children(properties).stream().anyMatch(SlideLayoutFit::specialBodyProperty)) {
            return null;
        }

        // 그룹 배율을 적용한 폭과 높이에서 본문의 원래 여백을 차감한다.
        Element extent = child(transform, DRAWING, "ext");
        double width = number(extent, "cx", 0) * group.horizontal()
                - inset(properties, "lIns", DEFAULT_SIDE_INSET) - inset(properties, "rIns", DEFAULT_SIDE_INSET);
        double height = number(extent, "cy", 0) * group.vertical()
                - inset(properties, "tIns", DEFAULT_VERTICAL_INSET) - inset(properties, "bIns", DEFAULT_VERTICAL_INSET);
        return Double.isFinite(width) && Double.isFinite(height) && width > 0 && height > 0
                ? new Area(width / EMU_PER_POINT, height / EMU_PER_POINT) : null;
    }

    // 잘못된 음수 여백을 사용해 실제보다 큰 영역을 추정하지 않는다.
    private static double inset(Element properties, String name, double fallback) {
        double value = number(properties, name, fallback);
        return value >= 0 ? value : Double.NaN;
    }

    // 원래 자동 맞춤이나 특수 효과가 있는 경우 해당 정책의 우선순위를 존중한다.
    private static boolean specialBodyProperty(Element property) {
        return DRAWING.equals(property.getNamespaceURI()) && switch (property.getLocalName()) {
            case "noAutofit", "normAutofit", "spAutoFit", "prstTxWarp", "scene3d", "sp3d", "flatTx" -> true;
            default -> false;
        };
    }

    // 단일 가로 열과 표준 자동 줄바꿈만 보수적으로 측정한다.
    private static boolean horizontal(Element properties) {
        String orientation = properties.getAttribute("vert");
        return (orientation.isEmpty() || orientation.equals("horz"))
                && number(properties, "numCol", 1) == 1
                && !OfficeParts.enabled(properties.getAttribute("fromWordArt"));
    }

    // 직접 지정된 회전이나 반전이 있으면 가로 폭 추정에 사용하지 않는다.
    private static boolean unrotated(Element transform) {
        return transform != null && number(transform, "rot", 0) == 0
                && !OfficeParts.enabled(transform.getAttribute("flipH"))
                && !OfficeParts.enabled(transform.getAttribute("flipV"));
    }

    // 문단의 명시 글꼴과 줄바꿈을 읽으며, 상속이나 특수 기능을 추정할 수 없으면 표시만 한다.
    private static List<Paragraph> measure(Element body) {
        var paragraphs = new ArrayList<Paragraph>();
        Element list = child(body, DRAWING, "lstStyle");
        Element defaults = child(list, DRAWING, "defPPr");
        for (Element paragraph : children(body, DRAWING, "p")) {
            Element properties = child(paragraph, DRAWING, "pPr");
            double levelNumber = number(properties, "lvl", 0);
            if (!Double.isFinite(levelNumber) || levelNumber < 0 || levelNumber > 8) {
                return List.of();
            }
            Element level = child(list, DRAWING, "lvl" + ((int) levelNumber + 1) + "pPr");
            if (complexParagraph(properties) || complexParagraph(level) || complexParagraph(defaults)) {
                return List.of();
            }

            double defaultFont = fontSize(child(properties, DRAWING, "defRPr"),
                    fontSize(child(level, DRAWING, "defRPr"),
                            fontSize(child(defaults, DRAWING, "defRPr"), Double.NaN)));
            StringBuilder content = new StringBuilder();
            double maximum = 0;
            double minimum = Double.POSITIVE_INFINITY;
            for (Element token : children(paragraph)) {
                if (is(token, DRAWING, "br")) {
                    content.append('\n');
                } else if (is(token, DRAWING, "r")) {
                    Element text = child(token, DRAWING, "t");
                    Element run = child(token, DRAWING, "rPr");
                    double font = fontSize(run, defaultFont);
                    if (text == null || !Double.isFinite(font) || font <= 0
                            || number(run, "spc", 0) != 0 || number(run, "baseline", 0) != 0) {
                        return List.of();
                    }
                    content.append(text.getTextContent());
                    maximum = Math.max(maximum, font);
                    minimum = Math.min(minimum, font);
                } else if (!is(token, DRAWING, "pPr") && !is(token, DRAWING, "endParaRPr")) {
                    return List.of();
                }
            }

            // 빈 문단의 줄 높이는 명시된 마지막 run의 크기에서만 확인한다.
            if (maximum == 0) {
                maximum = fontSize(child(paragraph, DRAWING, "endParaRPr"), defaultFont);
                minimum = maximum;
            }
            if (!Double.isFinite(maximum) || maximum <= 0 || content.indexOf("\t") >= 0) {
                return List.of();
            }
            paragraphs.add(new Paragraph(lineWidths(content.toString(), maximum), maximum, minimum));
        }
        return paragraphs;
    }

    // 들여쓰기, 문단 간격 및 글머리표가 있으면 원래 레이아웃을 독립적으로 보존한다.
    private static boolean complexParagraph(Element properties) {
        return number(properties, "marL", 0) != 0 || number(properties, "marR", 0) != 0
                || number(properties, "indent", 0) != 0 || children(properties).stream().anyMatch(item ->
                        DRAWING.equals(item.getNamespaceURI()) && switch (item.getLocalName()) {
                            case "lnSpc", "spcBef", "spcAft", "buAutoNum", "buChar", "buBlip", "tabLst" -> true;
                            default -> false;
                        });
    }

    // 명시한 100분의 1 포인트 단위를 일반 포인트 단위로 변환한다.
    private static double fontSize(Element properties, double fallback) {
        return number(properties, "sz", fallback * 100) / 100;
    }

    // 기존 강제 줄바꿈을 유지하며, 일반 줄바꿈 후보를 단어와 동아시아 글자 경계에서 찾는다.
    private static List<List<Double>> lineWidths(String text, double font) {
        var hardLines = new ArrayList<List<Double>>();
        BreakIterator breaks = BreakIterator.getLineInstance(Locale.ROOT);
        for (String line : text.split("\\R", -1)) {
            var widths = new ArrayList<Double>();
            breaks.setText(line);
            int start = breaks.first();
            for (int end = breaks.next(); end != BreakIterator.DONE; end = breaks.next()) {
                double em = line.substring(start, end).codePoints().mapToDouble(SlideLayoutFit::glyphWidth).sum();
                widths.add(em * font * WIDTH_RESERVE);
                start = end;
            }
            hardLines.add(widths);
        }
        return hardLines;
    }

    // 실제 글꼴의 렌더링 대신 보수적인 글자군 폭으로 배치 가능성을 예상한다.
    private static double glyphWidth(int codePoint) {
        if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
            return 0;
        }
        if (Character.isWhitespace(codePoint)) {
            return codePoint == 0x3000 ? 1.0 : 0.30;
        }
        if (codePoint < 128) {
            if ("ilIjtfr".indexOf(codePoint) >= 0) {
                return 0.31;
            }
            if ("mwMW@%&".indexOf(codePoint) >= 0) {
                return 0.92;
            }
            if (Character.isUpperCase(codePoint)) {
                return 0.68;
            }
            return Character.isLetterOrDigit(codePoint) ? 0.54 : 0.39;
        }
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> 1.05;
            default -> codePoint >= 0x3000 ? 1.05 : 0.72;
        };
    }

    // 단어가 칸보다 넓거나 표시 줄 높이의 합이 본문 높이를 넘으면 맞춤을 적용하지 않는다.
    private static boolean fits(List<Paragraph> paragraphs, Area area, boolean wrap, int fontScale) {
        double scale = (double) fontScale / FULL_SCALE;
        double height = 0;
        for (Paragraph paragraph : paragraphs) {
            int lines = 0;
            for (List<Double> hardLine : paragraph.hardLines()) {
                lines++;
                double row = 0;
                for (double rawWidth : hardLine) {
                    double width = rawWidth * scale;
                    if (width > area.width()) {
                        return false;
                    }
                    if (row + width > area.width()) {
                        if (!wrap) {
                            return false;
                        }
                        lines++;
                        row = 0;
                    }
                    row += width;
                }
            }
            height += lines * paragraph.maximumFont() * scale * LINE_HEIGHT;
            if (height > area.height()) {
                return false;
            }
        }
        return true;
    }

    // 선택 속성의 OOXML 정수를 읽고 성공 여부를 수치 유효성으로 구분한다.
    private static double number(Element element, String name, double fallback) {
        if (element == null || !element.hasAttribute(name)) {
            return fallback;
        }
        try {
            return Long.parseLong(element.getAttribute(name));
        } catch (NumberFormatException malformed) {
            return Double.NaN;
        }
    }

    /** 그룹 좌표에서 실제 도형 크기로 적용할 누적 배율이다. */
    record Scale(double horizontal, double vertical) {
        static final Scale IDENTITY = new Scale(1, 1);
        private static final Scale UNSUPPORTED = new Scale(Double.NaN, Double.NaN);

        // 좌표의 크기만 반영하며 회전·반전되거나 치수가 없는 그룹은 원래 속성을 유지한다.
        Scale forGroup(Element group) {
            Element transform = child(child(group, PRESENTATION, "grpSpPr"), DRAWING, "xfrm");
            if (!valid() || !unrotated(transform)) {
                return UNSUPPORTED;
            }
            Element extent = child(transform, DRAWING, "ext");
            Element childExtent = child(transform, DRAWING, "chExt");
            return new Scale(horizontal * ratio(extent, childExtent, "cx"),
                    vertical * ratio(extent, childExtent, "cy"));
        }

        // 과도한 값이나 해석할 수 없는 배율로 문서 속성을 갱신하지 않는다.
        boolean valid() {
            return Double.isFinite(horizontal) && horizontal > 0 && Double.isFinite(vertical) && vertical > 0;
        }

        // 부모 도형과 자식 기준 좌표의 같은 축을 비교한다.
        private static double ratio(Element extent, Element childExtent, String axis) {
            double source = number(childExtent, axis, 0);
            double output = number(extent, axis, 0);
            return source > 0 && output > 0 ? output / source : Double.NaN;
        }
    }

    private record Area(double width, double height) {
    }

    private record Paragraph(List<List<Double>> hardLines, double maximumFont, double minimumFont) {
    }
}
