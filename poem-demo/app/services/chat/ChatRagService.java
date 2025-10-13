package services.chat;

import models.chat.ChatMessage;
import play.Logger;
import services.OpenAIService;
import services.chat.intent.ChatIntent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Orchestrates the hybrid chat flow by querying the knowledge graph when possible
 * and augmenting the LLM conversation with grounded context.
 */
@Singleton
public class ChatRagService {

    private static final Logger.ALogger logger = Logger.of(ChatRagService.class);
    private static final int MAX_ROWS_IN_CONTEXT = 12;

    private final OpenAIService openAIService;
    private final ChatKnowledgeService knowledgeService;
    private final ChatIntentResolver intentResolver;

    @Inject
    public ChatRagService(OpenAIService openAIService,
                          ChatKnowledgeService knowledgeService,
                          ChatIntentResolver intentResolver) {
        this.openAIService = openAIService;
        this.knowledgeService = knowledgeService;
        this.intentResolver = intentResolver;
    }

    public CompletionStage<String> generateResponse(String userMessage, List<ChatMessage> history) {
        Optional<ChatIntent> resolved = intentResolver.resolve(userMessage, history);

        if (resolved.isPresent()) {
            ChatIntent intent = resolved.get();
            logger.debug("Resolved intent {} for message '{}'", intent.name(), userMessage);
            ChatQueryResult result;
            try {
                result = knowledgeService.executeIntent(intent);
            } catch (Exception ex) {
                logger.warn("Failed executing intent {}: {}", intent.name(), ex.getMessage(), ex);
                result = ChatQueryResult.empty();
            }

            if (!result.isEmpty()) {
                String context = buildKnowledgeContext(intent, result);
                ChatMessage contextMessage = new ChatMessage(ChatMessage.Role.USER, context);
                List<ChatMessage> augmentedHistory = new ArrayList<>(history.size() + 1);
                augmentedHistory.addAll(history);
                augmentedHistory.add(contextMessage);
                return openAIService.generateResponse(augmentedHistory);
            }

            logger.debug("No knowledge graph results for intent {}", intent.name());
            return CompletableFuture.completedFuture(
                    "I could not find relevant information in the knowledge graph for that question.");
        }

        logger.debug("No intent detected for message '{}'", userMessage);
        return CompletableFuture.completedFuture(
                "I could not interpret that question using the available knowledge graph.");
    }

    private String buildKnowledgeContext(ChatIntent intent, ChatQueryResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("The following facts were retrieved from the POEM knowledge graph ")
                .append("for intent ").append(intent.description())
                .append(" (" + intent.name() + "):\n");

        List<Map<String, String>> rows = result.getRows();
        int rowCount = Math.min(rows.size(), MAX_ROWS_IN_CONTEXT);

        for (int i = 0; i < rowCount; i++) {
            Map<String, String> row = rows.get(i);
            String formatted = row.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; "));
            builder.append("- ").append(formatted).append("\n");
        }

        if (rows.size() > rowCount) {
            builder.append("- ... and ")
                    .append(rows.size() - rowCount)
                    .append(" more result(s) omitted for brevity.\n");
        }

        builder.append("Use only these facts (and prior conversation history) to answer the user's question. ")
                .append("If they do not address the question, say so clearly.");

        return builder.toString();
    }
}
