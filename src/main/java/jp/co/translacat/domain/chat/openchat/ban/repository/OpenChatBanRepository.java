package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenChatBanRepository
        extends JpaRepository<OpenChatBan, Long>,
        OpenChatBanRepositoryCustom {
}
