package jp.co.translacat.domain.imagetranslation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationAssets.hash;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
class ImageTranslationInput {
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern SORT_WORD = Pattern.compile("[0-9]+|[^0-9]+");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final ImageTranslationAssets assets;
    private final ImageTranslationLinks links;

    ImageTranslationInput(ImageTranslationAssets assets, ImageTranslationLinks links) {
        this.assets = assets;
        this.links = links;
    }

    // 이미지 또는 ZIP은 고정 객체를 검증하고, 링크에서는 승인 커넥터의 순서를 유지한다.
    Prepared prepare(Job job) throws IOException {
        if (job.sourceKind == SourceKind.LINK) {
            return links.prepare(job);
        }
        List<Page> pages = new ArrayList<>();
        List<InputIssue> issues = new ArrayList<>();
        if (job.sourceKind == SourceKind.ZIP) {
            prepareZip(job, pages, issues);
        } else {
            prepareFiles(job, pages, issues);
        }

        // 업로드 순서에 상관없이 숫자 단위 자연 정렬을 하고 동률은 기존 순서를 유지한다.
        pages.sort(Comparator.comparing(page -> page.fileName, ImageTranslationInput::naturalOrder));
        long totalPixels = pages.stream().mapToLong(page -> page.image.pixels()).sum();
        if (totalPixels > MAX_EPISODE_PIXELS || pages.size() > MAX_PAGES) {
            throw inputLimit();
        }
        for (int index = 0; index < pages.size(); index++) {
            pages.get(index).order = index;
        }
        return new Prepared(pages, issues, null);
    }

    // 준비한 링크 페이지를 내려받아 같은 검증·저장 경계를 통과시킨다.
    void acquire(UUID jobId, Page page) throws IOException {
        byte[] bytes = links.acquire(page.sourcePath);
        ImageInfo image = inspect(bytes, null);
        assets.put(jobId, page.objectId, new ByteArrayInputStream(bytes), bytes.length);
        page.image = image;
        page.sha256 = hash(bytes);
    }

    // 선택한 언어 모델에 넘기기 전에 불변 원본을 최대 이미지 바이트까지 읽는다.
    byte[] imageBytes(UUID jobId, UUID objectId) throws IOException {
        try (InputStream source = assets.open(jobId, objectId)) {
            return limit(source, MAX_IMAGE_BYTES);
        }
    }

    // 업로드 파일마다 타입 선언과 실측 타입을 대조하고 사용자가 제외 사유를 볼 수 있게 한다.
    private void prepareFiles(Job job, List<Page> pages, List<InputIssue> issues) throws IOException {
        for (Input input : job.inputs) {
            FileRequest file = input.declaration();
            byte[] bytes = imageBytes(job.id, input.objectId());
            try {
                ImageInfo info = inspect(bytes, file.contentType());
                pages.add(newPage(input.objectId(), file.fileName(), info, bytes));
            } catch (WorkFailure error) {
                issues.add(new InputIssue(file.fileName(), error.failure.code(), file.clientFileId()));
            }
        }
    }

    // 디스크에 경로를 풀지 않고 압축 엔트리 이름·실제 해제 바이트·장수를 제한한다.
    private void prepareZip(Job job, List<Page> pages, List<InputIssue> issues) throws IOException {
        Set<String> names = new HashSet<>();
        int unpacked = 0;
        int count = 0;

        try (InputStream input = assets.open(job.id, job.inputs.getFirst().objectId());
                ZipInputStream archive = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++count > MAX_ENTRY_COUNT) {
                    throw inputLimit();
                }
                String path = zipName(entry.getName(), entry.isDirectory());
                if (!names.add(path.toLowerCase(Locale.ROOT))) {
                    throw invalidArchive();
                }
                if (entry.isDirectory()) {
                    // 디렉터리 데이터 역시 읽어 해제 누계에 포함한다.
                    byte[] directoryData = limit(archive, Math.min(MAX_IMAGE_BYTES, MAX_EXPANDED_BYTES - unpacked));
                    if (directoryData.length != 0) {
                        throw invalidArchive();
                    }
                    continue;
                }
                byte[] bytes = limit(archive, Math.min(MAX_IMAGE_BYTES, MAX_EXPANDED_BYTES - unpacked));
                unpacked += bytes.length;
                prepareEntry(job.id, path, bytes, pages, issues);
            }
        } catch (ZipException | IllegalArgumentException error) {
            throw invalidArchive();
        }
        if (count == 0) {
            throw invalidArchive();
        }
    }

    // 유효한 이미지만 불변 추출 객체로 저장한다. 같은 준비 시도는 동일한 객체 키를 사용한다.
    private void prepareEntry(UUID jobId, String path, byte[] bytes, List<Page> pages, List<InputIssue> issues)
            throws IOException {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String type = extensionType(name);
        if (type == null) {
            if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                throw invalidArchive();
            }
            issues.add(new InputIssue(name, "UNSUPPORTED_MEDIA_TYPE", null));
            return;
        }
        ImageInfo image;
        try {
            image = inspect(bytes, type);
        } catch (WorkFailure error) {
            issues.add(new InputIssue(name, error.failure.code(), null));
            return;
        }

        // 경로는 정렬 표시와 해시 입력에만 사용한다. 객체 식별자는 경로 문자와 무관하다.
        if (pages.size() >= MAX_PAGES) {
            throw inputLimit();
        }
        UUID objectId = UUID.nameUUIDFromBytes((jobId + ":zip:" + path).getBytes(StandardCharsets.UTF_8));
        assets.put(jobId, objectId, new ByteArrayInputStream(bytes), bytes.length);
        pages.add(newPage(objectId, path, image, bytes));
    }

    // ZIP 내 경로의 모든 요소를 검증한다. 상대 경로를 로컬 경로로 사용하지 않는다.
    private static String zipName(String original, boolean directory) {
        String path = Normalizer.normalize(original, Normalizer.Form.NFC);
        if (directory && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.length() > 255 || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) {
            throw invalidArchive();
        }
        for (String component : path.split("/", -1)) {
            if (!safeFileName(component)) {
                throw invalidArchive();
            }
        }
        return path;
    }

    // 사용자가 붙인 이름을 표시 metadata로만 제한한다.
    static boolean safeFileName(String value) {
        return value != null && !value.isBlank() && value.length() <= 255 && !value.equals(".") && !value.equals("..")
                && value.indexOf('/') < 0 && value.indexOf('\\') < 0 && value.indexOf(':') < 0
                && value.chars().noneMatch(Character::isISOControl);
    }

    // 헤더 실측 크기를 제한한 뒤 PNG/JPEG의 이미지 디코딩을 확인한다.
    static ImageInfo inspect(byte[] bytes, String declaredType) {
        try {
            boolean png = starts(bytes, 0, PNG_SIGNATURE);
            boolean jpeg = bytes.length > 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
            boolean webp = marker(bytes, 0, "RIFF") && marker(bytes, 8, "WEBP");
            String type = png ? "image/png" : jpeg ? "image/jpeg" : webp ? "image/webp" : null;
            if (type == null || (declaredType != null && !declaredType.equals(type))) {
                throw new WorkFailure("UNSUPPORTED_MEDIA_TYPE", Stage.VALIDATE, false);
            }

            // 정지 WebP는 JDK에 없는 디코더 대신 컨테이너 헤더를 확인하고 AI에서 본문을 해독한다.
            if (webp) {
                return webpInfo(bytes);
            }
            int orientation = png ? pngOrientation(bytes) : jpegOrientation(bytes);
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) {
                    throw invalidImage();
                }
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    ImageInfo image = dimensions(type, reader.getWidth(0), reader.getHeight(0), orientation);
                    reader.read(0).flush();
                    return image;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IndexOutOfBoundsException error) {
            throw invalidImage();
        }
    }

    // PNG의 APNG 표시를 거절하고 선택적 EXIF 방향을 읽는다.
    private static int pngOrientation(byte[] bytes) {
        int orientation = 1;
        int offset = 8;
        while (offset <= bytes.length - 12) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            if (length < 0 || length > bytes.length - offset - 12) {
                throw invalidImage();
            }
            if (marker(bytes, offset + 4, "acTL")) {
                throw new WorkFailure("UNSUPPORTED_MEDIA_TYPE", Stage.VALIDATE, false);
            }
            if (marker(bytes, offset + 4, "eXIf")) {
                orientation = exifOrientation(bytes, offset + 8, length);
            }
            if (marker(bytes, offset + 4, "IEND")) {
                return orientation;
            }
            offset += length + 12;
        }
        throw invalidImage();
    }

    // JPEG 시작 구역의 APP1 EXIF 방향을 읽는다.
    private static int jpegOrientation(byte[] bytes) {
        int offset = 2;
        while (offset + 4 <= bytes.length && (bytes[offset] & 255) == 255) {
            int marker = bytes[offset + 1] & 255;
            if (marker == 218 || marker == 217) {
                break;
            }
            int length = ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
            if (length < 2 || length > bytes.length - offset - 2) {
                throw invalidImage();
            }
            if (marker == 225 && length >= 8 && marker(bytes, offset + 4, "Exif")
                    && bytes[offset + 8] == 0 && bytes[offset + 9] == 0) {
                return exifOrientation(bytes, offset + 10, length - 8);
            }
            offset += length + 2;
        }
        return 1;
    }

    // WebP의 정지 프레임 타입과 방향을 컨테이너 한도 안에서 검증한다.
    private static ImageInfo webpInfo(byte[] bytes) {
        int width = 0;
        int height = 0;
        int orientation = 1;
        boolean frame = false;
        int offset = 12;
        if (uint32le(bytes, 4) != bytes.length - 8L) {
            throw invalidImage();
        }
        while (offset <= bytes.length - 8) {
            long size = uint32le(bytes, offset + 4);
            int start = offset + 8;
            if (size > bytes.length - start) {
                throw invalidImage();
            }
            if (marker(bytes, offset, "ANIM") || marker(bytes, offset, "ANMF")) {
                throw new WorkFailure("UNSUPPORTED_MEDIA_TYPE", Stage.VALIDATE, false);
            }
            if (marker(bytes, offset, "VP8X") && size >= 10) {
                if ((bytes[start] & 2) != 0) {
                    throw new WorkFailure("UNSUPPORTED_MEDIA_TYPE", Stage.VALIDATE, false);
                }
                width = 1 + uint24le(bytes, start + 4);
                height = 1 + uint24le(bytes, start + 7);
            } else if (marker(bytes, offset, "VP8L") && size >= 5 && (bytes[start] & 255) == 47) {
                long info = uint32le(bytes, start + 1);
                if (width == 0) {
                    width = 1 + (int) (info & 16383);
                    height = 1 + (int) ((info >> 14) & 16383);
                }
                frame = true;
            } else if (marker(bytes, offset, "VP8 ") && size >= 10
                    && (bytes[start + 3] & 255) == 157 && (bytes[start + 4] & 255) == 1
                    && (bytes[start + 5] & 255) == 42) {
                if (width == 0) {
                    width = ((bytes[start + 6] & 255) | ((bytes[start + 7] & 255) << 8)) & 16383;
                    height = ((bytes[start + 8] & 255) | ((bytes[start + 9] & 255) << 8)) & 16383;
                }
                frame = true;
            } else if (marker(bytes, offset, "EXIF")) {
                orientation = exifOrientation(bytes, start, (int) size);
            }
            offset = start + (int) size + (int) (size & 1);
        }
        if (!frame || offset != bytes.length) {
            throw invalidImage();
        }
        return dimensions("image/webp", width, height, orientation);
    }

    // TIFF IFD0의 방향만 읽으며 다른 EXIF 내용은 처리하지 않는다.
    private static int exifOrientation(byte[] bytes, int start, int length) {
        if (length < 8) {
            return 1;
        }
        if (marker(bytes, start, "Exif") && length >= 14) {
            start += 6;
            length -= 6;
        }
        ByteBuffer tiff = ByteBuffer.wrap(bytes, start, length).slice();
        if (tiff.get(0) == 'I' && tiff.get(1) == 'I') {
            tiff.order(ByteOrder.LITTLE_ENDIAN);
        } else if (tiff.get(0) != 'M' || tiff.get(1) != 'M') {
            return 1;
        }
        int directory = tiff.getInt(4);
        if (tiff.getShort(2) != 42 || directory < 8 || directory > length - 2) {
            return 1;
        }
        int count = tiff.getShort(directory) & 65535;
        for (int index = 0; index < count; index++) {
            int entry = directory + 2 + index * 12;
            if (entry > length - 12) {
                break;
            }
            if (tiff.getShort(entry) == 274 && tiff.getShort(entry + 2) == 3 && tiff.getInt(entry + 4) == 1) {
                return tiff.getShort(entry + 8);
            }
        }
        return 1;
    }

    // 회전 보정 이후의 너비, 높이 및 픽셀 한도에 맞는지 검사한다.
    private static ImageInfo dimensions(String type, int width, int height, int orientation) {
        if (orientation >= 5 && orientation <= 8) {
            return dimensions(type, height, width, 1);
        }
        if (width < 1 || height < 1 || width > 4096 || height > 24_000 || (long) width * height > 20_000_000L) {
            throw inputLimit();
        }
        return new ImageInfo(type, width, height);
    }

    // 고정된 최대 길이만 읽으며 추가 바이트가 있으면 전체 작업을 제한한다.
    static byte[] limit(InputStream source, int max) throws IOException {
        byte[] bytes = source.readNBytes(max + 1);
        if (bytes.length > max) {
            throw inputLimit();
        }
        return bytes;
    }

    // 표시 이름과 불변 원본의 식별자를 새 페이지에 연결한다.
    static Page newPage(UUID objectId, String name, ImageInfo image, byte[] bytes) {
        Page page = new Page();
        page.id = UUID.randomUUID();
        page.objectId = objectId;
        page.fileName = name;
        page.image = image;
        page.sha256 = hash(bytes);
        return page;
    }

    // 숫자 구간은 정수, 나머지는 대소문자 구분 없이 비교한다.
    private static int naturalOrder(String left, String right) {
        var leftWords = SORT_WORD.matcher(left);
        var rightWords = SORT_WORD.matcher(right);
        while (leftWords.find()) {
            if (!rightWords.find()) {
                return 1;
            }
            String leftWord = leftWords.group();
            String rightWord = rightWords.group();
            boolean numeric = Character.isDigit(leftWord.charAt(0)) && Character.isDigit(rightWord.charAt(0));
            int comparison = numeric
                    ? new BigInteger(leftWord).compareTo(new BigInteger(rightWord))
                    : leftWord.compareToIgnoreCase(rightWord);
            if (comparison != 0) {
                return comparison;
            }
        }
        return rightWords.find() ? -1 : 0;
    }

    // 확장자는 표시 후보를 빠르게 걸러 내는 데만 사용한다.
    private static String extensionType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpeg") || lower.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    private static long uint32le(byte[] bytes, int start) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes, start, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static int uint24le(byte[] bytes, int start) {
        return (bytes[start] & 255) | ((bytes[start + 1] & 255) << 8) | ((bytes[start + 2] & 255) << 16);
    }

    private static boolean marker(byte[] bytes, int offset, String marker) {
        return starts(bytes, offset, marker.getBytes(StandardCharsets.US_ASCII));
    }

    // 길이가 짧은 잘못된 입력에서도 범위 밖 참조를 피한다.
    private static boolean starts(byte[] bytes, int offset, byte[] prefix) {
        return offset >= 0 && offset <= bytes.length - prefix.length
                && java.util.Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static WorkFailure invalidArchive() {
        return new WorkFailure("INVALID_ARCHIVE", Stage.VALIDATE, false);
    }

    private static WorkFailure invalidImage() {
        return new WorkFailure("INVALID_IMAGE", Stage.VALIDATE, false);
    }

    private static WorkFailure inputLimit() {
        return new WorkFailure("INPUT_LIMIT_EXCEEDED", Stage.VALIDATE, false);
    }
}
