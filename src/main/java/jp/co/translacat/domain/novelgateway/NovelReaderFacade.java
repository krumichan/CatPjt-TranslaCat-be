package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NovelReaderFacade {
    private final NovelReaderQueryService queries;
    private final NovelReaderCommandService commands;

    public JsonNode reader(NovelReaderAddress address) { return queries.reader(actor(), address); }
    public JsonNode status(NovelReaderAddress address, String jobId) { return queries.status(actor(), address, jobId); }
    public JsonNode start(NovelReaderAddress address, NovelReaderCommandService.TranslationStart request) {
        return commands.start(actor(), address, request);
    }
    public JsonNode cancel(NovelReaderAddress address, String jobId) {
        return commands.cancel(actor(), address, jobId);
    }
    public JsonNode audio(NovelReaderAddress address, NovelReaderCommandService.AudioRequest request) {
        return commands.audio(actor(), address, request);
    }
    public JsonNode glossary(NovelReaderAddress address) { return queries.glossary(actor(), address); }
    public JsonNode glossary(NovelReaderAddress address, NovelReaderCommandService.GlossaryUpdate request) {
        return commands.glossary(actor(), address, request);
    }

    public JsonNode repair(NovelReaderAddress address, String jobId, NovelReaderCommandService.RepairRequest request) {
        return commands.repair(actor(), address, jobId, request);
    }

    static long actor() {
        // 외부 body/header의 관리자 플래그나 사용자 ID 대신 기존 인증 주체만 사용한다.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null || principal.getId() <= 0
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new NovelGatewayException(404, "NOVEL_NOT_FOUND", false);
        }
        return principal.getId();
    }
}
