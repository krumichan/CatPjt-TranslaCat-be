package jp.co.translacat.domain.support.service;

import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.entity.SupportNotice;
import jp.co.translacat.domain.support.entity.SupportNoticeDismissal;
import jp.co.translacat.domain.support.enums.NoticeType;
import jp.co.translacat.domain.support.enums.SupportTargetType;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.repository.SupportNoticeRepository;
import jp.co.translacat.domain.support.repository.SupportNoticeDismissalRepository;
import jp.co.translacat.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportNoticeService {

    private final SupportNoticeRepository noticeRepository;
    private final SupportNoticeDismissalRepository dismissalRepository;
    private final SupportAccessService accessService;
    private final SupportNotificationService notificationService;

    public SupportPageResponseDto<NoticeResponseDto> list(int page, int size) {
        return SupportPageResponseDto.from(
                noticeRepository.findAll(
                        accessService.page(
                                page, size,
                                Sort.by(Sort.Order.desc("noticeType"), Sort.Order.desc("id"))
                        )
                ),
                NoticeResponseDto::from
        );
    }

    public List<NoticeResponseDto> pendingUrgent(Long actorId) {
        // 열람/알림 읽음과 무관하게 사용자가 영구 숨김을 선택하지 않은 긴급 공지를 반환한다.
        accessService.user(actorId);
        return noticeRepository.findUndismissed(actorId, NoticeType.URGENT)
                .stream().map(NoticeResponseDto::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean dismiss(Long actorId, Long noticeId) {
        // 공지 잠금 이후 최신 숨김 행을 확인하여 여러 기기의 동시 요청도 한 번만 저장한다.
        User actor = accessService.user(actorId);
        SupportNotice notice = lockedNotice(noticeId);
        if (dismissalRepository.existsByUser_IdAndNotice_Id(actorId, noticeId)) {
            return true;
        }

        // 목록을 받은 뒤 일반 공지로 바뀌어도 사용자의 해당 공지 숨김 의도는 유지한다.
        dismissalRepository.saveAndFlush(SupportNoticeDismissal.create(actor, notice));
        return true;
    }

    @Transactional
    public NoticeResponseDto detail(Long actorId, Long noticeId) {
        // 공지 변경 알림과 상세 읽음 처리가 교차하지 않도록 같은 행을 잠근다.
        SupportNotice notice = lockedNotice(noticeId);
        notificationService.markTargetRead(actorId, SupportTargetType.NOTICE, noticeId);

        return NoticeResponseDto.from(notice);
    }

    @Transactional
    public NoticeResponseDto create(Long actorId, NoticeWriteRequestDto request) {
        // 관리자 권한을 확인하고 긴급 공지와 알림을 함께 저장한다.
        User actor = accessService.admin(actorId);
        SupportNotice notice = noticeRepository.saveAndFlush(
                SupportNotice.create(actor, request.title().strip(), request.content().strip(), request.noticeType())
        );

        if (notice.getNoticeType() == NoticeType.URGENT) {
            notificationService.broadcastNotice(actorId, notice.getId(), notice.getTitle());
        }

        return NoticeResponseDto.from(notice);
    }

    @Transactional
    public NoticeResponseDto update(Long actorId, Long noticeId, NoticeWriteRequestDto request) {
        // 단순 내용 수정은 재알림하지 않고 일반 공지가 긴급 공지로 바뀔 때만 보낸다.
        accessService.admin(actorId);
        SupportNotice notice = lockedNotice(noticeId);
        boolean newlyUrgent = notice.getNoticeType() != NoticeType.URGENT
                && request.noticeType() == NoticeType.URGENT;
        notice.update(request.title().strip(), request.content().strip(), request.noticeType());
        noticeRepository.flush();

        if (newlyUrgent) {
            notificationService.broadcastNotice(actorId, noticeId, notice.getTitle());
        }

        return NoticeResponseDto.from(notice);
    }

    @Transactional
    public boolean delete(Long actorId, Long noticeId) {
        // 삭제한 공지의 알림과 계정별 숨김 이력도 같은 트랜잭션에서 정리한다.
        accessService.admin(actorId);
        SupportNotice notice = lockedNotice(noticeId);
        notificationService.deleteTarget(SupportTargetType.NOTICE, noticeId);
        dismissalRepository.deleteByNoticeId(noticeId);

        // 자식 이력과 공지를 모두 일괄 삭제하여 관리 중인 참조의 삭제 상태가 엇갈리지 않게 한다.
        noticeRepository.deleteByNoticeId(notice.getId());

        return true;
    }

    private SupportNotice lockedNotice(Long noticeId) {
        return noticeRepository.findLockedById(noticeId).orElseThrow(SupportException::notFound);
    }
}
