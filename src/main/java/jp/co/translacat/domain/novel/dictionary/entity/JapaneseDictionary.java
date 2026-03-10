package jp.co.translacat.domain.novel.dictionary.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "japanese_dictionary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JapaneseDictionary extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false, unique = true)
    private String surface;

    @Column(length = 20, nullable = false)
    private String reading;

    @Builder(access = AccessLevel.PRIVATE)
    private JapaneseDictionary(String surface, String reading) {
        this.surface = surface;
        this.reading = reading;
    }

    public static JapaneseDictionary create(String surface, String reading) {
        return JapaneseDictionary.builder().surface(surface).reading(reading).build();
    }
}
