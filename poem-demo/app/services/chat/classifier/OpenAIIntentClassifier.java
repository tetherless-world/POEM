package services.chat.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import play.Logger;
import services.OpenAIService;
import services.chat.intent.ChatIntent;
import services.chat.intent.IntentFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses an LLM (via {@link OpenAIService}) to classify chat intents.
 */
@Singleton
public class OpenAIIntentClassifier implements IntentClassifier {

    private static final Logger.ALogger logger = Logger.of(OpenAIIntentClassifier.class);
    private static final double TEMPERATURE = 0.0;
    private static final int MAX_COMPLETION_TOKENS = 256;

    private final OpenAIClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public OpenAIIntentClassifier(OpenAIService openAIService) {
        this.client = openAIService.getClient();
    }

    @Override
    public Optional<ChatIntent> classify(String message, ClassificationContext context) {
        if (context.instruments().isEmpty() && context.scales().isEmpty() && context.concepts().isEmpty()) {
            return Optional.empty();
        }
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(message, context);

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("openai/gpt-oss-20b")
                    .temperature(TEMPERATURE)
                    .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            String content = extractContent(completion);
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            Optional<ResultPayload> payload = parseContent(content, context);
            if (payload.isEmpty()) {
                return Optional.empty();
            }

            ResultPayload result = payload.get();
            return IntentFactory.create(result.intentName(), result.instrumentUris(), result.scaleUris(), result.conceptUris());
        } catch (Exception ex) {
            logger.warn("Intent classification via LLM failed: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private String buildSystemPrompt() {
        String intentDescriptions = IntentFactory.definitions().stream()
                .map(def -> "- " + def.name() + ": " + def.description())
                .collect(Collectors.joining("\n"));

        return "You are an intent classifier for the POEM knowledge graph chat application.\n"
                + "Given a user utterance and candidate resources, select the most appropriate intent\n"
                + "from the list below or report that no intent applies.\n\n"
                + "Intents:\n"
                + intentDescriptions + "\n\n"
                + "Respond with a strict JSON object containing the fields:\n"
                + "{ \"intent\": string|null, \"instrumentUris\": string[], \"scaleUris\": string[], \"conceptUris\": string[] }\n"
                + "Use the provided candidate URIs only. If you are unsure, set \"intent\" to null.\n"
                + "Do not include any additional text.";
    }

    private String buildUserPrompt(String message, ClassificationContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("User question:\n");
        builder.append(message).append("\n\n");

        builder.append("Candidate instruments:\n");
        appendCandidates(builder, context.instruments());
        builder.append("\nCandidate scales:\n");
        appendCandidates(builder, context.scales());
        builder.append("\nCandidate concepts:\n");
        appendCandidates(builder, context.concepts());

        builder.append("\nChoose the intent and URIs that best match the user question.");
        return builder.toString();
    }

    private void appendCandidates(StringBuilder builder, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            builder.append("- none\n");
            return;
        }
        for (Candidate candidate : candidates) {
            builder.append("- ").append(candidate.label())
                    .append(" (").append(candidate.uri()).append(")\n");
        }
    }

    private String extractContent(ChatCompletion completion) {
        if (completion.choices() == null || completion.choices().isEmpty()) {
            return null;
        }
        var message = completion.choices().get(0).message();
        if (message == null || message.content().isEmpty()) {
            return null;
        }
        return message.content().get();
    }

    private Optional<ResultPayload> parseContent(String content, ClassificationContext context) {
        try {
            String cleaned = cleanup(content);
            JsonNode root = mapper.readTree(cleaned);

            JsonNode intentNode = root.get("intent");
            String intentName = intentNode == null || intentNode.isNull() ? null : intentNode.asText();

            List<String> instrumentUris = filterValidUris(readStringArray(root.get("instrumentUris")), context.instruments());
            List<String> scaleUris = filterValidUris(readStringArray(root.get("scaleUris")), context.scales());
            List<String> conceptUris = filterValidUris(readStringArray(root.get("conceptUris")), context.concepts());

            if (intentName == null || intentName.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new ResultPayload(intentName, instrumentUris, scaleUris, conceptUris));
        } catch (Exception ex) {
            logger.debug("Failed to parse classifier response '{}': {}", content, ex.getMessage());
            return Optional.empty();
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> {
            if (n != null && !n.isNull()) {
                values.add(n.asText());
            }
        });
        return values;
    }

    private List<String> filterValidUris(List<String> uris, List<Candidate> candidates) {
        if (uris.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = candidates.stream()
                .map(Candidate::uri)
                .collect(Collectors.toCollection(HashSet::new));
        return uris.stream()
                .filter(allowed::contains)
                .collect(Collectors.toList());
    }

    private String cleanup(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```json", "")
                    .replaceAll("^```", "")
                    .replaceAll("```$", "")
                    .trim();
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace >= firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private record ResultPayload(String intentName,
                                 List<String> instrumentUris,
                                 List<String> scaleUris,
                                 List<String> conceptUris) {
    }
}
