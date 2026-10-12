package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.support.enums.InquiryCategory;
import jp.co.translacat.domain.support.enums.InquiryStatus;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "support_inquiry", indexes = {
        @Index(name = "idx_support_inquiry_user_updated", columnList = "user_id,updated_at"),
        @Index(name = "idx_support_inquiry_status_updated", columnList = "status,updated_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportInquiry extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(nullable = false)
    private int replyCount;

    @Column
    private Long relatedMessageId;

    public static SupportInquiry create(
            User user, String title, String content, InquiryCategory category, Long relatedMessageId
    ) {
        SupportInquiry inquiry = new SupportInquiry();
        inquiry.user = user;
        inquiry.title = title;
        inquiry.content = content;
        inquiry.category = category;
        inquiry.status = InquiryStatus.WAITING;
        inquiry.relatedMessageId = relatedMessageId;
        return inquiry;
    }

    public void addReply(boolean admin) {
        // 종료 상태는 답변으로 암묵적으로 바꾸지 않고 명시적인 재개를 요구한다.
        if (status == InquiryStatus.CLOSED) {
            throw SupportException.closed();
        }

        status = admin ? InquiryStatus.ANSWERED : InquiryStatus.WAITING;
        replyCount++;
    }

    public void changeStatus(InquiryStatus requested) {
        // 답변 완료 여부는 답변 작성으로만 변경한다.
        if (requested != InquiryStatus.WAITING && requested != InquiryStatus.CLOSED) {
            throw SupportException.invalid("문의 상태는 종료 또는 재개로 변경할 수 있습니다.");
        }

        if (requested == InquiryStatus.WAITING && status != InquiryStatus.CLOSED) {
            throw SupportException.invalid("종료한 문의만 다시 열 수 있습니다.");
        }

        status = requested;
    }
}
