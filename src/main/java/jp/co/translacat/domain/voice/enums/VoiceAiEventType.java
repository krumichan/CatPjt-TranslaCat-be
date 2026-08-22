package jp.co.translacat.domain.voice.enums;

public enum VoiceAiEventType {
    STREAM_READY,
    SPEECH_STARTED,
    TRANSCRIPT_PARTIAL,
    TRANSCRIPT_FINAL,
    VOICE_PIPELINE_COMPLETED,
    VOICE_PIPELINE_FAILED,
    NO_SPEECH,
    BACKPRESSURE,
    STREAM_CLOSED
}
