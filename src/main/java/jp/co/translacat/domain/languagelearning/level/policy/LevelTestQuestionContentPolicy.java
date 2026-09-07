package jp.co.translacat.domain.languagelearning.level.policy;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LevelTestQuestionContentPolicy {

    private static final int READING_MIN_PASSAGE_LENGTH = 24;
    private static final int READING_MIN_QUESTION_LENGTH = 6;

    private static final Pattern UNDERLINE_MARKER = Pattern.compile("(?i)</?u>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern KANA = Pattern.compile("[\\u3040-\\u30FF]");
    private static final Pattern ASCII_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern GRAMMAR_BLANK = Pattern.compile("(?:_{2,}|＿{2,})");

    private final LanguageLearningJsonCodec jsonCodec;

    public Health inspect(AiLevelTestQuestionResponseDto response) {
        return inspect(response, null);
    }

    public Health inspect(
            AiLevelTestQuestionResponseDto response,
            String learningLanguage
    ) {
        if (response == null) {
            return invalid("NULL_RESPONSE");
        }
        Health content = inspectValues(
                response.domain(),
                response.itemType(),
                response.instruction(),
                response.instructionLanguage(),
                learningLanguage,
                response.answerMode(),
                response.answerLanguage(),
                response.promptText(),
                response.options() == null ? List.of() : response.options(),
                response.internalAnswerKey(),
                response.referencePayload() == null ? Map.of() : response.referencePayload(),
                response.maxAnswerLength(),
                response.maxAudioSeconds()
        );
        if (!content.valid()) {
            return content;
        }
        boolean required = response.itemType() != null
                && (response.itemType().name().startsWith("LISTENING_")
                || response.itemType() == LevelTestItemType.SPEAKING_REPEAT);
        if (!required) {
            return Health.ok();
        }
        if (response.referenceAudio() == null
                || blank(response.referenceAudio().objectKey())
                || blank(response.referenceAudio().contentType())) {
            return invalid("REFERENCE_AUDIO_MISSING");
        }
        return Health.ok();
    }

    public Health inspect(LevelTestQuestionPool pool) {
        if (pool == null) {
            return invalid("NULL_POOL_QUESTION");
        }
        try {
            Health content = inspectValues(
                    pool.getDomain(),
                    pool.getItemType(),
                    pool.getInstruction(),
                    pool.getInstructionLanguage(),
                    pool.getLearningLanguage(),
                    pool.getAnswerMode(),
                    pool.getAnswerLanguage(),
                    pool.getPromptText(),
                    readOptions(pool.getOptionsJson()),
                    readAnswerKey(pool.getInternalAnswerKeyJson()),
                    readPayload(pool.getReferencePayloadJson()),
                    pool.getMaxAnswerLength(),
                    pool.getMaxAudioSeconds()
            );
            if (!content.valid()) {
                return content;
            }
            return inspectStoredReferenceAudio(
                    pool.getItemType(),
                    pool.getReferenceAudioObjectKey(),
                    pool.getReferenceAudioContentType()
            );
        } catch (RuntimeException exception) {
            return invalid("INVALID_STORED_JSON");
        }
    }

    public Health inspect(LevelTestItem item) {
        if (item == null) {
            return invalid("NULL_ITEM");
        }
        try {
            Health content = inspectValues(
                    item.getDomain(),
                    item.getItemType(),
                    item.getInstruction(),
                    item.getInstructionLanguage(),
                    item.getSession().getLearningLanguage(),
                    item.getAnswerMode(),
                    item.getAnswerLanguage(),
                    item.getPromptText(),
                    readOptions(item.getOptionsJson()),
                    readAnswerKey(item.getInternalAnswerKeyJson()),
                    readPayload(item.getReferencePayloadJson()),
                    item.getMaxAnswerLength(),
                    item.getMaxAudioSeconds()
            );
            if (!content.valid()) {
                return content;
            }
            return inspectStoredReferenceAudio(
                    item.getItemType(),
                    item.getReferenceAudioObjectKey(),
                    item.getReferenceAudioContentType()
            );
        } catch (RuntimeException exception) {
            return invalid("INVALID_STORED_JSON");
        }
    }

    private Health inspectValues(
            LevelTestDomain domain,
            LevelTestItemType itemType,
            String instruction,
            String instructionLanguage,
            String learningLanguage,
            LevelTestAnswerMode answerMode,
            String answerLanguage,
            String promptText,
            List<LevelTestOptionDto> options,
            LevelTestInternalAnswerKeyDto answerKey,
            Map<String, Object> referencePayload,
            Integer maxAnswerLength,
            Integer maxAudioSeconds
    ) {
        if (domain == null || itemType == null) {
            return invalid("DOMAIN_OR_ITEM_TYPE_MISSING");
        }
        if (blank(instruction)) {
            return invalid("INSTRUCTION_MISSING");
        }
        if (!isInstructionCompatible(instructionLanguage, instruction)) {
            return invalid("INSTRUCTION_LANGUAGE_MISMATCH");
        }
        if (blank(promptText)) {
            return invalid("PROMPT_MISSING");
        }
        if (promptText.contains("**") || !hasSafeInlineMarkup(promptText)) {
            return invalid("UNSUPPORTED_PROMPT_MARKUP");
        }
        Health markupHealth = inspectUnderlinePolicy(itemType, promptText, referencePayload);
        if (!markupHealth.valid()) {
            return markupHealth;
        }
        if (domain == LevelTestDomain.READING) {
            String readingPassage = asString(referencePayload.get("readingPassage"));
            String readingQuestion = asString(referencePayload.get("readingQuestion"));
            if (!hasUsableReadingStructure(promptText, readingPassage, readingQuestion)) {
                return invalid("READING_STRUCTURE_INVALID");
            }
            if (itemType == LevelTestItemType.READING_DISCOURSE_FUNCTION) {
                String emphasisText = asString(referencePayload.get("emphasisText"));
                if (blank(emphasisText)
                        || countLiteral(readingPassage, emphasisText.trim()) != 1) {
                    return invalid("READING_EMPHASIS_INVALID");
                }
            }
        }
        Health languageLaneHealth = inspectLearningLanguageLane(
                itemType,
                learningLanguage,
                referencePayload,
                options
        );
        if (!languageLaneHealth.valid()) {
            return languageLaneHealth;
        }
        if (itemType == LevelTestItemType.WRITING_TRANSLATION) {
            String translationSourceText = asString(referencePayload.get("translationSourceText"));
            if (blank(translationSourceText)
                    || !promptText.trim().equals(translationSourceText.trim())) {
                return invalid("WRITING_TRANSLATION_SOURCE_INVALID");
            }
        }
        Health guidanceHealth = inspectGuidedTask(itemType, referencePayload);
        if (!guidanceHealth.valid()) {
            return guidanceHealth;
        }

        LevelTestAnswerMode expectedMode = expectedAnswerMode(itemType);
        if (expectedMode == null || answerMode != expectedMode) {
            return invalid("ANSWER_MODE_MISMATCH");
        }
        if (answerMode == LevelTestAnswerMode.CHOICE) {
            if (!blank(answerLanguage)) {
                return invalid("CHOICE_ANSWER_LANGUAGE_PRESENT");
            }
            Health choiceHealth = inspectChoice(itemType, options, answerKey);
            if (!choiceHealth.valid()) {
                return choiceHealth;
            }
            if (itemType == LevelTestItemType.GRAMMAR_FORM_CHOICE) {
                Health grammarHealth = inspectGrammarFormChoice(promptText, options, answerKey);
                if (!grammarHealth.valid()) {
                    return grammarHealth;
                }
            }
        } else {
            if (blank(answerLanguage)) {
                return invalid("ANSWER_LANGUAGE_MISSING");
            }
            if (options != null && !options.isEmpty()) {
                return invalid("NON_CHOICE_OPTIONS_PRESENT");
            }
        }

        if (maxAnswerLength != null && (maxAnswerLength < 1 || maxAnswerLength > 10000)) {
            return invalid("MAX_ANSWER_LENGTH_INVALID");
        }
        if (maxAudioSeconds != null && (maxAudioSeconds < 1 || maxAudioSeconds > 60)) {
            return invalid("MAX_AUDIO_SECONDS_INVALID");
        }

        if (domain == LevelTestDomain.LISTENING) {
            String sourceText = asString(referencePayload.get("sourceText"));
            String listeningQuestion = asString(referencePayload.get("listeningQuestion"));
            if (blank(sourceText)) {
                return invalid("LISTENING_SOURCE_TEXT_MISSING");
            }
            if (blank(listeningQuestion)) {
                return invalid("LISTENING_QUESTION_MISSING");
            }
            if (!promptText.trim().equals(listeningQuestion.trim())) {
                return invalid("LISTENING_PROMPT_QUESTION_MISMATCH");
            }
            if (hasListeningScriptLeak(promptText, sourceText)) {
                return invalid("LISTENING_SCRIPT_LEAK");
            }
            if (itemType == LevelTestItemType.LISTENING_INTERPRETATION) {
                int meanings = listSize(referencePayload.get("referenceMeanings"));
                int units = listSize(referencePayload.get("keyMeaningUnits"));
                if (meanings < 2 || meanings > 3 || units < 2 || units > 5) {
                    return invalid("LISTENING_INTERPRETATION_REFERENCE_INVALID");
                }
            }
        }
        if (itemType == LevelTestItemType.SPEAKING_REPEAT
                && blank(asString(referencePayload.get("referenceText")))) {
            return invalid("SPEAKING_REPEAT_REFERENCE_TEXT_MISSING");
        }
        return Health.ok();
    }

    private Health inspectChoice(
            LevelTestItemType itemType,
            List<LevelTestOptionDto> options,
            LevelTestInternalAnswerKeyDto answerKey
    ) {
        if (answerKey == null) {
            return invalid("ANSWER_KEY_MISSING");
        }
        List<LevelTestOptionDto> safeOptions = options == null ? List.of() : options;
        if (safeOptions.stream().anyMatch(value -> value == null || blank(value.key()) || blank(value.text()))) {
            return invalid("CHOICE_OPTION_INVALID");
        }
        List<String> keys = safeOptions.stream().map(LevelTestOptionDto::key).toList();
        if (new HashSet<>(keys).size() != keys.size()) {
            return invalid("CHOICE_OPTION_KEY_DUPLICATED");
        }
        if (itemType == LevelTestItemType.GRAMMAR_SENTENCE_ORDER) {
            List<String> order = answerKey.correctOrder();
            if (keys.size() < 2 || order == null || order.size() != keys.size()
                    || !new HashSet<>(keys).equals(new HashSet<>(order))) {
                return invalid("SENTENCE_ORDER_ANSWER_INVALID");
            }
            if (keys.equals(order)) {
                return invalid("SENTENCE_ORDER_NOT_SHUFFLED");
            }
            return Health.ok();
        }
        if (keys.size() != 4 || blank(answerKey.correctOptionKey())
                || !new HashSet<>(keys).contains(answerKey.correctOptionKey())) {
            return invalid("CHOICE_ANSWER_INVALID");
        }
        Map<String, Integer> optionScores = answerKey.optionScores();
        if (optionScores != null && !optionScores.isEmpty()) {
            if (!new HashSet<>(optionScores.keySet()).equals(new HashSet<>(keys))
                    || !Integer.valueOf(100).equals(optionScores.get(answerKey.correctOptionKey()))
                    || optionScores.values().stream().anyMatch(score -> score == null || score < 0 || score > 100)) {
                return invalid("CHOICE_OPTION_SCORES_INVALID");
            }
            if (!blank(answerKey.selectionPolicy())
                    && !Set.of("UNIQUE_ANSWER", "BEST_ANSWER").contains(answerKey.selectionPolicy())) {
                return invalid("CHOICE_SELECTION_POLICY_INVALID");
            }
        }
        return Health.ok();
    }

    private Health inspectUnderlinePolicy(
            LevelTestItemType itemType,
            String promptText,
            Map<String, Object> referencePayload
    ) {
        int opens = countLiteral(promptText, "<u>");
        int closes = countLiteral(promptText, "</u>");
        if (opens > 0 || closes > 0) {
            return invalid("UNDERLINE_MARKUP_NOT_ALLOWED");
        }
        if (itemType != LevelTestItemType.VOCAB_PARAPHRASE_CHOICE) {
            return Health.ok();
        }

        String emphasisText = asString(referencePayload.get("emphasisText"));
        if (blank(emphasisText)) {
            return invalid("VOCAB_PARAPHRASE_EMPHASIS_MISSING");
        }
        String target = emphasisText.trim();
        if (codePointLength(target) > 80 || countLiteral(promptText, target) != 1) {
            return invalid("VOCAB_PARAPHRASE_EMPHASIS_INVALID");
        }
        return Health.ok();
    }

    private Health inspectLearningLanguageLane(
            LevelTestItemType itemType,
            String learningLanguage,
            Map<String, Object> referencePayload,
            List<LevelTestOptionDto> options
    ) {
        if (blank(learningLanguage)) {
            return Health.ok();
        }

        StringBuilder learnerText = new StringBuilder();
        if (itemType != null && itemType.name().startsWith("READING_")) {
            appendLaneText(learnerText, asString(referencePayload.get("readingPassage")));
            appendLaneText(learnerText, asString(referencePayload.get("readingQuestion")));
        } else if (itemType == LevelTestItemType.LISTENING_GIST_CHOICE
                || itemType == LevelTestItemType.LISTENING_DETAIL_CHOICE) {
            appendLaneText(learnerText, asString(referencePayload.get("listeningQuestion")));
        } else {
            return Health.ok();
        }
        if (options != null) {
            options.stream()
                    .filter(value -> value != null)
                    .map(LevelTestOptionDto::text)
                    .forEach(value -> appendLaneText(learnerText, value));
        }

        String value = learnerText.toString();
        if (value.isBlank()) {
            return invalid("LEARNER_TEXT_LANGUAGE_MISMATCH");
        }
        String language = learningLanguage.trim().toLowerCase(Locale.ROOT);
        boolean hasHangul = HANGUL.matcher(value).find();
        boolean hasKana = KANA.matcher(value).find();
        boolean hasAscii = ASCII_LETTER.matcher(value).find();
        boolean mismatch = switch (language) {
            case "ja" -> hasHangul || !hasKana;
            case "ko" -> hasKana || !hasHangul;
            case "en" -> hasHangul || hasKana || !hasAscii;
            default -> false;
        };
        return mismatch ? invalid("LEARNER_TEXT_LANGUAGE_MISMATCH") : Health.ok();
    }

    private void appendLaneText(StringBuilder target, String value) {
        if (blank(value)) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(value.trim());
    }

    private Health inspectGrammarFormChoice(
            String promptText,
            List<LevelTestOptionDto> options,
            LevelTestInternalAnswerKeyDto answerKey
    ) {
        Matcher matcher = GRAMMAR_BLANK.matcher(promptText);
        if (!matcher.find()) {
            return invalid("GRAMMAR_FORM_BLANK_MISSING");
        }
        int start = matcher.start();
        int end = matcher.end();
        if (matcher.find()) {
            return invalid("GRAMMAR_FORM_MULTIPLE_BLANKS");
        }
        String correctOption = options.stream()
                .filter(value -> value != null && answerKey.correctOptionKey().equals(value.key()))
                .map(LevelTestOptionDto::text)
                .findFirst()
                .orElse(null);
        if (blank(correctOption)) {
            return invalid("GRAMMAR_FORM_CORRECT_OPTION_MISSING");
        }
        if (hasBoundaryDuplication(promptText, start, end, correctOption)) {
            return invalid("GRAMMAR_FORM_BOUNDARY_DUPLICATION");
        }
        return Health.ok();
    }

    private boolean hasBoundaryDuplication(
            String promptText,
            int blankStart,
            int blankEnd,
            String option
    ) {
        String left = promptText.substring(0, blankStart).stripTrailing();
        String right = promptText.substring(blankEnd).stripLeading();
        String answer = option.trim();
        if (answer.isBlank()) {
            return true;
        }
        int rightMax = Math.min(3, Math.min(answer.length(), right.length()));
        for (int size = rightMax; size >= 1; size--) {
            String overlap = answer.substring(answer.length() - size);
            if (right.startsWith(overlap) && containsWordLike(overlap)) {
                return true;
            }
        }
        int leftMax = Math.min(3, Math.min(answer.length(), left.length()));
        for (int size = leftMax; size >= 1; size--) {
            String overlap = answer.substring(0, size);
            if (left.endsWith(overlap) && containsWordLike(overlap)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWordLike(String value) {
        return value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private int countLiteral(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private LevelTestAnswerMode expectedAnswerMode(LevelTestItemType itemType) {
        return switch (itemType) {
            case VOCAB_CONTEXT_CHOICE,
                 VOCAB_PARAPHRASE_CHOICE,
                 GRAMMAR_FORM_CHOICE,
                 GRAMMAR_SENTENCE_ORDER,
                 READING_GIST,
                 READING_DETAIL,
                 READING_DISCOURSE_FUNCTION,
                 READING_TEXT_INFERENCE,
                 LISTENING_GIST_CHOICE,
                 LISTENING_DETAIL_CHOICE -> LevelTestAnswerMode.CHOICE;
            case LISTENING_DICTATION,
                 LISTENING_INTERPRETATION,
                 WRITING_TRANSLATION,
                 WRITING_GUIDED_SENTENCE,
                 WRITING_SCENARIO_RESPONSE,
                 WRITING_SHORT_PARAGRAPH -> LevelTestAnswerMode.TEXT;
            case SPEAKING_REPEAT,
                 SPEAKING_GUIDED_RESPONSE,
                 SPEAKING_SHORT_RESPONSE -> LevelTestAnswerMode.AUDIO;
        };
    }

    private Health inspectGuidedTask(
            LevelTestItemType itemType,
            Map<String, Object> referencePayload
    ) {
        int minFacts;
        int minIntents;
        switch (itemType) {
            case WRITING_GUIDED_SENTENCE,
                 SPEAKING_GUIDED_RESPONSE -> {
                minFacts = 1;
                minIntents = 1;
            }
            case WRITING_SCENARIO_RESPONSE,
                 WRITING_SHORT_PARAGRAPH,
                 SPEAKING_SHORT_RESPONSE -> {
                minFacts = 2;
                minIntents = 2;
            }
            default -> {
                return Health.ok();
            }
        }
        if (listSize(referencePayload.get("providedFacts")) < minFacts) {
            return invalid("GUIDED_TASK_FACTS_INSUFFICIENT");
        }
        if (listSize(referencePayload.get("requiredIntents")) < minIntents) {
            return invalid("GUIDED_TASK_INTENTS_INSUFFICIENT");
        }
        if (listSize(referencePayload.get("responseConstraints")) < 1) {
            return invalid("GUIDED_TASK_CONSTRAINTS_MISSING");
        }
        return Health.ok();
    }

    private Health inspectStoredReferenceAudio(
            LevelTestItemType itemType,
            String objectKey,
            String contentType
    ) {
        boolean required = itemType != null
                && (itemType.name().startsWith("LISTENING_")
                || itemType == LevelTestItemType.SPEAKING_REPEAT);
        if (!required) {
            return Health.ok();
        }
        if (blank(objectKey) || blank(contentType)) {
            return invalid("REFERENCE_AUDIO_MISSING");
        }
        return Health.ok();
    }

    private boolean hasUsableReadingStructure(
            String promptText,
            String readingPassage,
            String readingQuestion
    ) {
        if (blank(readingPassage) || blank(readingQuestion)) {
            return false;
        }
        String passage = UNDERLINE_MARKER.matcher(readingPassage.trim()).replaceAll("");
        String question = UNDERLINE_MARKER.matcher(readingQuestion.trim()).replaceAll("");
        if (codePointLength(passage) < READING_MIN_PASSAGE_LENGTH
                || codePointLength(question) < READING_MIN_QUESTION_LENGTH) {
            return false;
        }
        String canonical = passage + "\n\n" + question;
        String normalizedPrompt = UNDERLINE_MARKER.matcher(
                promptText.replace("\r\n", "\n").replace('\r', '\n').trim()
        ).replaceAll("");
        if (!canonical.equals(normalizedPrompt)) {
            return false;
        }
        return passage.chars().anyMatch(value -> ".!?。！？".indexOf(value) >= 0);
    }

    private boolean hasListeningScriptLeak(String promptText, String sourceText) {
        String prompt = compactForLeakComparison(promptText);
        String source = compactForLeakComparison(sourceText);
        if (prompt.isEmpty() || source.isEmpty()) {
            return false;
        }
        if (prompt.equals(source)) {
            return true;
        }
        if (source.length() >= 12 && prompt.contains(source)) {
            return true;
        }

        int threshold = source.length() < 20
                ? Math.max(8, source.length() - 2)
                : Math.max(20, (int) Math.ceil(source.length() * 0.60d));
        if (prompt.length() < threshold) {
            return false;
        }

        return longestCommonSubstringLength(source, prompt) >= threshold;
    }

    private String compactForLeakComparison(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder compact = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(compact::appendCodePoint);
        return compact.toString();
    }

    private int longestCommonSubstringLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int longest = 0;
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            int[] current = new int[right.length() + 1];
            char leftChar = left.charAt(leftIndex - 1);
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (leftChar == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                    longest = Math.max(longest, current[rightIndex]);
                }
            }
            previous = current;
        }
        return longest;
    }


    private boolean hasSafeInlineMarkup(String value) {
        return !ANY_TAG.matcher(value).find();
    }

    private boolean isInstructionCompatible(String languageCode, String instruction) {
        if (blank(instruction)) {
            return false;
        }
        String language = languageCode == null ? "" : languageCode.trim().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "ko" -> HANGUL.matcher(instruction).find() && !KANA.matcher(instruction).find();
            case "ja" -> KANA.matcher(instruction).find() && !HANGUL.matcher(instruction).find();
            case "en" -> ASCII_LETTER.matcher(instruction).find()
                    && !HANGUL.matcher(instruction).find()
                    && !KANA.matcher(instruction).find();
            default -> true;
        };
    }

    private List<LevelTestOptionDto> readOptions(String json) {
        if (blank(json)) {
            return List.of();
        }
        return jsonCodec.read(json, new TypeReference<List<LevelTestOptionDto>>() { });
    }

    private LevelTestInternalAnswerKeyDto readAnswerKey(String json) {
        return blank(json) ? null : jsonCodec.read(json, LevelTestInternalAnswerKeyDto.class);
    }

    private Map<String, Object> readPayload(String json) {
        if (blank(json)) {
            return Map.of();
        }
        Map<String, Object> value = jsonCodec.read(json, new TypeReference<Map<String, Object>>() { });
        return value == null ? Map.of() : value;
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private Health invalid(String reason) {
        return new Health(false, reason);
    }

    public record Health(boolean valid, String reason) {
        public static Health ok() {
            return new Health(true, null);
        }
    }
}
