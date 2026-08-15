package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.ConversationStartMode;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.request.SpeakingSessionCreateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.factory.SpeakingSessionFactory;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionCreationContext;
import jp.co.translacat.domain.languagelearning.speaking.session.repository.SpeakingSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingSessionCommandService {

    private final SpeakingSessionRepository sessionRepository;
    private final SpeakingSessionCreationContextService contextService;
    private final SpeakingSessionFactory sessionFactory;
    private final SpeakingSessionOpeningCommandService openingCommandService;

    @Transactional
    public SpeakingSession create(
            Long userId,
            SpeakingSessionCreateRequestDto request
    ) {
        if (request != null
                && request.idempotencyKey() != null
                && !request.idempotencyKey().isBlank()) {
            var existing = sessionRepository.findByUserIdAndCreateIdempotencyKey(
                    userId,
                    request.idempotencyKey()
            );
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        SpeakingSessionCreationContext context = contextService.prepare(
                userId,
                request
        );
        SpeakingSession session = sessionFactory.create(request, context);
        session = saveIdempotently(userId, request, session);

        if (context.resolvedStartMode() == ConversationStartMode.AI_FIRST
                && session.getOpeningAssistantText() == null) {
            openingCommandService.start(session, request, context);
        }

        return session;
    }

    private SpeakingSession saveIdempotently(
            Long userId,
            SpeakingSessionCreateRequestDto request,
            SpeakingSession session
    ) {
        try {
            return sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            return sessionRepository.findByUserIdAndCreateIdempotencyKey(
                    userId,
                    request.idempotencyKey()
            ).orElseThrow(() -> e);
        }
    }
}
