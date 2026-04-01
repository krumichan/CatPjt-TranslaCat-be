package jp.co.translacat.domain.chat.room.controller;

import jp.co.translacat.domain.chat.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
}
