package jp.co.translacat.domain.novel.genre.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.Getter;

@Entity
@Getter
@Table(name = "genre", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform_id", "identifier"})
})
public class Genre extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(length = 50, nullable = false, unique = true)
    private String identifier;

    @Column(length = 200)
    private String name;

    @Column(length = 200)
    private String nameJa;

    @Column(length = 200)
    private String nameKo;
}
