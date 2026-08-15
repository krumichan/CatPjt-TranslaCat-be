package jp.co.translacat.domain.languagelearning.speaking.topic.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "language_learning_speaking_topic",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_speaking_topic_code_version",
                columnNames = {"topic_code", "version"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_speaking_topic_language",
                        columnList = "learning_language,active"
                ),
                @Index(
                        name = "idx_ll_speaking_topic_category",
                        columnList = "category,active"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakingTopic extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_code", nullable = false, length = 100)
    private String topicCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SpeakingTopicCategory category;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "origin_language", length = 20)
    private String originLanguage;

    @Column(name = "learning_language", length = 20)
    private String learningLanguage;

    @Column(name = "recommended_level", length = 50)
    private String recommendedLevel;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "recommended_start_mode",
            nullable = false,
            length = 40
    )
    private ConversationStartMode recommendedStartMode;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int version;

    private SpeakingTopic(
            String topicCode,
            SpeakingTopicCategory category,
            String title,
            String description,
            String originLanguage,
            String learningLanguage,
            String recommendedLevel,
            ConversationStartMode recommendedStartMode,
            int sortOrder,
            int version
    ) {
        this.topicCode = topicCode;
        this.category = category;
        this.title = title;
        this.description = description;
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.recommendedLevel = recommendedLevel;
        this.recommendedStartMode = recommendedStartMode;
        this.active = true;
        this.sortOrder = sortOrder;
        this.version = version;
    }

    public static SpeakingTopic create(
            String topicCode,
            SpeakingTopicCategory category,
            String title,
            String description,
            String originLanguage,
            String learningLanguage,
            String recommendedLevel,
            ConversationStartMode recommendedStartMode,
            int sortOrder,
            int version
    ) {
        return new SpeakingTopic(
                topicCode,
                category,
                title,
                description,
                originLanguage,
                learningLanguage,
                recommendedLevel,
                recommendedStartMode,
                sortOrder,
                version
        );
    }

    public void update(
            String title,
            String description,
            String recommendedLevel,
            ConversationStartMode recommendedStartMode,
            Integer sortOrder,
            Boolean active
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (description != null) {
            this.description = description.trim();
        }
        if (recommendedLevel != null) {
            this.recommendedLevel = recommendedLevel.trim();
        }
        if (recommendedStartMode != null) {
            this.recommendedStartMode = recommendedStartMode;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (active != null) {
            this.active = active;
        }
    }
}
