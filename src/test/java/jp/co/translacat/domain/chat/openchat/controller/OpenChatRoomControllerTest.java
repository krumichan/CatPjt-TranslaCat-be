package jp.co.translacat.domain.chat.openchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerProfileCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListResponseDto;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatMembershipService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomCommandService;
import jp.co.translacat.domain.chat.openchat.service.OpenChatRoomQueryService;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenChatRoomControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenChatRoomCommandService commandService;
    private OpenChatRoomQueryService queryService;
    private OpenChatMembershipService membershipService;
    private OpenChatProfileService profileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(OpenChatRoomCommandService.class);
        queryService = mock(OpenChatRoomQueryService.class);
        membershipService = mock(OpenChatMembershipService.class);
        profileService = mock(OpenChatProfileService.class);

        OpenChatRoomController controller =
                new OpenChatRoomController(
                        commandService,
                        queryService,
                        membershipService,
                        profileService
                );
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        User user = User.createLocalUser(
                "open-controller@example.com",
                "password",
                "open-controller",
                Role.USER,
                "OPENCTRL001"
        );
        user.setId(1L);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OPEN 채팅방 생성 API는 인증 사용자 ID를 전달한다")
    void createOpenRoom() throws Exception {
        OpenChatRoomCreateRequestDto request =
                new OpenChatRoomCreateRequestDto(
                        "일본어 회화",
                        "일본어로 대화합니다.",
                        OpenChatVisibility.PUBLIC,
                        50,
                        new OpenChatOwnerProfileCreateRequestDto(
                                "日本語初心者",
                                null
                        )
                );
        OpenChatRoomDetailResponseDto response = response(100L);

        when(commandService.create(1L, request))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/chat/open-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value(201))
                .andExpect(jsonPath("$.body.id").value(100))
                .andExpect(jsonPath("$.body.roomType")
                        .value("OPEN"))
                .andExpect(jsonPath("$.body.visibility")
                        .value("PUBLIC"));

        verify(commandService).create(1L, request);
    }

    @Test
    @DisplayName("PUBLIC OPEN 채팅방 목록 API는 검색·Cursor를 전달한다")
    void getPublicRooms() throws Exception {
        OpenChatRoomListResponseDto response =
                OpenChatRoomListResponseDto.of(
                        List.of(),
                        null,
                        false
                );
        when(queryService.getPublicRooms(
                1L,
                "고양이",
                300L,
                10
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/chat/open-rooms")
                        .param("keyword", "고양이")
                        .param("cursorId", "300")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.hasNext")
                        .value(false));

        verify(queryService).getPublicRooms(
                1L,
                "고양이",
                300L,
                10
        );
    }

    @Test
    @DisplayName("OPEN 채팅방 상세 API는 인증 사용자 ID를 전달한다")
    void getDetail() throws Exception {
        when(queryService.getDetail(1L, 100L))
                .thenReturn(response(100L));

        mockMvc.perform(get("/api/v1/chat/open-rooms/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.id").value(100))
                .andExpect(jsonPath("$.body.joined").value(true));

        verify(queryService).getDetail(1L, 100L);
    }

    private OpenChatRoomDetailResponseDto response(Long roomId) {
        return new OpenChatRoomDetailResponseDto(
                roomId,
                ChatRoomType.OPEN,
                ChatRoomSourceType.OPEN,
                "일본어 회화",
                "일본어로 대화합니다.",
                OpenChatVisibility.PUBLIC,
                OpenChatRoomStatus.ACTIVE,
                1L,
                50,
                true,
                false,
                OpenChatJoinBlockedReason.ALREADY_JOINED,
                ChatRoomMemberRole.OWNER,
                null,
                null,
                null,
                null,
                null
        );
    }
}
