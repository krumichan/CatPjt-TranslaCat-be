package jp.co.translacat.domain.documenttranslation.format;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideLayoutFitTest {
    private static final String DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String PRESENTATION = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String SOURCE = "予約内容が変更された場合は、案内所の担当者に連絡してください。";
    private static final String TRANSLATION =
            "If reservation details change, please contact the person in charge at the information desk.";

    @Test
    void fitsTheReproducedGroupedBoxWithoutChangingGeometryOrOtherRunProperties() throws Exception {
        // 준비: 재현된 슬라이드의 원래 박스, 글자 크기 및 그룹 배율을 사용한다.
        Element shape = shape("");
        Element body = OfficeParts.child(shape, PRESENTATION, "txBody");
        Element run = (Element) shape.getElementsByTagNameNS(DRAWING, "r").item(0);
        Element text = OfficeParts.child(run, DRAWING, "t");
        var transform = OfficeParts.child(shape, PRESENTATION, "spPr").cloneNode(true);
        Element runProperties = (Element) OfficeParts.child(run, DRAWING, "rPr").cloneNode(true);
        var plan = new OfficeTextPlan();
        plan.add("slide:1/shape:3/p:1", List.of(text), () -> {});
        var grouped = new SlideLayoutFit.Scale(6_810_720.0 / 12_192_000, 3_830_472.0 / 6_858_000);
        SlideLayoutFit.track(plan, shape, body, grouped, "slide:1/shape:3");

        // 실행: 원래 겹침을 재현한 것과 같은 번역문을 붙인다.
        plan.apply(Map.of("p1", TRANSLATION));

        // 검증: 직접 지정된 글자 크기만 줄이며 자동 맞춤 배율을 중립으로 둔다.
        Element fit = OfficeParts.child(OfficeParts.child(body, DRAWING, "bodyPr"), DRAWING, "normAutofit");
        assertEquals(100_000, Integer.parseInt(fit.getAttribute("fontScale")));
        Element fittedRun = OfficeParts.child(run, DRAWING, "rPr");
        int fittedSize = Integer.parseInt(fittedRun.getAttribute("sz"));
        assertTrue(fittedSize >= 900 && fittedSize < 2200);

        // 기존 좌표, run의 나머지 속성 및 번역문은 유지한다.
        assertTrue(transform.isEqualNode(OfficeParts.child(shape, PRESENTATION, "spPr")));
        runProperties.setAttribute("sz", fittedRun.getAttribute("sz"));
        assertTrue(runProperties.isEqualNode(fittedRun));
        assertEquals(TRANSLATION, text.getTextContent());
    }

    @Test
    void leavesUnchangedTextAndExplicitExistingFitAlone() throws Exception {
        // 준비: 원문이 유지되는 도형과 작성자가 직접 맞춤을 끈 도형을 배치한다.
        Element original = shape("");
        Element explicit = shape("<a:noAutofit/>");
        var plan = new OfficeTextPlan();
        track(plan, original, "slide:1/shape:1");
        track(plan, explicit, "slide:1/shape:2");

        // 실행: 첫 도형은 입력 그대로, 두 번째 도형은 더 긴 문구로 처리한다.
        plan.apply(Map.of("p1", SOURCE, "p2", TRANSLATION));

        // 검증: 맞춤을 추가하지 않으며, 두 번째 도형에서만 검수 위치를 남긴다.
        assertNull(fit(original));
        assertNull(fit(explicit));
        assertEquals(1, explicit.getElementsByTagNameNS(DRAWING, "noAutofit").getLength());
        assertTrue(plan.warnings().stream().anyMatch(item -> item.location().equals("slide:1/shape:2")));
        assertFalse(plan.warnings().stream().anyMatch(item -> item.location().startsWith("slide:1/shape:1")));
    }

    // 테스트 계획에도 운영과 같은 번역 완료 후 도형 검사를 등록한다.
    private static void track(OfficeTextPlan plan, Element shape, String location) throws Exception {
        Element body = OfficeParts.child(shape, PRESENTATION, "txBody");
        Element text = (Element) shape.getElementsByTagNameNS(DRAWING, "t").item(0);
        plan.add(location + "/p:1", List.of(text), () -> {});
        SlideLayoutFit.track(plan, shape, body, SlideLayoutFit.Scale.IDENTITY, location);
    }

    // 기록된 표준 자동 맞춤 속성이 있는지 읽는다.
    private static Element fit(Element shape) {
        Element body = OfficeParts.child(shape, PRESENTATION, "txBody");
        return OfficeParts.child(OfficeParts.child(body, DRAWING, "bodyPr"), DRAWING, "normAutofit");
    }

    // 재현과 동일한 일반 가로 도형을 최소 XML 조각으로 만든다.
    private static Element shape(String autofit) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        String xml = """
                <p:sp xmlns:p="%s" xmlns:a="%s">
                  <p:spPr><a:xfrm><a:off x="704850" y="2514600"/>
                    <a:ext cx="10572750" cy="533400"/>
                  </a:xfrm></p:spPr>
                  <p:txBody><a:bodyPr>%s</a:bodyPr><a:lstStyle/>
                    <a:p><a:r><a:rPr sz="2200"/><a:t>%s</a:t></a:r></a:p>
                  </p:txBody>
                </p:sp>
                """.formatted(PRESENTATION, DRAWING, autofit, SOURCE);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
}
