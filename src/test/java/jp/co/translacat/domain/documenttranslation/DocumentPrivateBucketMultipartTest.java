package jp.co.translacat.domain.documenttranslation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DocumentPrivateBucketMultipartTest {
    private static final String BUCKET = "synthetic-private-documents";
    private static final String KEY = "source-00000000-0000-4000-8000-000000000001";
    private static final String UPLOAD_ID = "synthetic-multipart-upload";
    private static final String WRITE_ID = "document-write-id";
    private static final String PART_ETAG = "\"synthetic-part\"";

    @TempDir
    Path temporary;

    private Path source;
    private SimulatedBucket server;
    private DocumentFiles.PrivateBucket bucket;

    // 실제 문서 없이 파일 한 조각과 모의 S3를 준비한다.
    @BeforeEach
    void prepareSyntheticObject() throws IOException {
        source = temporary.resolve("synthetic.xlsx");
        Files.writeString(source, "synthetic office bytes");
        server = new SimulatedBucket(KEY);
        bucket = new DocumentFiles.PrivateBucket(server.client(), BUCKET);
    }

    // 보드의 객체 키가 문서 포트의 조회, 쓰기 또는 정리 경계에서 차단되는지 확인한다.
    @Test
    void rejectsBoardKeysBeforeAnyS3Operation() {
        String board = "boards/00000000-0000-4000-8000-000000000004";
        S3Client client = mock(S3Client.class);
        var documentBucket = new DocumentFiles.PrivateBucket(client, BUCKET);

        // 실행: 포트 기본 구현을 거치는 경로를 포함해 모든 저장소 진입점을 호출한다.
        List<Executable> attempts = List.of(
                () -> documentBucket.open(board),
                () -> documentBucket.delete(board),
                () -> documentBucket.exists(board),
                () -> documentBucket.createMultipart(board),
                () -> documentBucket.writeMultipart(board, UPLOAD_ID, source),
                () -> documentBucket.abortMultipart(board, UPLOAD_ID),
                () -> documentBucket.reconcileMultipart(board, UPLOAD_ID),
                () -> documentBucket.put(board, source),
                () -> documentBucket.put(board, source, () -> true),
                () -> documentBucket.reconcile(board, () -> fail("Unexpected reconciliation callback"))
        );
        for (Executable attempt : attempts) {
            IOException invalid = assertThrows(IOException.class, attempt);
            assertEquals("Invalid document object key", invalid.getMessage());
        }

        // 검증: 다른 기능의 객체는 S3에 요청하기 전에 일관되게 거부된다.
        verifyNoInteractions(client);
    }

    // 원본, 결과, 배치 및 재시도가 공유하는 선행 원본 키는 기존 객체 경로를 쓴다.
    @Test
    void preservesSupportedDocumentAndRetrySourceKeys() throws IOException {
        String job = "00000000-0000-4000-8000-000000000001";
        String lease = "00000000-0000-4000-8000-000000000002";
        String retrySource = "source-00000000-0000-4000-8000-000000000003";
        List<String> keys = List.of(KEY, "result-" + job + "-" + lease, "batch-" + job + "-" + lease + "-0",
                retrySource);
        byte[] expected = Files.readAllBytes(source);

        for (String key : keys) {
            var provider = new SimulatedBucket(key);
            var documentBucket = new DocumentFiles.PrivateBucket(provider.client(), BUCKET);

            // 실행: 파일을 저장해 조회하고 해당 키에 속한 객체만 지운다.
            String upload = documentBucket.createMultipart(key);
            documentBucket.writeMultipart(key, upload, source);
            assertTrue(documentBucket.exists(key));
            try (var opened = documentBucket.open(key)) {
                assertArrayEquals(expected, opened.readAllBytes());
            }
            documentBucket.delete(key);

            // 검증: 유효한 키가 모의 제공자에 그대로 전달되고 삭제 후 조회에서 비어 있다.
            assertEquals(List.of("create", "part", "complete", "head", "head", "get", "delete"), provider.calls);
            assertFalse(documentBucket.exists(key));
        }
    }

    // 정상 응답 경로에서도 하나의 멀티파트 객체에 원본 바이트와 쓰기 표식이 이어진다.
    @Test
    void completesAFileWithItsDurableOwnershipMetadata() throws IOException {
        String upload = bucket.createMultipart(KEY);

        // 실행: 동일한 업로드 ID의 part를 끝까지 저장한다.
        bucket.writeMultipart(KEY, upload, source);

        // 검증: 물리적 객체가 현재 시도의 데이터와 소유권을 담는다.
        assertArrayEquals(Files.readAllBytes(source), server.object.bytes());
        assertEquals(Map.of(WRITE_ID, KEY), server.object.metadata());
        assertFalse(server.uploadOpen);
    }

    // 완료 요청이 저장소에 반영되지 않은 경우에도 삭제가 진행될 수 있어야 한다.
    @Test
    void closesUploadWhenCompletionNeverArrived() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.completion = Completion.NEVER_ARRIVES;
        assertThrows(IOException.class, () -> bucket.writeMultipart(KEY, upload, source));

        // 성공 Abort와 정확한 업로드 ID 조회가 부재를 확인한 뒤에만 객체를 확인한다.
        int cleanupStarted = server.calls.size();
        bucket.reconcileMultipart(KEY, upload);

        assertEquals(List.of("abort", "list", "head"), server.calls.subList(cleanupStarted, server.calls.size()));
        assertFalse(server.uploadOpen);
        assertNull(server.object);
    }

    // 클라이언트가 타임아웃을 받은 뒤 도착한 Complete에도 폐쇄된 업로드 ID를 적용한다.
    @Test
    void lateCompletionCannotRecreateAnObjectAfterReconciliation() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.completion = Completion.ARRIVES_LATER;
        assertThrows(IOException.class, () -> bucket.writeMultipart(KEY, upload, source));
        CompleteMultipartUploadRequest delayed = server.delayedComplete;
        assertNotNull(delayed);

        // 정리가 끝난 다음 동일 저장소 요청을 전달해 이미 폐쇄된 ID의 동작을 본다.
        bucket.reconcileMultipart(KEY, upload);
        S3Exception closed = assertThrows(S3Exception.class, () -> server.complete(delayed));

        assertEquals("NoSuchUpload", closed.awsErrorDetails().errorCode());
        assertNull(server.object);
    }

    // 저장소에는 이미 커밋됐지만 클라이언트 응답이 유실된 경우 확인된 소유 객체를 삭제한다.
    @Test
    void deletesAnOwnedObjectWhenCompletionWonBeforeTheAbort() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.completion = Completion.COMMITTED_WITHOUT_ACK;
        assertThrows(IOException.class, () -> bucket.writeMultipart(KEY, upload, source));
        assertArrayEquals(Files.readAllBytes(source), server.object.bytes());
        int cleanupStarted = server.calls.size();

        // Abort가 NoSuchUpload를 반환해도 정확한 조회로 종료를 재확인하고 소유권을 검사한다.
        bucket.reconcileMultipart(KEY, upload);

        List<String> cleanup = server.calls.subList(cleanupStarted, server.calls.size());
        assertEquals(List.of("abort", "list", "head", "delete"), cleanup.subList(0, 4));
        assertNull(server.object);
    }

    // 일부 part가 저장소에 늦게 반영돼도 폐쇄된 업로드의 완료에는 사용할 수 없다.
    @Test
    void latePartCannotCompleteAClosedUpload() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.delayPart = true;
        assertThrows(IOException.class, () -> bucket.writeMultipart(KEY, upload, source));
        assertNotNull(server.delayedPart);
        assertFalse(server.calls.contains("complete"));

        // part는 저장소에 늦게 도착할 수 있게 하되, 완료 요청은 같은 폐쇄된 ID에 대해 평가한다.
        bucket.reconcileMultipart(KEY, upload);
        server.acceptDelayedPart();
        var completed = CompletedPart.builder().partNumber(1).eTag(PART_ETAG).build();
        var previousUpload = CompleteMultipartUploadRequest.builder()
                .bucket(BUCKET).key(KEY).uploadId(upload).ifNoneMatch("*")
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build();
        S3Exception closed = assertThrows(S3Exception.class, () -> server.complete(previousUpload));

        assertEquals("NoSuchUpload", closed.awsErrorDetails().errorCode());
        assertNull(server.object);
    }

    // Abort 성공 응답만으로는 아직 조회 가능한 업로드나 다른 404 원인을 종료로 처리하지 않는다.
    @Test
    void requiresExactNoSuchUploadAfterAbort() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.keepOpenAfterAbort = true;
        assertThrows(IOException.class, () -> bucket.reconcileMultipart(KEY, upload));
        assertEquals(List.of("create", "abort", "list"), server.calls);

        // 접근·가용성 실패 및 NoSuchUpload와 다른 404에서 객체 조회·삭제를 시작하지 않는다.
        for (S3Exception failure : List.of(error(403, "AccessDenied"), error(503, "SlowDown"),
                error(404, "NoSuchBucket"))) {
            server.listFailure = failure;
            assertThrows(IOException.class, () -> bucket.reconcileMultipart(KEY, upload));
        }

        assertFalse(server.calls.contains("head"));
        assertFalse(server.calls.contains("delete"));
    }

    // 업로드가 폐쇄된 상태여도 소유권 표식이 없는 객체는 삭제하지 않는다.
    @Test
    void preservesObjectsWithForeignOrMissingWriteMetadata() throws IOException {
        String upload = bucket.createMultipart(KEY);
        server.object = new StoredObject(new byte[] {1}, Map.of(WRITE_ID, "different-write"));

        // 같은 확인을 표식 불일치와 표식 부재 모두에 적용한다.
        assertThrows(IOException.class, () -> bucket.reconcileMultipart(KEY, upload));
        assertArrayEquals(new byte[] {1}, server.object.bytes());
        server.object = new StoredObject(new byte[] {2}, Map.of());
        assertThrows(IOException.class, () -> bucket.reconcileMultipart(KEY, upload));

        assertArrayEquals(new byte[] {2}, server.object.bytes());
        assertFalse(server.calls.contains("delete"));
    }

    // 정확한 업로드 ID가 닫힌 뒤에도 HEAD 오류는 확인 가능한 부재로 간주하지 않는다.
    @Test
    void headErrorsKeepReconciliationRetryable() throws IOException {
        String upload = bucket.createMultipart(KEY);
        for (S3Exception failure : List.of(error(403, "AccessDenied"), error(503, "SlowDown"))) {
            server.headFailure = failure;

            // 실행 및 검증: 해당 객체의 상태를 확인할 수 없으므로 삭제하지 않는다.
            assertThrows(IOException.class, () -> bucket.reconcileMultipart(KEY, upload));
            assertFalse(server.calls.contains("delete"));
        }
    }

    // 생성 응답이 모호할 때 원본 파일의 part 전송으로 넘어갈 수 있는 ID가 반환되지 않는다.
    @Test
    void ambiguousCreateNeverSendsDocumentBytes() {
        server.loseCreateResponse = true;

        assertThrows(IOException.class, () -> bucket.createMultipart(KEY));

        assertEquals(List.of("create"), server.calls);
        assertNull(server.part);
        assertNull(server.object);
    }

    // 테스트에 필요한 제공자 상태 코드와 원인만 재현한다.
    private static S3Exception error(int status, String code) {
        S3Exception.Builder response = S3Exception.builder();
        response.statusCode(status);
        response.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build());
        response.message("Synthetic provider response");
        return (S3Exception) response.build();
    }

    private enum Completion {
        NORMAL,
        NEVER_ARRIVES,
        ARRIVES_LATER,
        COMMITTED_WITHOUT_ACK
    }

    private record StoredObject(byte[] bytes, Map<String, String> metadata) {
    }

    // 요청 접수와 실제 저장 순서를 분리해 클라이언트 중단 뒤의 제공자 동작을 재현한다.
    private static final class SimulatedBucket {
        private final String expectedKey;
        private final List<String> calls = new ArrayList<>();
        private Completion completion = Completion.NORMAL;
        private Map<String, String> metadata;
        private byte[] part;
        private byte[] delayedPart;
        private CompleteMultipartUploadRequest delayedComplete;
        private StoredObject object;
        private boolean uploadOpen;
        private boolean delayPart;
        private boolean keepOpenAfterAbort;
        private boolean loseCreateResponse;
        private S3Exception listFailure;
        private S3Exception headFailure;

        private SimulatedBucket(String expectedKey) {
            this.expectedKey = expectedKey;
        }

        // AWS의 람다 편의 오버로드가 구체 요청 객체를 만들고 모의 요청으로 전달되게 한다.
        private S3Client client() {
            S3Client api = mock(S3Client.class, CALLS_REAL_METHODS);
            doAnswer(call -> create(call.getArgument(0))).when(api)
                    .createMultipartUpload(any(CreateMultipartUploadRequest.class));
            doAnswer(call -> stage(call.getArgument(0), call.getArgument(1))).when(api)
                    .uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
            doAnswer(call -> completeWithTransportOutcome(call.getArgument(0))).when(api)
                    .completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
            doAnswer(call -> abort(call.getArgument(0))).when(api)
                    .abortMultipartUpload(any(AbortMultipartUploadRequest.class));
            doAnswer(call -> list(call.getArgument(0))).when(api).listParts(any(ListPartsRequest.class));
            doAnswer(call -> head(call.getArgument(0))).when(api).headObject(any(HeadObjectRequest.class));
            doAnswer(call -> get(call.getArgument(0))).when(api).getObject(any(GetObjectRequest.class));
            doAnswer(call -> delete(call.getArgument(0))).when(api).deleteObject(any(DeleteObjectRequest.class));
            return api;
        }

        // 생성 시 받은 문서 쓰기 표식은 해당 멀티파트 완료 객체에 이어진다.
        private CreateMultipartUploadResponse create(CreateMultipartUploadRequest request) {
            assertTarget(request.bucket(), request.key());
            calls.add("create");
            assertEquals(expectedKey, request.metadata().get(WRITE_ID));
            metadata = Map.copyOf(request.metadata());
            uploadOpen = true;
            if (loseCreateResponse) {
                throw SdkClientException.create("Synthetic create timeout");
            }
            return CreateMultipartUploadResponse.builder().uploadId(UPLOAD_ID).build();
        }

        // 첫 part의 전송과 클라이언트가 받는 응답을 따로 제어한다.
        private UploadPartResponse stage(UploadPartRequest request, RequestBody body) throws IOException {
            assertUpload(request.bucket(), request.key(), request.uploadId());
            assertEquals(1, request.partNumber());
            calls.add("part");
            try (var stream = body.contentStreamProvider().newStream()) {
                if (delayPart) {
                    delayedPart = stream.readAllBytes();
                    throw SdkClientException.create("Synthetic part timeout");
                }
                part = stream.readAllBytes();
            }
            return UploadPartResponse.builder().eTag(PART_ETAG).build();
        }

        // 완료 요청의 응답이 유실되더라도 저장소 커밋 순서는 독립적이다.
        private CompleteMultipartUploadResponse completeWithTransportOutcome(CompleteMultipartUploadRequest request) {
            assertUpload(request.bucket(), request.key(), request.uploadId());
            assertEquals("*", request.ifNoneMatch());
            assertEquals(1, request.multipartUpload().parts().size());
            assertEquals(PART_ETAG, request.multipartUpload().parts().getFirst().eTag());
            calls.add("complete");
            return switch (completion) {
                case NORMAL -> complete(request);
                case NEVER_ARRIVES -> throw SdkClientException.create("Synthetic completion timeout");
                case ARRIVES_LATER -> {
                    delayedComplete = request;
                    throw SdkClientException.create("Synthetic completion timeout");
                }
                case COMMITTED_WITHOUT_ACK -> {
                    complete(request);
                    throw SdkClientException.create("Synthetic completion timeout");
                }
            };
        }

        // 업로드가 아직 열려 있고 키가 비어 있는 경우에만 완료와 저장을 한 단계로 처리한다.
        private CompleteMultipartUploadResponse complete(CompleteMultipartUploadRequest request) {
            assertUpload(request.bucket(), request.key(), request.uploadId());
            if (!uploadOpen) {
                throw error(404, "NoSuchUpload");
            }
            if (object != null) {
                throw error(412, "PreconditionFailed");
            }
            assertNotNull(part);
            object = new StoredObject(part, metadata);
            uploadOpen = false;
            return CompleteMultipartUploadResponse.builder().eTag("\"synthetic-object\"").build();
        }

        // 늦은 part가 도착하더라도 종료된 업로드의 세션 상태를 되돌리지 않는다.
        private void acceptDelayedPart() {
            part = delayedPart;
            delayedPart = null;
        }

        // 이미 끝난 업로드에 대한 Abort와 성공 응답 후 남은 업로드를 별도로 표현한다.
        private AbortMultipartUploadResponse abort(AbortMultipartUploadRequest request) {
            assertUpload(request.bucket(), request.key(), request.uploadId());
            calls.add("abort");
            if (!uploadOpen) {
                throw error(404, "NoSuchUpload");
            }
            uploadOpen = keepOpenAfterAbort;
            return AbortMultipartUploadResponse.builder().build();
        }

        // 정확한 업로드 ID의 종료는 NoSuchUpload로 확인한다.
        private ListPartsResponse list(ListPartsRequest request) {
            assertUpload(request.bucket(), request.key(), request.uploadId());
            calls.add("list");
            if (listFailure != null) {
                throw listFailure;
            }
            if (!uploadOpen) {
                throw error(404, "NoSuchUpload");
            }
            return ListPartsResponse.builder().uploadId(UPLOAD_ID).build();
        }

        // HEAD는 현재 객체의 저장 길이와 쓰기 표식을 노출한다.
        private HeadObjectResponse head(HeadObjectRequest request) {
            assertTarget(request.bucket(), request.key());
            calls.add("head");
            if (headFailure != null) {
                throw headFailure;
            }
            if (object == null) {
                throw error(404, "NoSuchKey");
            }
            return HeadObjectResponse.builder().contentLength((long) object.bytes().length)
                    .metadata(object.metadata()).build();
        }

        // 해당 키에 저장된 파일만 열어 호출자가 전체 바이트를 비교하도록 한다.
        private ResponseInputStream<GetObjectResponse> get(GetObjectRequest request) {
            assertTarget(request.bucket(), request.key());
            calls.add("get");
            if (object == null) {
                throw error(404, "NoSuchKey");
            }
            long length = object.bytes().length;
            return new ResponseInputStream<>(GetObjectResponse.builder().contentLength(length).build(),
                    new ByteArrayInputStream(object.bytes()));
        }

        // 현재 테스트 키만 삭제하도록 확인한다.
        private DeleteObjectResponse delete(DeleteObjectRequest request) {
            assertTarget(request.bucket(), request.key());
            calls.add("delete");
            object = null;
            return DeleteObjectResponse.builder().build();
        }

        // 각 작업이 테스트 버킷과 저장소 키를 넘어서지 않는지 확인한다.
        private void assertTarget(String bucket, String key) {
            assertEquals(BUCKET, bucket);
            assertEquals(expectedKey, key);
        }

        // 차단에 사용한 업로드 ID가 실제 파일 전송과 일치하는지 확인한다.
        private void assertUpload(String bucket, String key, String upload) {
            assertTarget(bucket, key);
            assertEquals(UPLOAD_ID, upload);
        }
    }
}
