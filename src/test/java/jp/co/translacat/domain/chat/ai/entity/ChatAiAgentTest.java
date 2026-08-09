package jp.co.translacat.domain.chat.ai.entity;

import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChatAiAgentTest {

    @Test
    void createsAndNormalizesAiProfile() {
        ChatAiAgent agent = ChatAiAgent.create(
                "  Mika  ",
                "  friendly cat  ",
                "JA",
                "  Talk naturally.  "
        );

        assertThat(agent.getNickname()).isEqualTo("Mika");
        assertThat(agent.getBio()).isEqualTo("friendly cat");
        assertThat(agent.getOriginalLanguageCode()).isEqualTo("ja");
        assertThat(agent.getPersonaPrompt()).isEqualTo("Talk naturally.");
        assertThat(agent.isActive()).isTrue();
    }

    @Test
    void requiresPersonaPrompt() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> ChatAiAgent.create(
                        "Mika",
                        null,
                        "ja",
                        " "
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatAiErrorCode.PERSONA_REQUIRED));
    }

    @Test
    void softDeleteKeepsProfileSnapshotFields() {
        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                "bio",
                "ja",
                "persona"
        );

        agent.softDelete();

        assertThat(agent.isActive()).isFalse();
        assertThat(agent.isDeleted()).isTrue();
        assertThat(agent.getNickname()).isEqualTo("Mika");
        assertThat(agent.getPersonaPrompt()).isEqualTo("persona");
    }
}
