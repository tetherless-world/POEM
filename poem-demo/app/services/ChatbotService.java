package services;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ChatbotService {
    
    public CompletionStage<String> generateResponse(String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            return generateMockResponse(userMessage);
        });
    }
    
    private String generateMockResponse(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        
        if (lowerMessage.contains("hello") || lowerMessage.contains("hi")) {
            return "Hello! How can I help you today?";
        } else if (lowerMessage.contains("how are you")) {
            return "I'm doing well, thank you! I'm here to help you with any questions you might have.";
        } else if (lowerMessage.contains("weather")) {
            return "I don't have access to real-time weather data, but I'd recommend checking a weather app or website for current conditions.";
        } else if (lowerMessage.contains("help")) {
            return "I'm here to help! You can ask me questions about various topics, and I'll do my best to provide helpful answers.";
        } else if (lowerMessage.contains("time")) {
            return "I don't have access to the current time, but you can check your device's clock or search for the current time in your timezone.";
        } else if (lowerMessage.contains("thank")) {
            return "You're welcome! I'm happy to help.";
        } else if (lowerMessage.contains("bye") || lowerMessage.contains("goodbye")) {
            return "Goodbye! Feel free to come back anytime if you have more questions.";
        } else if (lowerMessage.contains("name")) {
            return "I'm an AI chatbot built with Play Framework! You can call me Assistant.";
        } else {
            return "That's an interesting question! I'm a demo chatbot, so my responses are limited, but I'd be happy to chat with you about anything else.";
        }
    }
}
