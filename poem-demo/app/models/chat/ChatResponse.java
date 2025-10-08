package models.chat;

public class ChatResponse {
    public String response;
    public String sessionId;
    public String timestamp;

    public ChatResponse() {
    }

    public ChatResponse(String response, String sessionId, String timestamp) {
        this.response = response;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
    }
}
