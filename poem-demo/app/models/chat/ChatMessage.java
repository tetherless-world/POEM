package models.chat;

import java.util.Objects;

/**
 * Lightweight container for messages that will be sent to the LLM.
 */
public class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    private final Role role;
    private final String content;

    public ChatMessage(Role role, String content) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
