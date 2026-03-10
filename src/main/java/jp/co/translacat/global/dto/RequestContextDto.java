package jp.co.translacat.global.dto;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.text.SimpleDateFormat;

@Getter
@Setter
@NoArgsConstructor
public class RequestContextDto {
    private String guid;
    private String requestTime;
    private String requestUri;
    private String remoteAddress;
    private String requestHttpMethod;
    private HttpServletRequest request;
    public RequestContextDto(String requestUri, String reqHttpMethod, String guid, HttpServletRequest request) {
        this.requestUri = requestUri;
        this.request = request;
        this.requestHttpMethod = reqHttpMethod;
        this.guid = guid;
        this.remoteAddress = request.getRemoteAddr();
        this.requestTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis());
    }
}
