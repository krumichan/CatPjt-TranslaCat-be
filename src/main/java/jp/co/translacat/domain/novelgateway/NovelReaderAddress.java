package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;

public record NovelReaderAddress(String platform, String novel, String episode) {
    public NovelReaderAddress {
        // 공개 식별자는 URL 조각일 뿐이며 임의 URL/경로를 내부 서비스에 넘기지 않는다.
        if (platform == null || !platform.matches("[A-Za-z0-9_-]{1,32}")
                || novel == null || !novel.matches("[A-Za-z0-9_-]{1,128}")
                || episode == null || !episode.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
    }

    public String internalPath() {
        return "/internal/v1/novel/" + platform + "/" + novel + "/episodes/" + episode;
    }
}
