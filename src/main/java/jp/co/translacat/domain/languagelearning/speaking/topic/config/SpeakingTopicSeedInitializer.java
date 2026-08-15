package jp.co.translacat.domain.languagelearning.speaking.topic.config;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.languagelearning.speaking.topic.repository.SpeakingTopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpeakingTopicSeedInitializer implements ApplicationRunner {

    private static final int VERSION = 1;

    private final SpeakingTopicRepository topicRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int sortOrder = 10;
        for (Seed seed : seeds()) {
            if (!topicRepository.existsByTopicCodeAndVersion(
                    seed.code(),
                    VERSION
            )) {
                topicRepository.save(SpeakingTopic.create(
                        seed.code(),
                        seed.category(),
                        seed.title(),
                        seed.description(),
                        null,
                        null,
                        seed.recommendedLevel(),
                        seed.startMode(),
                        sortOrder,
                        VERSION
                ));
            }
            sortOrder += 10;
        }
    }

    private List<Seed> seeds() {
        return List.of(
                seed(
                        "DAILY",
                        SpeakingTopicCategory.DAILY,
                        "Daily Conversation",
                        "Everyday conversation practice",
                        "B1",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "TRAVEL",
                        SpeakingTopicCategory.TRAVEL,
                        "Travel",
                        "Airport, hotel, transport and sightseeing conversation",
                        "B1",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "FOOD",
                        SpeakingTopicCategory.FOOD,
                        "Food",
                        "Restaurant, cafe, cooking and food ordering conversation",
                        "A2",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "SHOPPING",
                        SpeakingTopicCategory.SHOPPING,
                        "Shopping",
                        "Price, payment, exchange and delivery conversation",
                        "A2",
                        ConversationStartMode.USER_FIRST
                ),
                seed(
                        "BUSINESS",
                        SpeakingTopicCategory.BUSINESS,
                        "Business",
                        "Meeting, schedule, customer service and presentation conversation",
                        "B1",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "IT",
                        SpeakingTopicCategory.IT,
                        "IT",
                        "Development, API, database, deployment and incident conversation",
                        "B1",
                        ConversationStartMode.USER_FIRST
                ),
                seed(
                        "HOBBY",
                        SpeakingTopicCategory.HOBBY,
                        "Hobby",
                        "Movie, music, reading and exercise conversation",
                        "A2",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "GAME",
                        SpeakingTopicCategory.GAME,
                        "Game",
                        "Cooperation, strategy, character and online game conversation",
                        "A2",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "CULTURE",
                        SpeakingTopicCategory.CULTURE,
                        "Culture",
                        "Festival, etiquette, local culture and language difference conversation",
                        "B1",
                        ConversationStartMode.AI_FIRST
                ),
                seed(
                        "FREE_TALK",
                        SpeakingTopicCategory.FREE_TALK,
                        "Free Talk",
                        "Open conversation without a fixed topic",
                        "A2",
                        ConversationStartMode.USER_FIRST
                )
        );
    }

    private Seed seed(
            String code,
            SpeakingTopicCategory category,
            String title,
            String description,
            String recommendedLevel,
            ConversationStartMode startMode
    ) {
        return new Seed(
                code,
                category,
                title,
                description,
                recommendedLevel,
                startMode
        );
    }

    private record Seed(
            String code,
            SpeakingTopicCategory category,
            String title,
            String description,
            String recommendedLevel,
            ConversationStartMode startMode
    ) {
    }
}
