package jp.co.translacat.domain.novel.author.service;

import jp.co.translacat.domain.novel.author.entity.Author;
import jp.co.translacat.domain.novel.author.repository.AuthorRepository;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthorService {
    private final AuthorRepository authorRepository;

    public List<Author> findAuthors(Long platformId, List<String> identifiers) {
        return authorRepository.findAllByPlatformIdAndIdentifierIn(platformId, identifiers);
    }

    public Optional<Author> findAuthor(Long platformId, String identifier) {
        return authorRepository.findByPlatformIdAndIdentifier(platformId, identifier);
    }

    @Transactional
    public Author create(Platform platform, String identifier, String name, String nameJa, String nameKo) {
        // 1. 혹시 그 사이에 다른 트랜잭션이 만들었는지 마지막으로 확인!
        return this.findAuthor(platform.getId(), identifier)
                .orElseGet(() -> authorRepository.save(Author.create(platform, identifier, name, nameJa, nameKo)));
    }
}
