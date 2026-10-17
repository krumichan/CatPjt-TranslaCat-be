package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.request.VoiceSessionCreateRequestDto;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionChannelRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.repository.VoiceUsageLedgerRepository;
import jp.co.translacat.domain.voice.service.VoiceSessionCommandService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VoiceLanguagePairTest {
    @ParameterizedTest
    @CsvSource({"ko, ja", "ja, ko", "ko, en", "en, ko", "ja, en", "en, ja"})
    void manualSessionPersistsEverySupportedSourceAndTargetPair(String source, String target) {
        // 준비: 실제 DB를 열지 않고 권한·사용량 검사를 통과하는 저장 경계만 구성한다.
        var entityManager = mock(EntityManager.class);
        var sessions = mock(VoiceSessionRepository.class);
        var channels = mock(VoiceSessionChannelRepository.class);
        var usage = mock(VoiceUsageLedgerRepository.class);
        var policy = mock(VoicePolicyProperties.class);
        var user = mock(User.class);
        when(entityManager.find(User.class, 123L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
        when(user.getAuthority()).thenReturn(Role.USER);
        when(policy.getDailyLimitMs()).thenReturn(7_200_000L);
        when(sessions.findActiveByUserId(eq(123L), any())).thenReturn(List.of());
        var service = new VoiceSessionCommandService(entityManager, sessions, channels,
                mock(VoiceSegmentRepository.class), usage, policy, new ObjectMapper());
        var request = new VoiceSessionCreateRequestDto(VoiceMode.MEDIA, VoiceSourceLanguageMode.MANUAL,
                target.toUpperCase(Locale.ROOT), true,
                Map.of(VoiceChannel.REMOTE, source.toUpperCase(Locale.ROOT)));

        // 실행
        service.create(123L, request);

        // 검증: 영어도 기존 언어처럼 정규화되어 세션과 원문 채널에 각각 저장된다.
        var saved = ArgumentCaptor.forClass(VoiceSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getTargetLanguage()).isEqualTo(target);
        assertThat(saved.getValue().getSourceLanguageMode()).isEqualTo(VoiceSourceLanguageMode.MANUAL);
        verify(channels).saveAll(argThat(values -> {
            var iterator = values.iterator();
            VoiceSessionChannel channel = iterator.next();
            assertThat(channel.getChannel()).isEqualTo(VoiceChannel.REMOTE);
            assertThat(channel.getManualSourceLanguage()).isEqualTo(source);
            assertThat(channel.getSession()).isSameAs(saved.getValue());
            assertThat(iterator.hasNext()).isFalse();
            return true;
        }));
    }
}
