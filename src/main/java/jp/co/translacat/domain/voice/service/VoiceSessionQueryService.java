package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionListResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionResponseDto;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;
import jp.co.translacat.domain.voice.mapper.VoiceResponseMapper;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionChannelRepository;
import jp.co.translacat.domain.voice.repository.VoiceSessionRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.domain.voice.support.VoicePolicy;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceSessionQueryService {

    private static final EnumSet<VoiceSessionStatus> HISTORY_STATUSES =
            EnumSet.of(
                    VoiceSessionStatus.COMPLETED,
                    VoiceSessionStatus.FAILED
            );

    private final VoiceSessionRepository sessionRepository;
    private final VoiceSessionChannelRepository channelRepository;
    private final VoiceSegmentRepository segmentRepository;
    private final VoiceResponseMapper responseMapper;
    private final VoicePolicyProperties policy;

    public VoiceSessionResponseDto get(
            Long userId,
            String sessionId
    ) {
        VoiceSession session = getOwnedEntity(userId, sessionId);
        return responseMapper.toSessionResponse(
                session,
                channelRepository.findBySession_IdOrderByChannelAsc(sessionId)
        );
    }

    public VoiceSessionListResponseDto listHistory(
            Long userId,
            LocalDateTime cursor,
            Integer requestedSize
    ) {
        int size = normalizePageSize(requestedSize);
        List<VoiceSession> fetched = sessionRepository.findHistory(
                userId,
                HISTORY_STATUSES,
                cursor,
                size + 1
        );

        boolean hasNext = fetched.size() > size;
        List<VoiceSession> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);

        List<VoiceSessionResponseDto> items = page.stream()
                .<VoiceSessionResponseDto>map(session -> responseMapper.toSessionResponse(
                        session,
                        channelRepository.findBySession_IdOrderByChannelAsc(
                                session.getId()
                        )
                ))
                .toList();

        LocalDateTime nextCursor = hasNext && !page.isEmpty()
                ? page.get(page.size() - 1).getCreatedAt()
                : null;

        return new VoiceSessionListResponseDto(
                items,
                nextCursor
        );
    }

    public VoiceSegmentListResponseDto listSegments(
            Long userId,
            String sessionId,
            Long cursor,
            Integer requestedSize
    ) {
        getOwnedEntity(userId, sessionId);

        int size = normalizePageSize(requestedSize);
        List<VoiceSegment> fetched = segmentRepository.findPage(
                sessionId,
                cursor,
                size + 1
        );

        boolean hasNext = fetched.size() > size;
        List<VoiceSegment> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);
        List<VoiceSegmentResponseDto> items = page.stream()
                .map(responseMapper::toSegmentResponse)
                .toList();

        Long nextCursor = hasNext && !page.isEmpty()
                ? page.get(page.size() - 1).getId()
                : null;

        return new VoiceSegmentListResponseDto(
                items,
                nextCursor
        );
    }

    public void validateStreamAccess(
            Long userId,
            String sessionId,
            VoiceChannel channel
    ) {
        if (!policy.isEnabled()) {
            throw new BusinessException(
                    "Voice Translation is disabled.",
                    VoiceErrorCode.FEATURE_DISABLED
            );
        }

        VoiceSession session = getOwnedEntity(userId, sessionId);
        if (!session.getStatus().isOpen()) {
            throw new BusinessException(
                    "Voice session is not open for streaming.",
                    VoiceErrorCode.SESSION_NOT_STREAMABLE
            );
        }

        channelRepository.findBySession_IdAndChannel(sessionId, channel)
                .orElseThrow(this::notFound);
    }

    private VoiceSession getOwnedEntity(
            Long userId,
            String sessionId
    ) {
        return sessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(this::notFound);
    }

    private int normalizePageSize(Integer requestedSize) {
        int size = requestedSize == null
                ? VoicePolicy.DEFAULT_PAGE_SIZE
                : requestedSize;

        return Math.max(
                1,
                Math.min(size, VoicePolicy.MAX_PAGE_SIZE)
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice resource was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
