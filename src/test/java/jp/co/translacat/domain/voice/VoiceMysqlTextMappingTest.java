package jp.co.translacat.domain.voice;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.voice.entity.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.MySQLDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceMysqlTextMappingTest {
    @Test
    void mysqlSchemaUsesLongTextForTranscriptsTokensAndPolicy() {
        // 준비: 실제 MySQL dialect로 생성될 DDL 타입을 DB 연결 없이 확인한다.
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", MySQLDialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();
        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(VoiceSession.class)
                    .addAnnotatedClass(VoiceSessionChannel.class)
                    .addAnnotatedClass(VoiceSegment.class)
                    .addAnnotatedClass(VoiceUsageLedger.class)
                    .buildMetadata();

            // 실행/검증: @Lob만 사용하면 MySQL에서 255바이트 TINYTEXT가 생성되므로 타입을 고정한다.
            for (String property : new String[]{"sourceText", "translatedText", "sourceReadingTokens"}) {
                var column = metadata.getEntityBinding(VoiceSegment.class.getName())
                        .getProperty(property).getValue().getColumns().getFirst();
                assertThat(column.getSqlType(metadata)).as(property).isEqualToIgnoringCase("longtext");
            }
            var policy = metadata.getEntityBinding(VoiceSession.class.getName())
                    .getProperty("policySnapshot").getValue().getColumns().getFirst();
            assertThat(policy.getSqlType(metadata)).isEqualToIgnoringCase("longtext");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
