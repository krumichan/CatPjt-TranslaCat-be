package jp.co.translacat.domain.support.service;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.entity.*;
import jp.co.translacat.domain.support.enums.*;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.repository.*;
import jp.co.translacat.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportInquiryService {

    private final SupportInquiryRepository inquiryRepository;
    private final SupportReplyRepository replyRepository;
    private final SupportMessageRepository messageRepository;
    private final SupportAccessService accessService;
    private final SupportNotificationService notificationService;

    public SupportPageResponseDto<InquiryResponseDto> list(
            Long actorId, InquiryStatus status, String search, int page, int size
    ) {
        // 관리자 여부를 서버에서 판정하여 조회 조건에 소유권을 포함한다.
        User actor = accessService.user(actorId);
        Page<SupportInquiry> result = inquiryRepository.search(
                accessService.isAdmin(actor), actorId, status, accessService.searchPattern(search),
                accessService.page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))
        );

        Set<Long> unreadIds = notificationService.unreadTargets(
                actorId, SupportTargetType.INQUIRY, result.map(SupportInquiry::getId).getContent()
        );
        return SupportPageResponseDto.from(
                result, inquiry -> InquiryResponseDto.from(inquiry, List.of(), unreadIds.contains(inquiry.getId()))
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InquiryResponseDto detail(Long actorId, Long inquiryId) {
        // 잠금 대기 전에 생긴 DB 스냅샷을 재사용하지 않고, 잠근 뒤 확인한 답변만 읽음 처리한다.
        User actor = accessService.user(actorId);
        SupportInquiry inquiry = accessibleInquiry(actor, inquiryId);
        List<ReplyResponseDto> replies = replies(inquiryId);

        notificationService.markTargetRead(actorId, SupportTargetType.INQUIRY, inquiryId);
        return InquiryResponseDto.from(inquiry, replies, false);
    }

    @Transactional
    public InquiryResponseDto create(Long actorId, InquiryCreateRequestDto request) {
        // 개인 메시지를 연결할 때는 문의 작성자가 실제 수신자인지 확인한다.
        User actor = accessService.user(actorId);
        if (request.relatedMessageId() != null) {
            SupportMessage message = messageRepository.findLockedById(request.relatedMessageId())
                    .orElseThrow(SupportException::notFound);
            if (!message.getRecipient().getId().equals(actorId)) {
                throw SupportException.notFound();
            }
        }

        SupportInquiry inquiry = inquiryRepository.saveAndFlush(
                SupportInquiry.create(
                        actor, request.title().strip(), request.content().strip(),
                        request.category(), request.relatedMessageId()
                )
        );

        notificationService.notifyAdmins(actorId, inquiry.getId(), null, inquiry.getTitle());
        return InquiryResponseDto.from(inquiry, List.of(), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InquiryResponseDto reply(Long actorId, Long inquiryId, ReplyCreateRequestDto request) {
        // 문의 잠금으로 종료/재개와 답변 상태 변경을 직렬화한다.
        User actor = accessService.user(actorId);
        SupportInquiry inquiry = accessibleInquiry(actor, inquiryId);
        boolean admin = accessService.isAdmin(actor);
        SupportReply parent = resolveParent(inquiryId, request.parentId());
        inquiry.addReply(admin);

        SupportReply reply = replyRepository.saveAndFlush(
                SupportReply.create(inquiry, actor, admin, request.content().strip(), parent)
        );

        // 답변 내용과 알림을 같은 트랜잭션에서 저장한다.
        if (admin) {
            notificationService.notifyUser(
                    inquiry.getUser(), actorId, SupportNotificationType.SUPPORT_REPLY,
                    SupportTargetType.INQUIRY, inquiryId, reply.getId(), inquiry.getTitle()
            );
        } else {
            notificationService.notifyAdmins(actorId, inquiryId, reply.getId(), inquiry.getTitle());
        }

        notificationService.markTargetRead(actorId, SupportTargetType.INQUIRY, inquiryId);
        return InquiryResponseDto.from(inquiry, replies(inquiryId), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InquiryResponseDto changeStatus(Long actorId, Long inquiryId, InquiryStatusRequestDto request) {
        // 종료는 작성자와 관리자 모두 가능하며 재개는 CLOSED 상태에서만 가능하다.
        User actor = accessService.user(actorId);
        SupportInquiry inquiry = accessibleInquiry(actor, inquiryId);
        inquiry.changeStatus(request.status());
        inquiryRepository.flush();

        if (request.status() == InquiryStatus.WAITING) {
            notificationService.notifyAdmins(actorId, inquiryId, null, inquiry.getTitle());
        }

        notificationService.markTargetRead(actorId, SupportTargetType.INQUIRY, inquiryId);
        return InquiryResponseDto.from(inquiry, replies(inquiryId), false);
    }

    private SupportInquiry accessibleInquiry(User actor, Long inquiryId) {
        SupportInquiry inquiry = inquiryRepository.findLockedById(inquiryId)
                .orElseThrow(SupportException::notFound);
        accessService.requireOwnerOrAdmin(actor, inquiry.getUser().getId());
        return inquiry;
    }

    private SupportReply resolveParent(Long inquiryId, Long parentId) {
        if (parentId == null) {
            return null;
        }

        // 이미 저장된 같은 문의의 답변만 연결하여 다른 문의 참조와 순환을 막는다.
        return replyRepository.findByIdAndInquiry_Id(parentId, inquiryId)
                .orElseThrow(() -> SupportException.invalid("같은 문의의 답변을 선택해 주세요."));
    }

    private List<ReplyResponseDto> replies(Long inquiryId) {
        return replyRepository.findByInquiry_IdOrderByIdAsc(inquiryId)
                .stream().map(ReplyResponseDto::from).toList();
    }
}
