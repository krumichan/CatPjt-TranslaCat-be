package jp.co.translacat.domain.common.language;

import jp.co.translacat.domain.common.language.controller.LanguageController;
import jp.co.translacat.domain.common.language.dto.RespLanguageDto;
import jp.co.translacat.domain.common.language.entity.Language;
import jp.co.translacat.domain.common.language.repository.LanguageRepository;
import jp.co.translacat.domain.common.language.service.LanguageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LanguageCatalogTest {
    @Test
    void catalogIncludesEnglishBeforeTheManualSeedWithoutWritingOnGet() throws Exception {
        // 준비: 기존 한국어·일본어만 있는 DB를 모사하되 실제 서비스와 HTTP 직렬화를 사용한다.
        var repository = mock(LanguageRepository.class);
        var existing = List.of(language("ko", "한국어"), language("ja", "日本語"));
        when(repository.findAll()).thenReturn(existing);
        var mvc = MockMvcBuilders.standaloneSetup(new LanguageController(new LanguageService(repository))).build();

        // 실행 / 검증: envelope와 기존 표시명을 유지하며 영어만 추가한다.
        mvc.perform(get("/api/v1/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.length()").value(3))
                .andExpect(jsonPath("$.body[0].code").value("ko"))
                .andExpect(jsonPath("$.body[0].name").value("한국어"))
                .andExpect(jsonPath("$.body[1].name").value("日本語"))
                .andExpect(jsonPath("$.body[2].code").value("en"))
                .andExpect(jsonPath("$.body[2].name").value("English"));
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void existingEnglishAndOtherCatalogRowsKeepTheirCodesNamesAndOrder() {
        // 준비: 수동 관리 중인 영어 표시명과 기존의 추가 언어를 그대로 제공한다.
        var repository = mock(LanguageRepository.class);
        var existing = List.of(language("ja", "日本語"), language("EN", "영어"),
                language("ko", "한국어"), language("fr", "Français"));
        when(repository.findAll()).thenReturn(existing);

        // 실행
        var result = new LanguageService(repository).languages();

        // 검증: 대소문자 차이로 영어를 중복 추가하거나 기존 데이터를 정규화하지 않는다.
        assertThat(result).extracting(RespLanguageDto::getCode).containsExactly("ja", "EN", "ko", "fr");
        assertThat(result).extracting(RespLanguageDto::getName).containsExactly("日本語", "영어", "한국어", "Français");
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void manualEnglishSeedIsRepeatableAndPreservesExistingDisplayNames() throws Exception {
        // 준비: 운영 DB와 무관한 H2 메모리 DB에 기존 PK와 표시명만 구성한다.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:language-" + UUID.randomUUID()
                + ";MODE=MySQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE language (code VARCHAR(2) PRIMARY KEY, name VARCHAR(50) NOT NULL)");
                statement.execute("INSERT INTO language VALUES ('ko', '한국어'), ('ja', '日本語')");
            }
            var seed = new FileSystemResource("scripts/sql/20261016_english_language_catalog.sql");

            // 실행: 재실행 뒤 영어 코드의 대소문자와 표시명을 직접 관리하는 환경에도 적용한다.
            ScriptUtils.executeSqlScript(connection, seed);
            ScriptUtils.executeSqlScript(connection, seed);
            try (var statement = connection.createStatement()) {
                statement.execute("UPDATE language SET code = 'EN', name = '영어' WHERE code = 'en'");
            }
            ScriptUtils.executeSqlScript(connection, seed);

            // 검증: 재실행은 PK 충돌이나 표시명 덮어쓰기 없이 동일 세 행을 보존한다.
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT code, name FROM language ORDER BY code")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("EN");
                assertThat(rows.getString("name")).isEqualTo("영어");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("ja");
                assertThat(rows.getString("name")).isEqualTo("日本語");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("code")).isEqualTo("ko");
                assertThat(rows.getString("name")).isEqualTo("한국어");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private Language language(String code, String name) {
        var language = mock(Language.class);
        when(language.getCode()).thenReturn(code);
        when(language.getName()).thenReturn(name);
        return language;
    }
}
