package jp.co.translacat.domain.support;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.entity.*;
import jp.co.translacat.domain.support.enums.*;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.repository.*;
import jp.co.translacat.domain.support.service.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:support-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        QueryDslConfig.class,
        SupportAccessService.class,
        SupportInquiryService.class,
        SupportNoticeService.class,
        SupportMessageService.class,
        SupportNotificationService.class
})
class SupportPersistenceTest {

    @Autowired UserRepository users;
    @Autowired SupportInquiryRepository inquiries;
    @Autowired SupportReplyRepository replies;
    @Autowired SupportNoticeRepository notices;
    @Autowired SupportNoticeDismissalRepository dismissals;
    @Autowired SupportMessageRepository messages;
    @Autowired SupportNotificationRepository notifications;
    @Autowired SupportInquiryService inquiryService;
    @Autowired SupportNoticeService noticeService;
    @Autowired SupportMessageService messageService;
    @Autowired SupportNotificationService notificationService;
    @Autowired PlatformTransactionManager transactionManager;

    User owner;
    User stranger;
    User admin;

    @BeforeEach
    void fixture() {
        owner = user(Role.USER);
        stranger = user(Role.USER);
        admin = user(Role.ADMIN);
    }

    @AfterEach
    void cleanupCommittedFixtures() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        // 동시성 검증만 독립 트랜잭션을 사용하므로 해당 fixture를 명시적으로 정리한다.
        transaction().executeWithoutResult(status -> {
            notifications.deleteAllInBatch();
            replies.deleteAllInBatch();
            inquiries.deleteAllInBatch();
            messages.deleteAllInBatch();
            dismissals.deleteAllInBatch();
            notices.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test
    void privateInquiryIsVisibleOnlyToOwnerAndAdmin() {
        // 준비
        InquiryResponseDto inquiry = createInquiry(owner);

        // 실행
        var ownerList = inquiryService.list(owner.getId(), null, "", 0, 20);
        var strangerList = inquiryService.list(stranger.getId(), null, "", 0, 20);
        var adminList = inquiryService.list(admin.getId(), null, "", 0, 20);

        // 검증: 목록과 상세 모두 서버 소유권을 적용한다.
        assertThat(ownerList.items()).extracting(InquiryResponseDto::id).containsExactly(inquiry.id());
        assertThat(strangerList.items()).isEmpty();
        assertThat(adminList.items()).extracting(InquiryResponseDto::id).containsExactly(inquiry.id());
        assertThatThrownBy(() -> inquiryService.detail(stranger.getId(), inquiry.id()))
                .isInstanceOfSatisfying(SupportException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void replyChangesStatusAndDetailSynchronizesUnreadNotification() {
        // 준비
        InquiryResponseDto inquiry = createInquiry(owner);

        // 실행: 관리자의 답변은 해결 상태와 별도로 ANSWERED를 만든다.
        InquiryResponseDto answered = inquiryService.reply(
                admin.getId(), inquiry.id(), new ReplyCreateRequestDto("관리자 답변", null)
        );

        // 검증
        assertThat(answered.status()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(answered.replyCount()).isEqualTo(1);
        assertThat(answered.replies().getFirst().authorRole()).isEqualTo(Role.ADMIN);
        assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
        assertThat(inquiryService.list(owner.getId(), null, "", 0, 20).items().getFirst().unread()).isTrue();

        // 실행: 상세 확인 후 같은 답변 아래에 추가 질문을 남긴다.
        inquiryService.detail(owner.getId(), inquiry.id());
        InquiryResponseDto followup = inquiryService.reply(
                owner.getId(), inquiry.id(),
                new ReplyCreateRequestDto("추가 질문", answered.replies().getFirst().id())
        );

        // 검증
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
        assertThat(followup.status()).isEqualTo(InquiryStatus.WAITING);
        assertThat(followup.replies()).hasSize(2);
        assertThat(followup.replies().getLast().parentId()).isEqualTo(answered.replies().getFirst().id());
        assertThat(notificationService.unreadCount(admin.getId()).count()).isEqualTo(1);
    }

    @Test
    void parentReplyCannotBelongToAnotherInquiry() {
        // 준비
        InquiryResponseDto first = createInquiry(owner);
        InquiryResponseDto second = createInquiry(owner);
        Long parentId = inquiryService.reply(
                admin.getId(), first.id(), new ReplyCreateRequestDto("첫 문의 답변", null)
        ).replies().getFirst().id();

        // 실행 및 검증
        assertThatThrownBy(() -> inquiryService.reply(
                owner.getId(), second.id(), new ReplyCreateRequestDto("잘못된 연결", parentId)
        )).isInstanceOfSatisfying(SupportException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(replies.findByInquiry_IdOrderByIdAsc(second.id())).isEmpty();
    }

    @Test
    void closedInquiryRejectsReplies() {
        // 준비
        InquiryResponseDto inquiry = createInquiry(owner);
        inquiryService.changeStatus(owner.getId(), inquiry.id(), new InquiryStatusRequestDto(InquiryStatus.CLOSED));

        // 실행 및 검증
        assertThatThrownBy(() -> inquiryService.reply(
                admin.getId(), inquiry.id(), new ReplyCreateRequestDto("종료 후 답변", null)
        )).isInstanceOfSatisfying(SupportException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(replies.findByInquiry_IdOrderByIdAsc(inquiry.id())).isEmpty();
    }

    @Test
    void ownerCanReopenClosedInquiryAndResumeConversation() {
        // 준비
        InquiryResponseDto inquiry = createInquiry(owner);
        inquiryService.changeStatus(owner.getId(), inquiry.id(), new InquiryStatusRequestDto(InquiryStatus.CLOSED));

        // 실행
        InquiryResponseDto reopened = inquiryService.changeStatus(
                owner.getId(), inquiry.id(), new InquiryStatusRequestDto(InquiryStatus.WAITING)
        );
        InquiryResponseDto answered = inquiryService.reply(
                admin.getId(), inquiry.id(), new ReplyCreateRequestDto("재개 후 답변", null)
        );

        // 검증
        assertThat(reopened.status()).isEqualTo(InquiryStatus.WAITING);
        assertThat(answered.status()).isEqualTo(InquiryStatus.ANSWERED);
    }

    @Test
    void nonAdminCannotSendMessagesOrWriteNotices() {
        // 준비
        var request = new MessageCreateRequestDto(stranger.getId(), "안내", "개인 메시지");

        // 실행 및 검증
        assertThatThrownBy(() -> messageService.create(owner.getId(), request))
                .isInstanceOfSatisfying(SupportException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(messages.count()).isZero();
        assertThatThrownBy(() -> noticeService.create(
                owner.getId(), new NoticeWriteRequestDto("공지", "본문", NoticeType.URGENT)
        )).isInstanceOf(SupportException.class);
        assertThat(notices.count()).isZero();
    }

    @Test
    void personalMessageReadStateBelongsToRecipient() {
        // 준비
        MessageResponseDto message = messageService.create(
                admin.getId(), new MessageCreateRequestDto(owner.getId(), "안내", "개인 메시지")
        );

        // 실행: 관리자 발송 확인은 상대방의 읽음 상태를 바꾸지 않는다.
        MessageResponseDto adminView = messageService.detail(admin.getId(), message.id());
        var strangerList = messageService.list(stranger.getId(), 0, 20);

        // 검증
        assertThat(adminView.readAt()).isNull();
        assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
        assertThat(strangerList.items()).isEmpty();

        // 실행
        MessageResponseDto ownerView = messageService.detail(owner.getId(), message.id());

        // 검증
        assertThat(ownerView.readAt()).isNotNull();
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
    }

    @Test
    void privateMessageAndRelatedInquiryRejectOtherRecipients() {
        // 준비
        MessageResponseDto message = messageService.create(
                admin.getId(), new MessageCreateRequestDto(owner.getId(), "안내", "개인 메시지")
        );

        // 실행 및 검증
        assertThatThrownBy(() -> messageService.detail(stranger.getId(), message.id()))
                .isInstanceOf(SupportException.class);
        assertThatThrownBy(() -> inquiryService.create(
                stranger.getId(), new InquiryCreateRequestDto(
                        "연결 문의", "본문", InquiryCategory.GENERAL, message.id()
                )
        )).isInstanceOf(SupportException.class);
        assertThat(inquiries.count()).isZero();
    }

    @Test
    void urgentNoticeBroadcastsOnceAndDeletionRemovesLinks() {
        // 준비
        NoticeResponseDto notice = noticeService.create(
                admin.getId(), new NoticeWriteRequestDto("일반 공지", "본문", NoticeType.NORMAL)
        );
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();

        // 실행: 긴급 유형 전환에서만 알리고 단순 수정에는 재발송하지 않는다.
        noticeService.update(admin.getId(), notice.id(), new NoticeWriteRequestDto("긴급 공지", "본문", NoticeType.URGENT));
        noticeService.update(admin.getId(), notice.id(), new NoticeWriteRequestDto("수정 공지", "새 본문", NoticeType.URGENT));

        // 검증
        assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
        assertThat(notificationService.unreadCount(stranger.getId()).count()).isEqualTo(1);
        assertThat(notificationService.unreadCount(admin.getId()).count()).isZero();

        // 실행: 상세 확인과 삭제를 각각 반영한다.
        noticeService.detail(owner.getId(), notice.id());
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
        noticeService.delete(admin.getId(), notice.id());

        // 검증
        assertThat(notifications.count()).isZero();
        assertThat(notices.count()).isZero();
    }

    @Test
    void demotedAdminCannotSeeOldPrivateInquiryNotifications() {
        // 준비
        createInquiry(owner);
        Long notificationId = notificationService.list(admin.getId(), false, 0, 20).items().getFirst().id();
        admin.setAuthority(Role.USER);
        users.saveAndFlush(admin);

        // 실행
        var list = notificationService.list(admin.getId(), false, 0, 20);
        var count = notificationService.unreadCount(admin.getId());

        // 검증: 상세뿐 아니라 제목과 읽지 않은 개수도 숨긴다.
        assertThat(list.items()).isEmpty();
        assertThat(count.count()).isZero();
        assertThatThrownBy(() -> notificationService.markRead(admin.getId(), notificationId))
                .isInstanceOf(SupportException.class);
    }

    @Test
    void notificationsCanOnlyBeMarkedByTheirRecipient() {
        // 준비
        messageService.create(admin.getId(), new MessageCreateRequestDto(owner.getId(), "안내", "본문"));
        Long notificationId = notificationService.list(owner.getId(), false, 0, 20).items().getFirst().id();

        // 실행 및 검증
        assertThatThrownBy(() -> notificationService.markRead(stranger.getId(), notificationId))
                .isInstanceOf(SupportException.class);
        assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
    }

    @Test
    void notificationReadIsIdempotentAndDoesNotResolveInquiry() {
        // 준비
        InquiryResponseDto inquiry = createInquiry(owner);
        inquiryService.reply(admin.getId(), inquiry.id(), new ReplyCreateRequestDto("답변", null));
        Long notificationId = notificationService.list(owner.getId(), false, 0, 20).items().getFirst().id();

        // 실행
        notificationService.markRead(owner.getId(), notificationId);
        notificationService.markRead(owner.getId(), notificationId);

        // 검증
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
        assertThat(inquiries.findById(inquiry.id()).orElseThrow().getStatus()).isEqualTo(InquiryStatus.ANSWERED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void detailWaitsForInFlightReplyAndOnlyThenMarksItRead() throws Exception {
        // 준비: 답변 저장 트랜잭션을 열어 둔 채 상세 조회를 시작한다.
        InquiryResponseDto inquiry = createInquiry(owner);
        CountDownLatch replySaved = new CountDownLatch(1);
        CountDownLatch commitReply = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = executor.submit(() -> transaction().executeWithoutResult(status -> {
                inquiryService.reply(admin.getId(), inquiry.id(), new ReplyCreateRequestDto("동시 답변", null));
                replySaved.countDown();
                await(commitReply);
            }));
            assertThat(replySaved.await(5, TimeUnit.SECONDS)).isTrue();

            Future<InquiryResponseDto> reader = executor.submit(() -> inquiryService.detail(owner.getId(), inquiry.id()));

            // 검증: 미확정 답변이 있는 동안 상세는 반환되지 않는다.
            try {
                assertThatThrownBy(() -> reader.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                commitReply.countDown();
            }

            // 실행 및 검증: 확정된 답변을 실제 반환한 뒤에만 읽음 상태가 된다.
            writer.get(5, TimeUnit.SECONDS);
            assertThat(reader.get(5, TimeUnit.SECONDS).replies()).extracting(ReplyResponseDto::content)
                    .containsExactly("동시 답변");
            assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
        } finally {
            commitReply.countDown();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void replyArrivingAfterDetailStaysUnread() throws Exception {
        // 준비: 상세 확인 트랜잭션이 문의 잠금을 보유한 동안 다음 답변을 시작한다.
        InquiryResponseDto inquiry = createInquiry(owner);
        CountDownLatch detailRead = new CountDownLatch(1);
        CountDownLatch commitDetail = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> reader = executor.submit(() -> transaction().executeWithoutResult(status -> {
                assertThat(inquiryService.detail(owner.getId(), inquiry.id()).replies()).isEmpty();
                detailRead.countDown();
                await(commitDetail);
            }));
            assertThat(detailRead.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> writer = executor.submit(() -> inquiryService.reply(
                    admin.getId(), inquiry.id(), new ReplyCreateRequestDto("나중 답변", null)
            ));

            // 검증: 상세 이후의 답변은 읽음 처리 트랜잭션을 통과한 뒤 저장된다.
            try {
                assertThatThrownBy(() -> writer.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                commitDetail.countDown();
            }

            reader.get(5, TimeUnit.SECONDS);
            writer.get(5, TimeUnit.SECONDS);
            assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
        } finally {
            commitDetail.countDown();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentClosePreventsLateReply() throws Exception {
        // 준비: 종료 트랜잭션이 완료되기 전에 답변 요청을 시작한다.
        InquiryResponseDto inquiry = createInquiry(owner);
        CountDownLatch closed = new CountDownLatch(1);
        CountDownLatch commitClose = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> closer = executor.submit(() -> transaction().executeWithoutResult(status -> {
                inquiryService.changeStatus(
                        owner.getId(), inquiry.id(), new InquiryStatusRequestDto(InquiryStatus.CLOSED)
                );
                closed.countDown();
                await(commitClose);
            }));
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> writer = executor.submit(() -> inquiryService.reply(
                    admin.getId(), inquiry.id(), new ReplyCreateRequestDto("늦은 답변", null)
            ));

            // 실행: 종료 확정 후 대기하던 답변이 상태를 다시 확인한다.
            commitClose.countDown();
            closer.get(5, TimeUnit.SECONDS);

            // 검증
            assertThatThrownBy(() -> writer.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(SupportException.class);
            assertThat(inquiries.findById(inquiry.id()).orElseThrow().getStatus()).isEqualTo(InquiryStatus.CLOSED);
            assertThat(replies.count()).isZero();
        } finally {
            commitClose.countDown();
        }
    }

    @Test
    void pendingUrgentExcludesNormalAndIncludesAdministrators() {
        // 준비
        NoticeResponseDto older = createNotice(NoticeType.URGENT);
        NoticeResponseDto normal = createNotice(NoticeType.NORMAL);
        NoticeResponseDto newer = createNotice(NoticeType.URGENT);

        // 실행
        var pending = noticeService.pendingUrgent(owner.getId());
        var adminPending = noticeService.pendingUrgent(admin.getId());
        var all = noticeService.list(0, 20);

        // 검증: 긴급 팝업 대상은 최신순이며 전체 게시판에는 일반 공지도 남는다.
        assertThat(pending).extracting(NoticeResponseDto::id).containsExactly(newer.id(), older.id());
        assertThat(adminPending).extracting(NoticeResponseDto::id).containsExactly(newer.id(), older.id());
        assertThat(all.items()).extracting(NoticeResponseDto::id)
                .containsExactly(newer.id(), older.id(), normal.id());
        assertThat(all.items()).extracting(NoticeResponseDto::noticeType)
                .containsExactly(NoticeType.URGENT, NoticeType.URGENT, NoticeType.NORMAL);
    }

    @Test
    void dismissalIsPerAccountIdempotentAndIndependentFromNotificationRead() {
        // 준비
        NoticeResponseDto notice = createNotice(NoticeType.URGENT);

        // 실행
        assertThat(noticeService.dismiss(owner.getId(), notice.id())).isTrue();
        assertThat(noticeService.dismiss(owner.getId(), notice.id())).isTrue();

        // 검증: 숨김은 본인 팝업에만 적용되고 공지 게시판과 알림 읽음을 바꾸지 않는다.
        assertThat(dismissals.count()).isEqualTo(1);
        assertThat(noticeService.pendingUrgent(owner.getId())).isEmpty();
        assertThat(noticeService.pendingUrgent(stranger.getId())).extracting(NoticeResponseDto::id)
                .containsExactly(notice.id());
        assertThat(noticeService.list(0, 20).items()).extracting(NoticeResponseDto::id)
                .containsExactly(notice.id());
        assertThat(notificationService.unreadCount(owner.getId()).count()).isEqualTo(1);
    }

    @Test
    void readingNoticeDoesNotPermanentlyDismissIt() {
        // 준비
        NoticeResponseDto notice = createNotice(NoticeType.URGENT);

        // 실행
        noticeService.detail(owner.getId(), notice.id());

        // 검증
        assertThat(notificationService.unreadCount(owner.getId()).count()).isZero();
        assertThat(noticeService.pendingUrgent(owner.getId())).extracting(NoticeResponseDto::id)
                .containsExactly(notice.id());
        assertThat(dismissals.count()).isZero();
    }

    @Test
    void editAndTypeChangesPreservePerNoticeDismissal() {
        // 준비
        NoticeResponseDto original = createNotice(NoticeType.URGENT);
        noticeService.dismiss(owner.getId(), original.id());

        // 실행: 같은 공지 수정이나 유형 재전환으로 사용자의 영구 선택을 취소하지 않는다.
        noticeService.update(
                admin.getId(), original.id(), new NoticeWriteRequestDto("수정", "새 본문", NoticeType.URGENT)
        );
        noticeService.update(
                admin.getId(), original.id(), new NoticeWriteRequestDto("일반 전환", "본문", NoticeType.NORMAL)
        );
        noticeService.update(
                admin.getId(), original.id(), new NoticeWriteRequestDto("긴급 재전환", "본문", NoticeType.URGENT)
        );
        NoticeResponseDto separate = createNotice(NoticeType.URGENT);

        // 검증: 새 공지 ID는 독립적으로 표시한다.
        assertThat(noticeService.pendingUrgent(owner.getId())).extracting(NoticeResponseDto::id)
                .containsExactly(separate.id());
        assertThat(noticeService.pendingUrgent(stranger.getId())).extracting(NoticeResponseDto::id)
                .containsExactly(separate.id(), original.id());
        assertThat(dismissals.count()).isEqualTo(1);
    }

    @Test
    void normalNoticeCanBeDismissedAfterItsPendingResponseWasFetched() {
        // 준비: 팝업 응답 이후 관리자가 일반 공지로 바꾼 상황도 저장할 수 있다.
        NoticeResponseDto notice = createNotice(NoticeType.NORMAL);

        // 실행
        noticeService.dismiss(owner.getId(), notice.id());
        noticeService.update(
                admin.getId(), notice.id(), new NoticeWriteRequestDto("긴급 전환", "본문", NoticeType.URGENT)
        );

        // 검증
        assertThat(noticeService.pendingUrgent(owner.getId())).isEmpty();
        assertThat(noticeService.pendingUrgent(stranger.getId())).extracting(NoticeResponseDto::id)
                .containsExactly(notice.id());
    }

    @Test
    void deletingNoticeCleansDismissalsAndNotifications() {
        // 준비
        NoticeResponseDto notice = createNotice(NoticeType.URGENT);
        noticeService.dismiss(owner.getId(), notice.id());
        noticeService.dismiss(stranger.getId(), notice.id());

        // 실행
        noticeService.delete(admin.getId(), notice.id());

        // 검증
        assertThat(dismissals.count()).isZero();
        assertThat(notifications.count()).isZero();
        assertThat(noticeService.pendingUrgent(owner.getId())).isEmpty();
        assertThat(notices.count()).isZero();
    }

    @Test
    void missingNoticeCannotCreateDanglingDismissal() {
        // 실행 및 검증
        assertThatThrownBy(() -> noticeService.dismiss(owner.getId(), Long.MAX_VALUE))
                .isInstanceOfSatisfying(SupportException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(dismissals.count()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDismissalsFromTwoDevicesCommitExactlyOnce() throws Exception {
        // 준비: 첫 기기의 숨김 저장이 확정되기 전에 두 번째 기기 요청을 시작한다.
        NoticeResponseDto notice = createNotice(NoticeType.URGENT);
        CountDownLatch stored = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> transaction().execute(status -> {
                boolean result = noticeService.dismiss(owner.getId(), notice.id());
                stored.countDown();
                await(commitFirst);
                return result;
            }));
            assertThat(stored.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> noticeService.dismiss(owner.getId(), notice.id()));

            // 검증: 중복 삽입 예외를 잡아 성공으로 위장하지 않고 첫 저장의 확정을 기다린다.
            try {
                assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                commitFirst.countDown();
            }

            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(dismissals.count()).isEqualTo(1);
            assertThat(noticeService.pendingUrgent(owner.getId())).isEmpty();
        } finally {
            commitFirst.countDown();
        }
    }

    private NoticeResponseDto createNotice(NoticeType type) {
        return noticeService.create(admin.getId(), new NoticeWriteRequestDto("공지", "본문", type));
    }

    private User user(Role role) {
        String identifier = UUID.randomUUID().toString().substring(0, 12);
        return users.saveAndFlush(
                User.createLocalUser(identifier + "@test.invalid", "test", identifier, role, identifier)
        );
    }

    private InquiryResponseDto createInquiry(User user) {
        return inquiryService.create(
                user.getId(), new InquiryCreateRequestDto("문의 제목", "비공개 내용", InquiryCategory.GENERAL, null)
        );
    }

    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 검증 동기화 대기 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
