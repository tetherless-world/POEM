package services;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import models.chat.ChatMessage;
import play.Logger;

import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class OpenAIService {
    private static final Logger.ALogger logger = Logger.of(OpenAIService.class);
    private final OpenAIClient openAIClient;

    // Configuration - in production, these should be injected from application.conf
    private static final String API_KEY = "";
    private static final String MODEL = "openai/gpt-oss-20b";

    public OpenAIService() {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:1234/v1/") // Local deployment URL
                .apiKey(API_KEY)
                .build();
    }

    /**
     * Generate a response for the given conversation history.
     *
     * @param conversationHistory List of ChatMessage representing the conversation history.
     * @return A CompletionStage that will complete with the AI-generated response.
     */
    public CompletionStage<String> generateResponse(List<ChatMessage> conversationHistory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Calling OpenAI API with {} prior messages", conversationHistory.size());
                return callOpenAIAPI(conversationHistory);
            } catch (Exception e) {
                logger.error("Error calling OpenAI API", e);
                return "Sorry, I'm having trouble processing your request right now. Error: " + e.getMessage();
            }
        });
    }

    /**
     * Overload: Generate a response for a single ChatMessage.
     * Wraps the message in a List and delegates to the main generateResponse method.
     */
    public CompletionStage<String> generateResponse(ChatMessage message) {
        return generateResponse(List.of(message));
    }

    private String callOpenAIAPI(List<ChatMessage> conversationHistory) {
        try {
            // Create the chat completion request using the OpenAI library
            ChatCompletionCreateParams.Builder requestBuilder = ChatCompletionCreateParams.builder()
                    .model(MODEL)
                    .addSystemMessage("You are a helpful AI assistant in a web chat application. Keep responses concise and friendly.")
                    .maxCompletionTokens(800)
                    .temperature(0.7);

            for (ChatMessage message : conversationHistory) {
                if (message.getRole() == ChatMessage.Role.USER) {
                    requestBuilder.addUserMessage(message.getContent());
                } else if (message.getRole() == ChatMessage.Role.ASSISTANT) {
                    requestBuilder.addAssistantMessage(message.getContent());
                }
            }

            ChatCompletionCreateParams request = requestBuilder.build();

            logger.debug("Sending request to OpenAI API with model: {}", MODEL);

            // Call the OpenAI API using the official client
            ChatCompletion chatCompletion = openAIClient.chat().completions().create(request);

            // Extract the response content
            if (chatCompletion.choices() != null && !chatCompletion.choices().isEmpty()) {
                var message = chatCompletion.choices().get(0).message();
                if (message != null && message.content() != null) {
                    String aiResponse = message.content().orElse("No response content");
                    logger.info("Successfully received response from OpenAI: {}", aiResponse);
                    return aiResponse;
                }
            }

            logger.warn("Unexpected response format from OpenAI API");
            return "Sorry, I received an unexpected response format from the AI service.";

        } catch (Exception e) {
            logger.error("Error calling OpenAI API", e);
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    public OpenAIClient getClient() {
        return openAIClient;
    }
}
