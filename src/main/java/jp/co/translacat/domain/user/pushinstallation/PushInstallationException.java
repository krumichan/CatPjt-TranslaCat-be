package jp.co.translacat.domain.user.pushinstallation;

import org.springframework.http.HttpStatus;

public final class PushInstallationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PushInstallationException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    static PushInstallationException invalid() { return new PushInstallationException(HttpStatus.BAD_REQUEST, "PUSH_INVALID_INPUT"); }
    static PushInstallationException conflict() { return new PushInstallationException(HttpStatus.CONFLICT, "PUSH_INSTALLATION_CONFLICT"); }
    static PushInstallationException unauthorized() { return new PushInstallationException(HttpStatus.UNAUTHORIZED, "PUSH_AUTH_REQUIRED"); }
    static PushInstallationException unavailable() { return new PushInstallationException(HttpStatus.SERVICE_UNAVAILABLE, "PUSH_REGISTRY_UNAVAILABLE"); }
}
