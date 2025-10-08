package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.chat.ChatMessage;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.ChatHistoryService;
import services.ChatTurn;
import services.OpenAIService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ChatController extends Controller {

    private final OpenAIService openAIService;
    private final ChatHistoryService chatHistoryService;

    @Inject
    public ChatController(OpenAIService openAIService, ChatHistoryService chatHistoryService) {
        this.openAIService = openAIService;
        this.chatHistoryService = chatHistoryService;
    }

    public Result chatPage(Http.Request request) {
        return ok(views.html.chat.render());
    }

    public CompletionStage<Result> sendMessage(Http.Request request) {
        JsonNode json = request.body().asJson();
        if (json == null) {
            return CompletableFuture.completedFuture(badRequest("Invalid JSON"));
        }
        
        String message = json.findPath("message").textValue();
        if (message == null || message.trim().isEmpty()) {
            return CompletableFuture.completedFuture(badRequest("Message cannot be empty"));
        }

        String sessionId = json.findPath("sessionId").textValue();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(badRequest("Session identifier is required"));
        }

        chatHistoryService.addUserMessage(sessionId, message);
        List<ChatMessage> history = chatHistoryService.getMessageHistory(sessionId);

        // Call the real OpenAI API using the service
        return openAIService.generateResponse(history).thenApply(botResponse -> {
            chatHistoryService.addAssistantMessage(sessionId, botResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("response", botResponse);  // Changed from "message" to "response"
            response.put("timestamp", Instant.now().toString());
            response.put("id", UUID.randomUUID().toString());
            response.put("sessionId", sessionId);
            
            return ok(Json.toJson(response));
        });
    }

    public Result getChatHistory(Http.Request request) {
        Map<String, String[]> queryParams = request.queryString();
        String[] sessionValues = queryParams.get("sessionId");
        String sessionId = (sessionValues != null && sessionValues.length > 0) ? sessionValues[0] : null;
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return badRequest("Session identifier is required");
        }

        List<ChatTurn> turns = chatHistoryService.getTurns(sessionId);
        List<Map<String, String>> history = turns.stream()
                .map(turn -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("userMessage", turn.getUserMessage());
                    entry.put("botResponse", turn.getAssistantMessage());
                    return entry;
                })
                .collect(Collectors.toList());

        return ok(Json.toJson(history));
    }
}
