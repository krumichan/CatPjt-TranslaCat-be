package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiRoomSummaryResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAiRoomSummaryService {

    private final ChatRoomAiMemberRepository aiMemberRepository;
    private final ChatRoomAiSettingRepository settingRepository;

    public ChatAiRoomSummaryResponseDto getSummary(Long roomId) {
        return getSummaries(List.of(roomId))
                .getOrDefault(roomId, ChatAiRoomSummaryResponseDto.disabled());
    }

    public Map<Long, ChatAiRoomSummaryResponseDto> getSummaries(
            Collection<Long> roomIds
    ) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (ChatRoomAiMember member : aiMemberRepository
                .findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(roomIds)) {
            counts.merge(member.getChatRoom().getId(), 1L, Long::sum);
        }

        Map<Long, ChatRoomAiSetting> settings = settingRepository
                .findByChatRoomIdIn(roomIds)
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getChatRoom().getId(),
                        Function.identity()
                ));

        Map<Long, ChatAiRoomSummaryResponseDto> result = new HashMap<>();
        for (Long roomId : roomIds) {
            long count = counts.getOrDefault(roomId, 0L);
            ChatRoomAiSetting setting = settings.get(roomId);
            ChatAiDisclosureType disclosureType = count == 0
                    ? null
                    : setting == null
                    ? ChatAiDisclosureType.PUBLIC
                    : setting.getDisclosureType();
            result.put(
                    roomId,
                    new ChatAiRoomSummaryResponseDto(
                            count > 0,
                            Math.toIntExact(count),
                            disclosureType
                    )
            );
        }
        return Map.copyOf(result);
    }
}
