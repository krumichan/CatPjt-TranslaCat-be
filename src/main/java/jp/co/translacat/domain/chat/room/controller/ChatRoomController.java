package jp.co.translacat.domain.chat.room.controller;

import jp.co.translacat.domain.chat.room.service.ChatRoomCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatRoomController {
    private final ChatRoomCommandService chatRoomCommandService;
}
