package jp.co.translacat.domain.user.search.controller;

import jp.co.translacat.domain.user.search.dto.UserSearchResponseDto;
import jp.co.translacat.domain.user.search.service.UserSearchService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserSearchController {

    private final UserSearchService userSearchService;

    @GetMapping("/search")
    public ResponseDto<UserSearchResponseDto> searchByPublicId(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String publicId
    ) {
        Long loginUserId = SecurityUtil.getLoginUserId(userPrincipal);
        return ResponseUtil.ok(userSearchService.searchByPublicId(loginUserId, publicId));
    }
}
