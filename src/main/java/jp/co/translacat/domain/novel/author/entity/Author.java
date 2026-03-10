package jp.co.translacat.domain.novel.author.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "author", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform_id", "identifier"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Author extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Column(length = 50, nullable = false)
    private String identifier;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000, nullable = false)
    private String nameJa;

    @Column
    private String nameKo;

    @Builder(access = AccessLevel.PRIVATE)
    private Author(Platform platform, String identifier, String name, String nameJa, String nameKo) {
        this.platform = platform;
        this.identifier = identifier;
        this.name = name;
        this.nameJa = nameJa;
        this.nameKo = nameKo;
    }

    public static Author create(Platform platform, String identifier, String name, String nameJa, String nameKo) {
        return Author.builder()
                .platform(platform)
                .identifier(identifier)
                .name(name)
                .nameJa(nameJa)
                .nameKo(nameKo)
                .build();
    }
}
