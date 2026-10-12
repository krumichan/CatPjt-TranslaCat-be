package jp.co.translacat.domain.support.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.support.enums.NoticeType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "support_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportNotice extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = NoticeTypeConverter.class)
    @Column(name = "important", nullable = false)
    private NoticeType noticeType;

    public static SupportNotice create(User author, String title, String content, NoticeType noticeType) {
        SupportNotice notice = new SupportNotice();
        notice.author = author;
        notice.update(title, content, noticeType);
        return notice;
    }

    public void update(String title, String content, NoticeType noticeType) {
        this.title = title;
        this.content = content;
        this.noticeType = noticeType;
    }
}
