package models.chat;

public class ChatRequest {
    public String message;
    public String sessionId;

    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
}
