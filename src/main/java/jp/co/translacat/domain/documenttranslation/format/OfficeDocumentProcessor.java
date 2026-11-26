package jp.co.translacat.domain.documenttranslation.format;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 허용된 번역 텍스트와 해당 표시 속성을 대체 사본에 반영하고 Office 패키지를 다시 검사한다. */
public final class OfficeDocumentProcessor implements DocumentFormatPort {
    @Override
    public Plan read(Path source, String extension) throws IOException {
        OfficePackageInspector.Inspection inspection = OfficePackageInspector.inspect(source, extension);
        String format = extension.toLowerCase(Locale.ROOT);

        // 다시 열었을 때 같은 원본과 같은 조각을 확인할 수 있도록 해시와 읽기 결과를 저장한다.
        byte[] sourceDigest = digest(source);
        try (OfficeParts parts = new OfficeParts(source)) {
            OfficeTextPlan plan = analyze(parts, format, inspection.mainPart());
            return new Snapshot(source.toAbsolutePath(), format, sourceDigest, plan.segments(), plan.warnings());
        }
    }

    @Override
    public List<Warning> write(Plan plan, Map<String, String> translatedPieces, Path output) throws IOException {
        if (!(plan instanceof Snapshot snapshot) || output == null
                || !Arrays.equals(snapshot.digest(), digest(snapshot.source()))) {
            throw new IOException("Office translation plan does not match the source");
        }
        Path destination = output.toAbsolutePath().normalize();
        if (snapshot.source().normalize().equals(destination)
                || Files.exists(destination) && Files.isSameFile(snapshot.source(), destination)) {
            throw new IOException("Office translation output must differ from the source");
        }

        // 작업자의 출력 위치에 완성되지 않은 파일을 공개하지 않도록 같은 디렉터리의 사본을 사용한다.
        Path staged = Files.createTempFile(destination.getParent(), ".office-output-", ".zip");
        try {
            OfficePackageInspector.Inspection original = OfficePackageInspector.inspect(snapshot.source(),
                    snapshot.format());
            List<Warning> warnings;
            try (OfficeParts parts = new OfficeParts(snapshot.source())) {
                OfficeTextPlan current = analyze(parts, snapshot.format(), original.mainPart());
                if (!current.segments().equals(snapshot.segments()) || !current.warnings().equals(snapshot.warnings())) {
                    throw new IOException("Office translation coordinates changed");
                }
                warnings = current.apply(translatedPieces);
                parts.write(staged);
            }

            // 주 파트와 항목 수가 같고 재열기 검사를 통과한 사본만 최종 경로로 전달한다.
            OfficePackageInspector.Inspection result = OfficePackageInspector.inspect(staged, snapshot.format());
            if (!original.equals(result)) {
                throw new IOException("Office package identity changed");
            }
            try {
                Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return warnings;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    // 패키지 관계 정책을 적용하고 확장자에 맞는 허용된 위치만 찾는다.
    private static OfficeTextPlan analyze(OfficeParts parts, String format, String main) throws IOException {
        parts.validateRelationships();
        OfficeTextPlan plan = new OfficeTextPlan();
        switch (format) {
            case "docx" -> WordText.collect(parts, plan, main);
            case "pptx" -> SlideText.collect(parts, plan, main);
            case "xlsx" -> WorkbookText.collect(parts, plan, main);
            default -> throw new IOException("Unsupported Office file extension");
        }
        return plan;
    }

    // 공급자 응답을 기다리는 동안 원본이 바뀌었는지 확인할 수 있도록 압축 파일을 해시한다.
    private static byte[] digest(Path source) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK SHA-256 unavailable", exception);
        }

        try (var input = Files.newInputStream(source)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            long read = 0;
            while ((count = input.read(buffer)) != -1) {
                read += count;
                if (read > 10L * 1024 * 1024) {
                    throw new IOException("Office package compressed size is out of bounds");
                }
                digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private record Snapshot(Path source, String format, byte[] digest, List<Segment> segments,
                            List<Warning> warnings) implements Plan {
    }
}
