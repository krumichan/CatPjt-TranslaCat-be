package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "support_reply", indexes = {
        @Index(name = "idx_support_reply_inquiry_id", columnList = "inquiry_id,id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportReply extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private SupportInquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role authorRole;

    @Column
    private Long parentId;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static SupportReply create(
            SupportInquiry inquiry, User author, boolean admin, String content, SupportReply parent
    ) {
        SupportReply reply = new SupportReply();
        reply.inquiry = inquiry;
        reply.author = author;
        reply.authorRole = admin ? Role.ADMIN : Role.USER;
        reply.content = content;
        reply.parentId = parent == null ? null : parent.getId();
        reply.depth = parent == null ? 0 : parent.getDepth() + 1;
        return reply;
    }
}
