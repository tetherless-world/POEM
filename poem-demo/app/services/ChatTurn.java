package services;

import java.util.Objects;

/**
 * Represents a single user/assistant exchange in the chat history.
 */
public class ChatTurn {

    private final String userMessage;
    private final String assistantMessage;

    public ChatTurn(String userMessage, String assistantMessage) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public ChatTurn withAssistantMessage(String newAssistantMessage) {
        Objects.requireNonNull(newAssistantMessage, "assistant message must not be null");
        return new ChatTurn(this.userMessage, newAssistantMessage);
    }
}
