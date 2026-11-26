package jp.co.translacat.domain.documenttranslation.format;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 문서 번역을 시작하기 전에 Transitional Office 패키지의 접수 형식과 크기를 검사한다. */
public final class OfficePackageInspector {
    static final long MAX_COMPRESSED_BYTES = 10L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String ROOT_RELATIONSHIPS = "_rels/.rels";
    private static final String RELATIONSHIP_TYPE = "application/vnd.openxmlformats-package.relationships+xml";
    private static final String OOXML_MIME = "application/vnd.openxmlformats-officedocument.";
    private static final String OOXML_NAMESPACE = "http://schemas.openxmlformats.org/";
    private static final Set<String> ACTIVE_CONTENT_TYPES = Set.of(
            "application/vnd.ms-office.vbaproject",
            "application/vnd.ms-office.vbaprojectsignature",
            "application/vnd.ms-word.vbadata+xml",
            "application/vnd.ms-office.activex+xml",
            "application/vnd.ms-office.activex",
            OOXML_MIME + "oleobject",
            OOXML_MIME + "spreadsheetml.connections+xml",
            OOXML_MIME + "spreadsheetml.externallink+xml");

    private OfficePackageInspector() {
    }

    /**
     * 업로드가 끝난 변경 불가 사본의 ZIP과 Office 주 파트를 검사한다.
     *
     * @param packagePath 업로드 한도를 적용해 저장한 로컬 입력 파일
     * @param extension 점을 제외한 pptx, docx 또는 xlsx 확장자. 대소문자는 구분하지 않는다.
     * @return 주 파트의 실제 ZIP 이름과 디렉터리 마커를 제외한 ZIP 항목 수
     * @throws IOException 읽을 수 없거나 접수 검사 범위를 만족하지 않는 파일인 경우
     */
    public static Inspection inspect(Path packagePath, String extension) throws IOException {
        Format format = Format.fromExtension(extension);
        requireZipFile(packagePath);

        try (ZipFile archive = new ZipFile(packagePath.toFile())) {
            // 메타데이터에 의존하기 전에 이름과 각 스트림의 실제 크기·CRC를 확인한다.
            Map<String, ZipEntry> entries = index(archive);
            verifyExpandedEntries(archive, entries);
            ZipEntry manifest = requirePart(entries, CONTENT_TYPES);
            OfficePackageXml.ContentTypes types = OfficePackageXml.readContentTypes(archive, manifest);

            // 최상위 관계가 가리키는 실제 파트를 확장자별 MIME과 루트 요소에 대조한다.
            ZipEntry relationships = requirePart(entries, ROOT_RELATIONSHIPS);
            requireType(types, relationships, RELATIONSHIP_TYPE);
            ZipEntry main = requirePart(entries, OfficePackageXml.readMainTarget(archive, relationships));
            requireType(types, main, format.contentType());
            OfficePackageXml.verify(archive, main, format.root());

            // 형식이 선언된 나머지 파트에서 XML 제한과 명시적인 활성 콘텐츠 형식을 확인한다.
            int partCount = verifyRemainingParts(archive, entries, types, manifest, relationships, main);
            return new Inspection(main.getName(), partCount);
        }
    }

    /**
     * 접수 검사에서 확인한 Office 주 파트와 항목 수를 보관한다.
     *
     * @param mainPart 선행 슬래시가 없는 실제 ZIP 이름
     * @param partCount 메타데이터를 포함하고 디렉터리 마커를 제외한 ZIP 항목 수
     */
    public record Inspection(String mainPart, int partCount) {
    }

    // 크기 상한과 ZIP의 시작 시그니처를 중앙 디렉터리를 읽기 전에 확인한다.
    private static void requireZipFile(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Office package must be a regular file");
        }

        long size = Files.size(path);
        if (size < 4 || size > MAX_COMPRESSED_BYTES) {
            throw new IOException("Office package compressed size is out of bounds");
        }

        try (var input = Files.newInputStream(path)) {
            byte[] signature = input.readNBytes(4);
            if (signature.length != 4 || signature[0] != 'P' || signature[1] != 'K'
                    || signature[2] != 3 || signature[3] != 4) {
                throw new IOException("Office package is not an OPC ZIP archive");
            }
        }
    }

    // 중앙 디렉터리의 전체 항목을 색인하고 OPC 이름의 동등성 및 경로 충돌을 확인한다.
    private static Map<String, ZipEntry> index(ZipFile archive) throws IOException {
        if (archive.size() > MAX_ENTRIES) {
            throw new DocumentFormatPort.Rejected(
                    DocumentFormatPort.Rejection.DOCUMENT_ZIP_ENTRY_LIMIT, "Office package contains too many ZIP entries");
        }

        TreeMap<String, ZipEntry> entries = new TreeMap<>();
        var enumeration = archive.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            String name = entry.getName();
            String key = entry.isDirectory() ? partKey(name.substring(0, name.length() - 1)) : entryKey(name);
            if (entries.putIfAbsent(key, entry) != null) {
                throw new IOException("Office package contains equivalent ZIP names");
            }
        }

        // 정렬된 색인에서 파일명으로 시작하는 첫 하위 항목만 찾으면 부모 충돌을 확인할 수 있다.
        for (Map.Entry<String, ZipEntry> indexed : entries.entrySet()) {
            if (indexed.getValue().isDirectory()) {
                continue;
            }
            String prefix = indexed.getKey() + "/";
            String candidate = entries.ceilingKey(prefix);
            if (candidate != null && candidate.startsWith(prefix)) {
                throw new IOException("Office package part name is a parent of another entry");
            }
        }
        return entries;
    }

    // 콘텐츠 형식 스트림만 OPC 파트 URI 문법의 대괄호 예외를 사용한다.
    private static String entryKey(String name) throws IOException {
        return CONTENT_TYPES.equals(name) ? name.toLowerCase(Locale.ROOT) : partKey(name);
    }

    // 루트 슬래시를 제외한 실제 파트 이름을 검사하고 대소문자를 구분하지 않는 비교 키를 만든다.
    static String partKey(String name) throws IOException {
        if (name.isEmpty() || !name.chars().allMatch(character -> character < 128)
                || name.startsWith("/") || name.contains("//") || name.endsWith("/")
                || name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Invalid Office package part name");
        }

        try {
            URI uri = new URI("/" + name);
            if (!("/" + name).equals(uri.getRawPath())) {
                throw new IOException("Invalid Office package part name");
            }
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid Office package part name");
        }

        // 점으로 끝나는 구간과 별칭으로 해석될 수 있는 퍼센트 인코딩을 허용하지 않는다.
        for (String segment : name.split("/")) {
            if (segment.endsWith(".")) {
                throw new IOException("Invalid Office package path segment");
            }
            for (int escape = segment.indexOf('%'); escape >= 0; escape = segment.indexOf('%', escape + 3)) {
                int value = Integer.parseInt(segment.substring(escape + 1, escape + 3), 16);
                if (value <= 31 || value == 127 || value == '/' || value == '\\' || isUnreserved(value)) {
                    throw new IOException("Invalid escape in Office package part name");
                }
            }
        }
        return name.toLowerCase(Locale.ROOT);
    }

    // OPC가 퍼센트 인코딩을 허용하지 않는 RFC 3986 비예약 문자를 판별한다.
    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    // 필요 파트는 색인에서 실제 파일로 존재해야 하며 디렉터리 마커를 대신 사용할 수 없다.
    private static ZipEntry requirePart(Map<String, ZipEntry> entries, String name) throws IOException {
        ZipEntry entry = entries.get(entryKey(name));
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Office package is missing a required part");
        }
        return entry;
    }

    // ZIP 헤더의 선언값과 별개로 모든 스트림의 팽창 한도와 CRC를 확인한다.
    private static void verifyExpandedEntries(ZipFile archive, Map<String, ZipEntry> entries) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long expanded = 0;
        for (Map.Entry<String, ZipEntry> indexed : entries.entrySet()) {
            ZipEntry entry = indexed.getValue();
            boolean xmlPart = indexed.getKey().endsWith(".xml") || indexed.getKey().endsWith(".rels");

            long entryBytes = 0;
            CRC32 crc = new CRC32();
            try (var input = archive.getInputStream(entry)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    entryBytes += count;
                    expanded += count;
                    if (entry.isDirectory() && entryBytes > 0) {
                        throw new IOException("Office package ZIP directory contains data");
                    }
                    if (xmlPart && entryBytes > OfficePackageXml.MAX_BYTES) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_XML_SIZE_LIMIT,
                                "Office package XML part is too large");
                    }
                    if (expanded > MAX_EXPANDED_BYTES) {
                        throw new DocumentFormatPort.Rejected(
                                DocumentFormatPort.Rejection.DOCUMENT_EXPANDED_SIZE_LIMIT,
                                "Office package expanded size is out of bounds");
                    }
                    crc.update(buffer, 0, count);
                }
            }
            if (entryBytes != entry.getSize() || crc.getValue() != entry.getCrc()) {
                throw new IOException("Office package ZIP entry integrity check failed");
            }
        }
    }

    // 루트 관계와 주 파트의 MIME을 매니페스트의 유효한 매핑에 대조한다.
    private static void requireType(OfficePackageXml.ContentTypes types, ZipEntry entry, String expected)
            throws IOException {
        if (!expected.equals(types.find(entry.getName()))) {
            throw new IOException("Office package part does not have the expected content type");
        }
    }

    // 실제 파트 수를 계산하고 앞서 읽지 않은 XML 파트를 보안 제한 안에서 끝까지 검사한다.
    private static int verifyRemainingParts(ZipFile archive, Map<String, ZipEntry> entries,
                                            OfficePackageXml.ContentTypes types, ZipEntry manifest,
                                            ZipEntry relationships, ZipEntry main) throws IOException {
        int partCount = 0;
        for (Map.Entry<String, ZipEntry> indexed : entries.entrySet()) {
            ZipEntry entry = indexed.getValue();
            if (entry.isDirectory()) {
                continue;
            }
            partCount++;
            if (entry == manifest || entry == relationships || entry == main) {
                continue;
            }

            String type = types.find(entry.getName());
            if (type == null) {
                throw new IOException("Office package part does not have a declared content type");
            }
            int parameters = type.indexOf(';');
            String mediaType = parameters >= 0 ? type.substring(0, parameters).stripTrailing() : type;
            if (ACTIVE_CONTENT_TYPES.contains(mediaType) || mediaType.contains(".macroenabled.")
                    || mediaType.startsWith("application/vnd.openxmlformats-package.digital-signature-")) {
                throw new DocumentFormatPort.Rejected(
                        DocumentFormatPort.Rejection.UNSUPPORTED_PACKAGE, "Office package contains unsupported active content");
            }

            String name = indexed.getKey();
            if (name.endsWith(".xml") || name.endsWith(".rels") || mediaType.endsWith("+xml")
                    || mediaType.equals("application/xml") || mediaType.equals("text/xml")) {
                OfficePackageXml.verify(archive, entry, null);
            }
        }
        return partCount;
    }

    private record Format(String contentType, QName root) {
        // 업로드 확장자가 가리키는 Transitional 주 파트의 MIME과 루트를 정의한다.
        private static Format fromExtension(String extension) throws IOException {
            if (extension == null) {
                throw new IOException("Missing Office file extension");
            }
            return switch (extension.toLowerCase(Locale.ROOT)) {
                case "docx" -> new Format(OOXML_MIME + "wordprocessingml.document.main+xml",
                        new QName(OOXML_NAMESPACE + "wordprocessingml/2006/main", "document"));
                case "pptx" -> new Format(OOXML_MIME + "presentationml.presentation.main+xml",
                        new QName(OOXML_NAMESPACE + "presentationml/2006/main", "presentation"));
                case "xlsx" -> new Format(OOXML_MIME + "spreadsheetml.sheet.main+xml",
                        new QName(OOXML_NAMESPACE + "spreadsheetml/2006/main", "workbook"));
                default -> throw new IOException("Unsupported Office file extension");
            };
        }
    }
}
