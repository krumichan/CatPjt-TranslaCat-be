package jp.co.translacat.domain.voice.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.Base;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "voice_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceHistory extends Base {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_group_id")
    private VoiceHistoryGroup group;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String textJa;

    @Column(columnDefinition = "TEXT")
    private String textKo;

    @Builder(access = AccessLevel.PRIVATE)
    public VoiceHistory(VoiceHistoryGroup group, String text, String textJa, String textKo, String userEmail) {
        this.group = group;
        this.text = text;
        this.textJa = textJa;
        this.textKo = textKo;
        this.createdBy = userEmail;
    }

    public static VoiceHistory create(VoiceHistoryGroup group, String text, String textJa, String textKo, String userEmail) {
        return VoiceHistory.builder()
            .group(group)
            .text(text)
            .textJa(textJa)
            .textKo(textKo)
            .userEmail(userEmail)
            .build();
    }
}
