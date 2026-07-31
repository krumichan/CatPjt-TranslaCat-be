package jp.co.translacat.domain.chat.openchat.repository;

import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenChatRoomRepository
        extends JpaRepository<OpenChatRoom, Long>,
        OpenChatRoomRepositoryCustom {
}
