package jp.co.translacat.domain.chat.ai.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ChatAiConversationGate {

    public boolean passesResponseRate(int responseRate) {
        if (responseRate <= 0) {
            return false;
        }
        if (responseRate >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < responseRate;
    }
}
