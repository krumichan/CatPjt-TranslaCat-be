package jp.co.translacat.domain.voice.support;

import lombok.experimental.UtilityClass;

@UtilityClass
public class VoiceErrorCode {

    public static final String FEATURE_DISABLED =
            "VOICE_FEATURE_DISABLED";
    public static final String ROLE_NOT_ALLOWED =
            "VOICE_ROLE_NOT_ALLOWED";
    public static final String REQUEST_INVALID =
            "VOICE_INVALID_REQUEST";
    public static final String LANGUAGE_UNSUPPORTED =
            "VOICE_UNSUPPORTED_LANGUAGE";
    public static final String SOURCE_LANGUAGE_INVALID =
            "VOICE_INVALID_SOURCE_LANGUAGE";
    public static final String ACTIVE_SESSION_EXISTS =
            "VOICE_ACTIVE_SESSION_EXISTS";
    public static final String DAILY_LIMIT_EXCEEDED =
            "VOICE_DAILY_LIMIT_EXCEEDED";
    public static final String SESSION_LIMIT_EXCEEDED =
            "VOICE_SESSION_LIMIT_EXCEEDED";
    public static final String SESSION_NOT_STREAMABLE =
            "VOICE_SESSION_NOT_STREAMABLE";
    public static final String ACTIVE_SESSION_DELETE_DENIED =
            "VOICE_ACTIVE_SESSION_DELETE_DENIED";
    public static final String INVALID_TITLE =
            "VOICE_INVALID_TITLE";
    public static final String INVALID_STATE =
            "VOICE_INVALID_STATE";
    public static final String NOT_FOUND =
            "VOICE_NOT_FOUND";
    public static final String CHANNEL_NOT_ALLOWED =
            "VOICE_CHANNEL_NOT_ALLOWED";
    public static final String CHANNEL_ALREADY_CONNECTED =
            "VOICE_CHANNEL_ALREADY_CONNECTED";
    public static final String STALE_CONNECTION =
            "VOICE_STALE_CONNECTION";
    public static final String INVALID_USAGE =
            "VOICE_INVALID_USAGE";
    public static final String INVALID_SEGMENT_OFFSET =
            "VOICE_INVALID_SEGMENT_OFFSET";
    public static final String FINAL_EVENT_CONFLICT =
            "VOICE_FINAL_EVENT_CONFLICT";
    public static final String FINAL_SEQUENCE_CONFLICT =
            "VOICE_FINAL_SEQUENCE_CONFLICT";
    public static final String FINAL_PAYLOAD_CONFLICT =
            "VOICE_FINAL_PAYLOAD_CONFLICT";
    public static final String TARGET_LANGUAGE_CONFLICT =
            "VOICE_TARGET_LANGUAGE_CONFLICT";
    public static final String LATE_FINAL_EVENT =
            "VOICE_LATE_FINAL_EVENT";
    public static final String INVALID_EVENT_SCHEMA =
            "VOICE_INVALID_EVENT_SCHEMA";
    public static final String AI_EVENT_SCOPE_MISMATCH =
            "VOICE_AI_EVENT_SCOPE_MISMATCH";
    public static final String AI_CONNECTION_FAILED =
            "VOICE_AI_CONNECTION_FAILED";
    public static final String AI_CONNECTION_CLOSED =
            "VOICE_AI_CONNECTION_CLOSED";
    public static final String AI_READY_TIMEOUT =
            "VOICE_AI_READY_TIMEOUT";
    public static final String AI_CIRCUIT_OPEN =
            "VOICE_AI_CIRCUIT_OPEN";
    public static final String AI_STREAM_OPEN_FAILED =
            "VOICE_AI_STREAM_OPEN_FAILED";
    public static final String AI_NOT_CONNECTED =
            "VOICE_AI_NOT_CONNECTED";
    public static final String COMPLETE_TIMEOUT =
            "VOICE_COMPLETE_TIMEOUT";
    public static final String WEBSOCKET_TICKET_INVALID =
            "VOICE_WS_TICKET_INVALID";
    public static final String INVALID_AUDIO_FRAME =
            "VOICE_INVALID_AUDIO_FRAME";
    public static final String STREAM_CLOSING =
            "VOICE_STREAM_CLOSING";
    public static final String BACKPRESSURE =
            "VOICE_BACKPRESSURE";
    public static final String BE_BACKPRESSURE =
            "VOICE_BE_BACKPRESSURE";
    public static final String BE_INTERNAL_ERROR =
            "VOICE_BE_INTERNAL_ERROR";
    public static final String INVALID_CONTROL =
            "VOICE_INVALID_CONTROL";
    public static final String RETRY_NOT_ALLOWED =
            "VOICE_RETRY_NOT_ALLOWED";
    public static final String RETRY_SOURCE_UNAVAILABLE =
            "VOICE_RETRY_SOURCE_UNAVAILABLE";
    public static final String RETRY_LANGUAGE_UNAVAILABLE =
            "VOICE_RETRY_LANGUAGE_UNAVAILABLE";
    public static final String RETRY_CONFLICT =
            "VOICE_RETRY_CONFLICT";
    public static final String TRANSLATION_RETRY_FAILED =
            "VOICE_TRANSLATION_RETRY_FAILED";
    public static final String INVALID_AI_RESPONSE =
            "VOICE_INVALID_AI_RESPONSE";
}
