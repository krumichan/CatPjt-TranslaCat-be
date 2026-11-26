package jp.co.translacat.domain.documenttranslation.format;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 압축 해제 바이트가 확인된 OPC XML을 외부 리소스 없이 읽는다. */
final class OfficePackageXml {
    static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final int MAX_DEPTH = 128;
    private static final int MAX_TEXT_CODE_POINTS = 32_768;
    private static final String CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String OFFICE_DOCUMENT =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final QName TYPES_ROOT = new QName(CONTENT_TYPES_NS, "Types");
    private static final QName DEFAULT_TYPE = new QName(CONTENT_TYPES_NS, "Default");
    private static final QName OVERRIDE_TYPE = new QName(CONTENT_TYPES_NS, "Override");
    private static final QName RELATIONSHIPS_ROOT = new QName(RELATIONSHIPS_NS, "Relationships");
    private static final QName RELATIONSHIP = new QName(RELATIONSHIPS_NS, "Relationship");

    private OfficePackageXml() {
    }

    // 콘텐츠 형식의 기본값과 파트별 재정의에서 같은 키를 여러 번 정의할 수 없다.
    static ContentTypes readContentTypes(ZipFile archive, ZipEntry manifest) throws IOException {
        Map<String, String> defaults = new HashMap<>();
        Map<String, String> overrides = new HashMap<>();
        read(archive, manifest, TYPES_ROOT, (element, depth) -> {
            if (depth == 1) {
                return;
            }

            QName name = element.getName();
            String declared = requiredAttribute(element, "ContentType").toLowerCase(Locale.ROOT);
            if (depth == 2 && DEFAULT_TYPE.equals(name)) {
                String extension = requiredAttribute(element, "Extension").toLowerCase(Locale.ROOT);
                if (defaults.putIfAbsent(extension, declared) == null) {
                    return;
                }
            } else if (depth == 2 && OVERRIDE_TYPE.equals(name)) {
                String partName = requiredAttribute(element, "PartName");
                if (!partName.startsWith("/")) {
                    throw new IOException("Office content type override must name an absolute part");
                }
                String key = OfficePackageInspector.partKey(partName.substring(1));
                if (overrides.putIfAbsent(key, declared) == null) {
                    return;
                }
            }
            throw new IOException("Office content types entry is invalid or repeated");
        });
        return new ContentTypes(defaults, overrides);
    }

    // 루트 관계에는 확장자와 대조할 내부 Office 주 파트 관계가 하나만 있어야 한다.
    static String readMainTarget(ZipFile archive, ZipEntry relationships) throws IOException {
        Set<String> ids = new HashSet<>();
        String[] mainTarget = new String[1];
        read(archive, relationships, RELATIONSHIPS_ROOT, (element, depth) -> {
            if (depth == 1) {
                return;
            }
            if (depth != 2 || !RELATIONSHIP.equals(element.getName())) {
                throw new IOException("Office package relationship entry is invalid");
            }

            String id = requiredAttribute(element, "Id");
            String type = requiredAttribute(element, "Type");
            String target = requiredAttribute(element, "Target");
            String mode = element.getAttributeValue(XMLConstants.NULL_NS_URI, "TargetMode");
            if (!ids.add(id) || mode != null && !mode.equals("Internal") && !mode.equals("External")) {
                throw new IOException("Office package relationship ID or mode is invalid");
            }

            if (OFFICE_DOCUMENT.equals(type)) {
                if (mainTarget[0] != null || "External".equals(mode)) {
                    throw new IOException("Office package must declare a single internal main part");
                }
                mainTarget[0] = resolveRootTarget(target);
            }
        });
        if (mainTarget[0] == null) {
            throw new IOException("Office package has no Transitional main-part relationship");
        }
        return mainTarget[0];
    }

    // 지정한 경우 주 파트의 QName을 대조하고 XML을 끝까지 읽어 제한을 확인한다.
    static void verify(ZipFile archive, ZipEntry part, QName expectedRoot) throws IOException {
        read(archive, part, expectedRoot, (element, depth) -> { });
    }

    // 패키지 루트에서 내부 URI를 해석하며 스킴·권한·쿼리·조각은 허용하지 않는다.
    private static String resolveRootTarget(String target) throws IOException {
        try {
            URI uri = new URI(target);
            String rawPath = uri.getRawPath();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || rawPath == null || rawPath.isEmpty()
                    || rawPath.contains("//")) {
                throw new IOException("Invalid Office main-part relationship target");
            }

            String path = URI.create("/").resolve(uri).normalize().getRawPath();
            String name = path.substring(1);
            OfficePackageInspector.partKey(name);
            return name;
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid Office main-part relationship target");
        }
    }

    // 검사된 실제 크기를 기준으로 읽고, XML 처리 오류에 입력 원문을 노출하지 않는다.
    private static void read(ZipFile archive, ZipEntry part, QName expectedRoot, ElementReader elements)
            throws IOException {
        if (part.getSize() > MAX_BYTES) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_LIMIT_EXCEEDED, "Office package XML size is out of bounds");
        }

        try (var input = archive.getInputStream(part)) {
            XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(input);
            try {
                visit(reader, expectedRoot, elements);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException exception) {
            throw new IOException("Office package contains malformed or unsafe XML");
        }
    }

    // JDK 기본 파서에 문서 유형과 외부 엔터티를 로드하지 않는 정책을 적용한다.
    private static XMLInputFactory secureXmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External resources are disabled");
        });
        return factory;
    }

    // XML 이벤트에서 QName과 깊이를 검사하고 잘게 전달된 연속 텍스트의 길이를 합산한다.
    private static void visit(XMLStreamReader reader, QName expectedRoot, ElementReader elements)
            throws IOException, XMLStreamException {
        int depth = 0;
        int textCodePoints = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE ->
                        throw new IOException("Office package XML declarations and entities are disabled");
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE, XMLStreamConstants.CDATA -> {
                    textCodePoints += Character.codePointCount(
                            reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                    if (textCodePoints > MAX_TEXT_CODE_POINTS) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_LIMIT_EXCEEDED, "Office package XML text is out of bounds");
                    }
                    continue;
                }
                case XMLStreamConstants.START_ELEMENT -> {
                    depth++;
                    if (depth > MAX_DEPTH || depth == 1 && expectedRoot != null
                            && !expectedRoot.equals(reader.getName())) {
                        throw new IOException("Office package XML root or depth is invalid");
                    }
                    elements.read(reader, depth);
                }
                case XMLStreamConstants.END_ELEMENT -> depth--;
                default -> { }
            }
            textCodePoints = 0;
        }
    }

    // OPC 메타데이터의 필수 속성은 네임스페이스 없이 값이 지정되어야 한다.
    private static String requiredAttribute(XMLStreamReader element, String name) throws IOException {
        String value = element.getAttributeValue(XMLConstants.NULL_NS_URI, name);
        if (value == null || value.isBlank()) {
            throw new IOException("Office package metadata is missing a required attribute");
        }
        return value;
    }

    record ContentTypes(Map<String, String> defaults, Map<String, String> overrides) {
        // OPC 규칙에 따라 파트별 재정의를 기본 확장자 매핑보다 우선한다.
        String find(String partName) {
            String key = partName.toLowerCase(Locale.ROOT);
            String override = overrides.get(key);
            if (override != null) {
                return override;
            }
            int dot = key.lastIndexOf('.');
            return dot > key.lastIndexOf('/') ? defaults.get(key.substring(dot + 1)) : null;
        }
    }

    @FunctionalInterface
    private interface ElementReader {
        // 검사 중인 요소에서 필요한 메타데이터를 읽고 잘못된 항목을 거절한다.
        void read(XMLStreamReader element, int depth) throws IOException;
    }
}
