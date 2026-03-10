package jp.co.translacat.domain.novel.genre.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.genre.dto.GenreResponseDto;
import jp.co.translacat.domain.novel.genre.entity.Genre;
import jp.co.translacat.domain.novel.genre.repository.GenreRepository;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GenreService {
    private final GenreRepository genreRepository;

    private final PlatformService platformService;

    public Optional<Genre> findGenre(String identifier) {
        return genreRepository.findByIdentifier(identifier);
    }

    public Genre getGenre(String identifier) {
        return this.findGenre(identifier)
            .orElseThrow(() -> new EntityNotFoundException("해당 장르를 찾을 수 없습니다: " + identifier));
    }

    public Optional<Genre> findGenre(Long platformId, String name) {
        return genreRepository.findByPlatformIdAndNameEndsWith(platformId, name);
    }

    public Genre getGenre(Long platformId, String name) {
        return this.findGenre(platformId, name)
            .orElseThrow(() -> new EntityNotFoundException(
                String.format("Genre not found for platform ID [%d] and Japanese name [%s]", platformId, name)
            ));
    }

    public List<GenreResponseDto> list(PlatformCode platformCode) {
        Platform platform = platformService.getPlatformByCode(platformCode);
        List<Genre> genres = genreRepository.findAllByPlatformId(platform.getId());
        return genres.stream().map(GenreResponseDto::of).toList();
    }
}
