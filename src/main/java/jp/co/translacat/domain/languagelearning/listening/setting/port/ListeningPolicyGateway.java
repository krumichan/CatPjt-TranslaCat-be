package jp.co.translacat.domain.languagelearning.listening.setting.port;

import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;

public interface ListeningPolicyGateway {
    ListeningPolicySnapshot get();
}
