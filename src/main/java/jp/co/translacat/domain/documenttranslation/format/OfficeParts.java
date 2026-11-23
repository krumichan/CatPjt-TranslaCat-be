package jp.co.translacat.domain.documenttranslation.format;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 검사된 패키지의 관계와 한정된 XML 파트를 읽고 변경 없는 ZIP 항목을 복사한다. */
final class OfficeParts implements AutoCloseable {
    static final String OFFICE_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    static final String OFFICE_REL_ATTRIBUTE = OFFICE_RELS.substring(0, OFFICE_RELS.length() - 1);
    private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String ROOT_RELS = "_rels/.rels";
    private static final Set<String> BLOCKED_RELATIONSHIP_TYPES = Set.of(
            "oleObject", "package", "control", "externalLink", "externalLinkPath", "connections", "queryTable",
            "vbaProject", "vbaProjectSignature", "activeXControl", "activeXControlBinary");

    private final DocumentBuilder builder;
    private final ZipFile archive;
    private final Map<String, ZipEntry> entries = new LinkedHashMap<>();
    private final Map<String, Map<String, Relationship>> relationships = new HashMap<>();
    private final Map<String, Document> changed = new HashMap<>();

    // 이미 검사한 원본만 열어 같은 ZIP 항목 순서와 실제 이름을 기억한다.
    OfficeParts(Path source) throws IOException {
        builder = createBuilder();
        archive = new ZipFile(source.toFile());
        var items = archive.entries();
        while (items.hasMoreElements()) {
            ZipEntry item = items.nextElement();
            entries.put(item.getName().toLowerCase(Locale.ROOT), item);
        }
    }

    // 모든 관계 파일에서 외부 리소스와 포함된 활성 콘텐츠를 차단하고 내부 위치를 확인한다.
    void validateRelationships() throws IOException {
        for (var indexed : entries.entrySet()) {
            if (indexed.getValue().isDirectory() || !indexed.getKey().endsWith(".rels")) {
                continue;
            }

            String file = indexed.getValue().getName();
            String source = relationshipSource(file);
            Element root = xml(file).getDocumentElement();
            if (!is(root, PACKAGE_RELS, "Relationships")) {
                throw new IOException("Invalid Office relationship root");
            }

            Map<String, Relationship> discovered = new LinkedHashMap<>();
            for (Element item : children(root)) {
                if (!is(item, PACKAGE_RELS, "Relationship")) {
                    throw new IOException("Invalid Office relationship element");
                }
                String id = required(item, "Id");
                String type = required(item, "Type");
                String target = required(item, "Target");
                String mode = item.getAttribute("TargetMode");
                String kind = type.substring(type.lastIndexOf('/') + 1);
                if (BLOCKED_RELATIONSHIP_TYPES.contains(kind) || type.startsWith(PACKAGE_RELS + "/digital-signature/")) {
                    throw new DocumentFormatPort.Rejected(
                            DocumentFormatPort.Rejection.UNSUPPORTED_PACKAGE, "Office package contains unsupported active content");
                }
                if (mode.equals("External") && !type.equals(OFFICE_RELS + "hyperlink")) {
                    throw new DocumentFormatPort.Rejected(
                            DocumentFormatPort.Rejection.UNSUPPORTED_PACKAGE, "Office package contains an unsupported external relationship");
                }
                if (!mode.isEmpty() && !mode.equals("Internal") && !mode.equals("External")) {
                    throw new IOException("Invalid Office relationship mode");
                }

                // 일반 외부 링크는 OOXML을 복사할 때 주소를 그대로 유지하며 서버에서 열지 않는다.
                boolean external = mode.equals("External");
                String address = external ? target : resolve(source, target);
                if (discovered.putIfAbsent(id, new Relationship(type, address, external)) != null) {
                    throw new IOException("Duplicate Office relationship ID");
                }
            }
            relationships.put(source.toLowerCase(Locale.ROOT), discovered);
        }
    }

    // 허용된 사전 정의 관계를 통해서만 문서의 스토리나 시트 파트를 선택한다.
    String target(String source, String id, String expected) throws IOException {
        Relationship relation = relationships(source).get(id);
        if (relation == null || relation.external() || !relation.type().equals(OFFICE_RELS + expected)) {
            throw new IOException("Missing or unexpected Office relationship");
        }
        return relation.target();
    }

    // 해당 소스의 관계를 문서 순서로 제공한다.
    Map<String, Relationship> relationships(String source) {
        return relationships.getOrDefault(source.toLowerCase(Locale.ROOT), Map.of());
    }

    // 형식 검사에서 검증된 XML 항목을 네임스페이스를 유지해 읽는다.
    Document xml(String part) throws IOException {
        ZipEntry entry = entry(part);
        if (entry.getSize() > OfficePackageXml.MAX_BYTES) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_XML_SIZE_LIMIT, "Office XML part is too large");
        }
        try (var input = archive.getInputStream(entry)) {
            return builder.parse(input);
        } catch (SAXException exception) {
            throw new IOException("Invalid Office XML part");
        }
    }

    // 쓰기에 반영할 XML만 등록하여 나머지 항목의 비압축 바이트가 바뀌지 않게 한다.
    void changed(String name, Document document) {
        changed.put(name.toLowerCase(Locale.ROOT), document);
    }

    // 기존 이름과 항목 순서대로 복사하고 변경을 등록한 XML만 직렬화한다.
    void write(Path output) throws IOException {
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        try (var destination = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var indexed : entries.entrySet()) {
                ZipEntry original = indexed.getValue();
                ZipEntry copy = new ZipEntry(original.getName());
                if (original.getTime() >= 0) {
                    copy.setTime(original.getTime());
                }
                destination.putNextEntry(copy);
                Document document = changed.get(indexed.getKey());
                if (document != null) {
                    factory.newTransformer().transform(new DOMSource(document), new StreamResult(destination));
                } else if (!original.isDirectory()) {
                    try (var input = archive.getInputStream(original)) {
                        input.transferTo(destination);
                    }
                }
                destination.closeEntry();
            }
        } catch (TransformerException exception) {
            throw new IOException("Could not write Office XML part");
        }
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }

    // 관계 파일 경로에서 루트 관계 또는 실제 원본 파트 이름을 찾는다.
    private String relationshipSource(String file) throws IOException {
        if (file.equalsIgnoreCase(ROOT_RELS)) {
            return "";
        }
        String key = file.toLowerCase(Locale.ROOT);
        int base = key.lastIndexOf("/_rels/");
        if (key.startsWith("_rels/") && base < 0) {
            return entry(file.substring("_rels/".length(), file.length() - ".rels".length())).getName();
        }
        if (base < 0) {
            throw new IOException("Invalid Office relationship part name");
        }
        String source = file.substring(0, base + 1)
                + file.substring(base + "/_rels/".length(), file.length() - ".rels".length());
        return entry(source).getName();
    }

    // 관계 주소를 패키지 안에서만 해석한다. 내부 하이퍼링크의 북마크는 파일 선택에 관여하지 않는다.
    private String resolve(String source, String target) throws IOException {
        try {
            URI relative = new URI(target);
            if (relative.isAbsolute() || target.startsWith("//") || relative.getRawAuthority() != null
                    || relative.getRawQuery() != null || relative.getRawPath() == null
                    || relative.getRawPath().contains("//")) {
                throw new IOException("Office relationship target is outside the package");
            }

            URI base = URI.create("/" + source);
            String name = relative.getRawPath().isEmpty() ? source
                    : base.resolve(relative).normalize().getRawPath().substring(1);
            return entry(name).getName();
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid Office relationship address");
        }
    }

    // 디렉터리와 메타데이터가 아닌 실제 OPC 파트를 대소문자 규칙에 따라 선택한다.
    private ZipEntry entry(String name) throws IOException {
        ZipEntry entry = entries.get(OfficePackageInspector.partKey(name));
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Office package part is missing");
        }
        return entry;
    }

    // 내부 XML 파서는 외부 접근과 문서 유형 처리를 모두 비활성화한다.
    private static DocumentBuilder createBuilder() throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            return builder;
        } catch (ParserConfigurationException exception) {
            throw new IOException("Could not configure Office XML parser");
        }
    }

    static boolean is(Element element, String namespace, String name) {
        return element != null && namespace.equals(element.getNamespaceURI()) && name.equals(element.getLocalName());
    }

    // 텍스트, 주석 등은 원본 DOM에 보존하고 탐색에 필요한 직접 자식 요소만 반환한다.
    static List<Element> children(Element parent) {
        List<Element> children = new ArrayList<>();
        if (parent != null) {
            for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element) {
                    children.add(element);
                }
            }
        }
        return children;
    }

    static List<Element> children(Element parent, String namespace, String name) {
        return children(parent).stream().filter(element -> is(element, namespace, name)).toList();
    }

    static Element child(Element parent, String namespace, String name) {
        for (Element element : children(parent)) {
            if (is(element, namespace, name)) {
                return element;
            }
        }
        return null;
    }

    static boolean enabled(String value) {
        return value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
    }

    // 필수 XML 속성은 기본 네임스페이스에서만 선택한다.
    static String required(Element element, String name) throws IOException {
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            throw new IOException("Office XML attribute is missing");
        }
        return value;
    }

    record Relationship(String type, String target, boolean external) {
    }
}
