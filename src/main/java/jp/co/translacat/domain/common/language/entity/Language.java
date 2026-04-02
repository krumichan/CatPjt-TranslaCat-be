package jp.co.translacat.domain.common.language.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "language")
public class Language {

    @Id
    @Column(length = 2)
    private String code;

    @Column(length = 50, nullable = false)
    private String name;
}
