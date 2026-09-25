package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.domain.languagelearning.listening.setting.port.ListeningPolicyGateway;
import org.springframework.stereotype.Component;

@Component
public class RemoteListeningPolicyGateway implements ListeningPolicyGateway {
    private final RemoteSettingsAccess access;

    public RemoteListeningPolicyGateway(RemoteSettingsAccess access) {
        this.access = access;
    }

    @Override
    public ListeningPolicySnapshot get() {
        return access.client().getListeningPolicy();
    }
}
