package jp.co.translacat.domain.novel.platform.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import lombok.Getter;

@Entity
@Getter
@Table(name = "platform_url_template", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform_id", "urlType"})
})
public class PlatformUrlTemplate extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformUrlType urlType;

    @Column(nullable = false)
    private String urlPattern;
}
