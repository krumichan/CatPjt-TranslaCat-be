package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LevelTestQuestionContentPolicyTest {

    private final LevelTestQuestionContentPolicy policy = new LevelTestQuestionContentPolicy(
            mock(LanguageLearningJsonCodec.class)
    );

    @Test
    void writingTranslationRequiresExactOriginSourcePayload() {
        var response = question(
                LevelTestDomain.WRITING,
                LevelTestItemType.WRITING_TRANSLATION,
                LevelTestAnswerMode.TEXT,
                "ja",
                "회의 시간이 변경되었으니 참석이 어려운 경우 알려주세요.",
                List.of(),
                new LevelTestInternalAnswerKeyDto(null, List.of(), null, Map.of()),
                Map.of("translationSourceText", "회의 시간이 변경되었으니 참석이 어려운 경우 알려주세요.")
        );

        assertThat(policy.inspect(response).valid()).isTrue();

        var invalid = question(
                LevelTestDomain.WRITING,
                LevelTestItemType.WRITING_TRANSLATION,
                LevelTestAnswerMode.TEXT,
                "ja",
                "회의 시간이 변경되었습니다.",
                List.of(),
                new LevelTestInternalAnswerKeyDto(null, List.of(), null, Map.of()),
                Map.of("translationSourceText", "다른 문장")
        );
        assertThat(policy.inspect(invalid).reason()).isEqualTo("WRITING_TRANSLATION_SOURCE_INVALID");
    }

    @Test
    void guidedWritingRejectsInsufficientFactsAndIntents() {
        var response = question(
                LevelTestDomain.WRITING,
                LevelTestItemType.WRITING_SCENARIO_RESPONSE,
                LevelTestAnswerMode.TEXT,
                "ja",
                "現在の状況を説明し、提案メールを書いてください。",
                List.of(),
                new LevelTestInternalAnswerKeyDto(null, List.of(), null, Map.of()),
                Map.of(
                        "providedFacts", List.of("入力が重複している"),
                        "requiredIntents", List.of("問題を説明する"),
                        "responseConstraints", List.of("丁寧体を使う")
                )
        );

        assertThat(policy.inspect(response).valid()).isFalse();
        assertThat(policy.inspect(response).reason()).isEqualTo("GUIDED_TASK_FACTS_INSUFFICIENT");
    }

    @Test
    void readingDiscourseRequiresVisibleStructuredEmphasis() {
        String emphasized = "これは、新たな戦略が必要であることを示している。";
        String passage = "売上が前年同期比で減少し、競合製品の台頭も確認された。"
                + emphasized
                + "今後は顧客層を再定義し、施策を見直す必要がある。";
        String question = "上記の強調部分は文章全体でどのような役割を果たしているか。";
        String prompt = passage + "\n\n" + question;
        var response = question(
                LevelTestDomain.READING,
                LevelTestItemType.READING_DISCOURSE_FUNCTION,
                LevelTestAnswerMode.CHOICE,
                null,
                prompt,
                List.of(
                        new LevelTestOptionDto("A", "原因分析"),
                        new LevelTestOptionDto("B", "前文を受けた評価"),
                        new LevelTestOptionDto("C", "例示"),
                        new LevelTestOptionDto("D", "反論")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "B",
                        List.of(),
                        "BEST_ANSWER",
                        Map.of("A", 0, "B", 100, "C", 10, "D", 0)
                ),
                Map.of(
                        "emphasisText", emphasized,
                        "readingPassage", passage,
                        "readingQuestion", question
                )
        );

        assertThat(policy.inspect(response).valid()).isTrue();
    }

    @Test
    void readingRejectsPromptWithoutStructuredPassageAndQuestion() {
        String prompt = "今日は駅前の図書館へ行きました。静かで勉強しやすい場所でした。\n\n"
                + "筆者が図書館について述べていることは何ですか。";
        var response = question(
                LevelTestDomain.READING,
                LevelTestItemType.READING_DETAIL,
                LevelTestAnswerMode.CHOICE,
                null,
                prompt,
                List.of(
                        new LevelTestOptionDto("A", "静かだった"),
                        new LevelTestOptionDto("B", "混んでいた"),
                        new LevelTestOptionDto("C", "遠かった"),
                        new LevelTestOptionDto("D", "閉まっていた")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of()
        );

        assertThat(policy.inspect(response).valid()).isFalse();
        assertThat(policy.inspect(response).reason()).isEqualTo("READING_STRUCTURE_INVALID");
    }

    @Test
    void listeningRequiresSeparateLearnerQuestionAndKeepsScriptHidden() {
        String sourceText = "来週末のパーティーは田中さんの家で開き、料理は持ち寄りにする予定です。";
        String questionText = "この音声メッセージの主な内容は何ですか。";
        var response = question(
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_GIST_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                questionText,
                List.of(
                        new LevelTestOptionDto("A", "パーティーの準備について"),
                        new LevelTestOptionDto("B", "旅行の予定について"),
                        new LevelTestOptionDto("C", "仕事の報告について"),
                        new LevelTestOptionDto("D", "買い物の相談について")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of(
                        "sourceText", sourceText,
                        "listeningQuestion", questionText
                )
        );

        assertThat(policy.inspect(response).valid()).isTrue();
    }

    @Test
    void listeningRejectsFullAudioScriptLeakInPrompt() {
        String sourceText = "来週末のパーティーは田中さんの家で開き、料理は持ち寄りにする予定です。";
        var response = question(
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_GIST_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                sourceText,
                List.of(
                        new LevelTestOptionDto("A", "パーティーの準備について"),
                        new LevelTestOptionDto("B", "旅行の予定について"),
                        new LevelTestOptionDto("C", "仕事の報告について"),
                        new LevelTestOptionDto("D", "買い物の相談について")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of(
                        "sourceText", sourceText,
                        "listeningQuestion", sourceText
                )
        );

        assertThat(policy.inspect(response).valid()).isFalse();
        assertThat(policy.inspect(response).reason()).isEqualTo("LISTENING_SCRIPT_LEAK");
    }

    @Test
    void listeningAllowsShortKeywordReferenceWithoutExposingTranscript() {
        String sourceText = "新製品の海外展開では、現地の流通チャネルを早期に確保することが最も重要です。"
                + "法務確認や品質管理も必要ですが、販売網がなければ市場に届けられません。";
        String questionText = "この音声によると、海外展開で最も重要な課題は何ですか。";
        var response = question(
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_DETAIL_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                questionText,
                List.of(
                        new LevelTestOptionDto("A", "最終品質チェックの完了"),
                        new LevelTestOptionDto("B", "流通チャネルの確保"),
                        new LevelTestOptionDto("C", "法務部門との連携"),
                        new LevelTestOptionDto("D", "来月の役員会議での報告")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "B",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 0, "B", 100, "C", 0, "D", 0)
                ),
                Map.of(
                        "sourceText", sourceText,
                        "listeningQuestion", questionText
                )
        );

        assertThat(policy.inspect(response).valid()).isTrue();
    }

    @Test
    void vocabParaphraseUsesStructuredEmphasisWithoutRawUnderlineMarkup() {
        String prompt = "予定を見合わせることになりました。";
        var response = question(
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_PARAPHRASE_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                prompt,
                List.of(
                        new LevelTestOptionDto("A", "延期する"),
                        new LevelTestOptionDto("B", "開始する"),
                        new LevelTestOptionDto("C", "忘れる"),
                        new LevelTestOptionDto("D", "確認する")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A",
                        List.of(),
                        "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of("emphasisText", "見合わせる")
        );

        assertThat(policy.inspect(response, "ja").valid()).isTrue();
    }

    @Test
    void vocabParaphraseRejectsRawUnderlineMarkupEvenWithEmphasisMetadata() {
        var response = question(
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_PARAPHRASE_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                "予定を<u>見合わせる</u>ことになりました。",
                List.of(
                        new LevelTestOptionDto("A", "延期する"),
                        new LevelTestOptionDto("B", "開始する"),
                        new LevelTestOptionDto("C", "忘れる"),
                        new LevelTestOptionDto("D", "確認する")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A", List.of(), "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of("emphasisText", "見合わせる")
        );

        assertThat(policy.inspect(response, "ja").valid()).isFalse();
    }

    @Test
    void listeningChoiceRejectsOriginLanguageQuestionAndOptionsInJapaneseLane() {
        String sourceText = "会議は午後三時から始まります。";
        String questionText = "이 음성의 중심 내용은 무엇입니까?";
        var response = question(
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_GIST_CHOICE,
                LevelTestAnswerMode.CHOICE,
                null,
                questionText,
                List.of(
                        new LevelTestOptionDto("A", "회의 시간"),
                        new LevelTestOptionDto("B", "날씨"),
                        new LevelTestOptionDto("C", "여행"),
                        new LevelTestOptionDto("D", "주문")
                ),
                new LevelTestInternalAnswerKeyDto(
                        "A", List.of(), "UNIQUE_ANSWER",
                        Map.of("A", 100, "B", 0, "C", 0, "D", 0)
                ),
                Map.of(
                        "sourceText", sourceText,
                        "listeningQuestion", questionText
                )
        );

        assertThat(policy.inspect(response, "ja").valid()).isFalse();
        assertThat(policy.inspect(response, "ja").reason())
                .isEqualTo("LEARNER_TEXT_LANGUAGE_MISMATCH");
    }

    private AiLevelTestQuestionResponseDto question(
            LevelTestDomain domain,
            LevelTestItemType itemType,
            LevelTestAnswerMode answerMode,
            String answerLanguage,
            String promptText,
            List<LevelTestOptionDto> options,
            LevelTestInternalAnswerKeyDto answerKey,
            Map<String, Object> referencePayload
    ) {
        return new AiLevelTestQuestionResponseDto(
                "request",
                1L,
                1,
                20,
                domain,
                itemType,
                3,
                "지시에 따라 답하세요.",
                "ko",
                answerMode,
                answerLanguage,
                promptText,
                options,
                answerKey,
                referencePayload,
                null,
                answerMode == LevelTestAnswerMode.TEXT ? 500 : null,
                answerMode == LevelTestAnswerMode.AUDIO ? 30 : null,
                "level-test-generation",
                "level-test-multiskill-prompt",
                null,
                null,
                (domain == LevelTestDomain.LISTENING
                        || itemType == LevelTestItemType.SPEAKING_REPEAT)
                        ? new LevelTestReferenceAudioDto(
                        "level-test/reference.wav",
                        "audio/wav",
                        2000,
                        "checksum"
                )
                        : null
        );
    }

}
