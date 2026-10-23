package jp.co.translacat.domain.support.service;

import jp.co.translacat.domain.support.exception.SupportException;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.deletion.UserAccountWriteFence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportAccessService {

    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entities;

    public User user(Long userId) {
        if (userId == null) {
            throw SupportException.notFound();
        }

        return userRepository.findById(userId)
                .map(user -> UserAccountWriteFence.lock(entities, user)).orElseThrow(SupportException::notFound);
    }

    public User admin(Long userId) {
        // 요청 본문이나 오래된 화면의 역할 대신 현재 DB 권한을 확인한다.
        User user = user(userId);
        if (!isAdmin(user)) {
            throw SupportException.forbidden();
        }

        return user;
    }

    public boolean isAdmin(User user) {
        return user.getAuthority() == Role.ADMIN;
    }

    public void requireOwnerOrAdmin(User actor, Long ownerId) {
        // 다른 사용자의 비공개 자료 존재 여부도 노출하지 않는다.
        if (!isAdmin(actor) && !actor.getId().equals(ownerId)) {
            throw SupportException.notFound();
        }
    }

    public PageRequest page(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw SupportException.invalid("페이지는 0 이상, 조회 수는 1~100이어야 합니다.");
        }

        return PageRequest.of(page, size, sort);
    }

    public String searchPattern(String query) {
        String value = query == null ? "" : query.strip();
        if (value.length() > 200) {
            throw SupportException.invalid("검색어는 200자 이내로 입력해 주세요.");
        }

        // LIKE 와일드카드는 검색어로 취급하여 의도하지 않은 전체 검색을 막는다.
        return "%" + value.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
