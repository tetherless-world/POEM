package services;

import models.chat.ChatMessage;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores chat history per session so that the assistant can respond with context.
 */
@Singleton
public class ChatHistoryService {

    private final Map<String, List<ChatTurn>> historyBySession = new ConcurrentHashMap<>();

    public void addUserMessage(String sessionId, String message) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        historyBySession.compute(sessionId, (id, turns) -> {
            List<ChatTurn> updatedTurns = turns == null ? new ArrayList<>() : new ArrayList<>(turns);
            updatedTurns.add(new ChatTurn(message, null));
            return updatedTurns;
        });
    }

    public void addAssistantMessage(String sessionId, String message) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(message, "message must not be null");

        historyBySession.compute(sessionId, (id, turns) -> {
            List<ChatTurn> updatedTurns = turns == null ? new ArrayList<>() : new ArrayList<>(turns);
            if (updatedTurns.isEmpty()) {
                updatedTurns.add(new ChatTurn(null, message));
            } else {
                int lastIndex = updatedTurns.size() - 1;
                ChatTurn lastTurn = updatedTurns.get(lastIndex);
                if (lastTurn.getAssistantMessage() == null) {
                    updatedTurns.set(lastIndex, lastTurn.withAssistantMessage(message));
                } else {
                    updatedTurns.add(new ChatTurn(null, message));
                }
            }
            return updatedTurns;
        });
    }

    public List<ChatTurn> getTurns(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        List<ChatTurn> turns = historyBySession.get(sessionId);
        if (turns == null) {
            return Collections.emptyList();
        }
        return List.copyOf(turns);
    }

    public List<ChatMessage> getMessageHistory(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        List<ChatTurn> turns = historyBySession.get(sessionId);
        if (turns == null || turns.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = new ArrayList<>();
        for (ChatTurn turn : turns) {
            String userMessage = turn.getUserMessage();
            if (userMessage != null && !userMessage.isBlank()) {
                messages.add(new ChatMessage(ChatMessage.Role.USER, userMessage));
            }
            String assistantMessage = turn.getAssistantMessage();
            if (assistantMessage != null && !assistantMessage.isBlank()) {
                messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, assistantMessage));
            }
        }
        return List.copyOf(messages);
    }
}
