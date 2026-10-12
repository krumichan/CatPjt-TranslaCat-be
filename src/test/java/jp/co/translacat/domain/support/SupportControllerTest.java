package jp.co.translacat.domain.support;

import jp.co.translacat.domain.support.controller.*;
import jp.co.translacat.domain.support.dto.*;
import jp.co.translacat.domain.support.enums.NoticeType;
import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.support.service.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {
                SupportInquiryController.class, SupportNoticeController.class,
                SupportMessageController.class, SupportNotificationController.class
        },
        properties = {
                "cors.allowed-origin=http://localhost:3000",
                "jwt.token.secret-key=dGVzdC1zZWNyZXQta2V5LXRlc3Qtc2VjcmV0LWtleQ==",
                "jwt.token.expired.access=86400000",
                "jwt.token.expired.refresh=604800000"
        }
)
@Import({SecurityConfig.class, JwtFilter.class, JWTService.class, ApiLoggingFilter.class})
class SupportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SupportInquiryService inquiries;
    @MockitoBean SupportNoticeService notices;
    @MockitoBean SupportMessageService messages;
    @MockitoBean SupportNotificationService notifications;

    @Test
    void anonymousCannotReadSupport() throws Exception {
        // 실행 및 검증
        mvc.perform(get("/api/v1/support/inquiries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/support/messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/support/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(inquiries, messages, notifications);
    }

    @Test
    void normalUserCannotPublishNoticesSendMessagesOrSearchRecipients() throws Exception {
        // 준비
        String notice = "{\"title\":\"공지\",\"content\":\"본문\",\"noticeType\":\"URGENT\"}";
        String message = "{\"recipientId\":8,\"title\":\"안내\",\"content\":\"본문\"}";

        // 실행 및 검증
        mvc.perform(post("/api/v1/support/notices").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(notice))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/support/messages").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/support/recipients").with(actor(Role.USER)).param("query", "name"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notices, messages);
    }

    @Test
    void blankAndOversizedInputsAreRejectedBeforePersistence() throws Exception {
        // 준비
        String blank = "{\"title\":\"  \",\"content\":\"본문\",\"category\":\"GENERAL\"}";
        String tooLong = "{\"content\":\"" + "가".repeat(10001) + "\"}";

        // 실행 및 검증
        mvc.perform(post("/api/v1/support/inquiries").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(blank))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.body.errorCode").value("SUPPORT_INVALID_REQUEST"));
        mvc.perform(post("/api/v1/support/inquiries/1/replies").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(tooLong))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inquiries);
    }

    @Test
    void invalidEnumsReturnBadRequestInsteadOfServerError() throws Exception {
        // 실행 및 검증
        mvc.perform(get("/api/v1/support/inquiries").with(actor(Role.USER)).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/support/inquiries/1/status").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inquiries);
    }

    @Test
    void queryUsesAuthenticatedIdentityAndExistingEnvelope() throws Exception {
        // 준비
        when(inquiries.list(eq(7L), isNull(), eq(""), eq(0), eq(20)))
                .thenReturn(new SupportPageResponseDto<>(List.of(), 0, 20, 0, 0));

        // 실행 및 검증: 임의 userId 쿼리로 다른 계정을 지정할 수 없다.
        mvc.perform(get("/api/v1/support/inquiries").with(actor(Role.USER)).param("userId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.items").isArray())
                .andExpect(jsonPath("$.body.page").value(0));
        verify(inquiries).list(7L, null, "", 0, 20);
    }

    @Test
    void closedInquiryErrorKeepsConflictStatus() throws Exception {
        // 준비
        when(inquiries.reply(eq(7L), eq(1L), any())).thenThrow(SupportException.closed());

        // 실행 및 검증
        mvc.perform(post("/api/v1/support/inquiries/1/replies").with(actor(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답변\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.body.errorCode").value("SUPPORT_INQUIRY_CLOSED"));
    }

    @Test
    void urgentAndDismissEndpointsRequireAuthentication() throws Exception {
        // 실행 및 검증
        mvc.perform(get("/api/v1/support/notices/urgent")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/support/notices/12/dismiss")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notices);
    }

    @Test
    void usersCanFetchAndDismissOnlyAsAuthenticatedIdentity() throws Exception {
        // 준비
        when(notices.pendingUrgent(7L)).thenReturn(List.of());
        when(notices.dismiss(7L, 12L)).thenReturn(true);

        // 실행 및 검증: 쿼리와 본문의 임의 userId는 숨김 대상 계정을 바꾸지 않는다.
        mvc.perform(get("/api/v1/support/notices/urgent").with(actor(Role.USER)).param("userId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").isArray());
        mvc.perform(put("/api/v1/support/notices/12/dismiss").with(actor(Role.USER))
                        .param("userId", "8")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(true));
        verify(notices).pendingUrgent(7L);
        verify(notices).dismiss(7L, 12L);
    }

    @Test
    void noticeTypeIsRequiredAndRejectsUnknownValues() throws Exception {
        // 준비
        String missing = "{\"title\":\"공지\",\"content\":\"본문\",\"important\":true}";
        String unknown = "{\"title\":\"공지\",\"content\":\"본문\",\"noticeType\":\"IMPORTANT\"}";

        // 실행 및 검증: 예전 필드만 보낸 요청도 일반 공지로 조용히 저장하지 않는다.
        mvc.perform(post("/api/v1/support/notices").with(actor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(missing))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/support/notices/12").with(actor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notices);
    }

    @Test
    void administratorCreatesTypedNoticeWithoutLegacyImportanceField() throws Exception {
        // 준비
        var request = new NoticeWriteRequestDto("긴급 안내", "본문", NoticeType.URGENT);
        when(notices.create(7L, request)).thenReturn(
                new NoticeResponseDto(12L, "긴급 안내", "본문", NoticeType.URGENT, "관리자", null, null)
        );

        // 실행 및 검증
        mvc.perform(post("/api/v1/support/notices").with(actor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"긴급 안내\",\"content\":\"본문\",\"noticeType\":\"URGENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(201))
                .andExpect(jsonPath("$.body.noticeType").value("URGENT"))
                .andExpect(jsonPath("$.body.important").doesNotExist());
        verify(notices).create(7L, request);
    }

    private RequestPostProcessor actor(Role role) {
        User actor = User.createLocalUser("support@test.invalid", "password", "테스트", role, "support-user");
        actor.setId(7L);
        return user(new UserPrincipal(actor));
    }
}
