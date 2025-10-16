package services.chat;

import models.Instrument;
import models.QuestionnaireScale;
import models.chat.ChatMessage;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import play.Logger;
import services.OpenAIService;
import services.chat.intent.ChatIntent;
import services.chat.intent.ConceptInstrumentUsageIntent;
import services.chat.intent.ConceptLocalizedStemsIntent;
import services.chat.intent.InstrumentCollectionIntent;
import services.chat.intent.InstrumentExperienceComparisonIntent;
import services.chat.intent.InstrumentIntent;
import services.chat.intent.InstrumentItemStructureIntent;
import services.chat.intent.InstrumentLanguagesIntent;
import services.chat.intent.InstrumentLineageIntent;
import services.chat.intent.InstrumentQuestionTextsIntent;
import services.chat.intent.InstrumentResponseOptionsIntent;
import services.chat.intent.InstrumentScalesIntent;
import services.chat.intent.InstrumentSimilarityByConceptsIntent;
import services.chat.intent.ListInstrumentCollectionsIntent;
import services.chat.intent.ListInstrumentsIntent;
import services.chat.intent.ScaleItemConceptsIntent;
import services.chat.intent.ScaleNotationIntent;
import utils.POEMModel;

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
    private static final int MAX_ROWS_IN_CONTEXT = 100;

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
                for (ChatMessage msg : augmentedHistory) {
                    logger.debug("Augmented history message: role={}, content={}", msg.getRole(), msg.getContent());
                }
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
        builder.append("Intent: ").append(intent.description())
                .append(" (").append(intent.name()).append(")\n");

        List<String> parameterSummaries = describeIntentParameters(intent);
        if (!parameterSummaries.isEmpty()) {
            builder.append("Parameters:\n");
            for (String param : parameterSummaries) {
                builder.append("- ").append(param).append("\n");
            }
        }

        builder.append("Facts:\n");

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
                //.append("Explicitly mention each parameter label with its URI in parentheses (e.g., Name (URI)). ")
                .append("From these facts, construct a concise and accurate answer to the user's question. Generate a complete sentence response that is easy to understand. ")
                .append("If they do not address the question, say so clearly.");

        return builder.toString();
    }

    private List<String> describeIntentParameters(ChatIntent intent) {
        List<String> summaries = new ArrayList<>();
        if (intent instanceof ListInstrumentCollectionsIntent collectionListIntent) {
            boolean hasFilters = !collectionListIntent.languageUris().isEmpty()
                    || !collectionListIntent.scaleUris().isEmpty();
            summaries.add(hasFilters ? "Instrument Collections: filtered list" : "Instrument Collections: all (multiple URIs)");
            addLanguageSummaries(collectionListIntent.languageUris(), summaries);
            addScaleSummaries(collectionListIntent.scaleUris(), summaries);
        } else if (intent instanceof ListInstrumentsIntent listIntent) {
            boolean hasFilters = !listIntent.collectionUris().isEmpty()
                    || !listIntent.languageUris().isEmpty()
                    || !listIntent.scaleUris().isEmpty()
                    || listIntent.itemCountEquals() != null;
            summaries.add(hasFilters ? "Instruments: filtered list" : "Instruments: all (multiple URIs)");
            addCollectionSummaries(listIntent.collectionUris(), summaries);
            addLanguageSummaries(listIntent.languageUris(), summaries);
            addScaleSummaries(listIntent.scaleUris(), summaries);
            if (listIntent.itemCountEquals() != null) {
                summaries.add("Item count equals " + listIntent.itemCountEquals());
            }
        } else if (intent instanceof InstrumentCollectionIntent collectionIntent) {
            summaries.add(describeInstrumentCollection(collectionIntent.collectionUri(), "Instrument Collection"));
        } else if (intent instanceof InstrumentIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentScalesIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentLanguagesIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentQuestionTextsIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentResponseOptionsIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentLineageIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentItemStructureIntent i) {
            summaries.add(describeInstrument(i.instrumentUri(), "Instrument"));
        } else if (intent instanceof InstrumentSimilarityByConceptsIntent i) {
            int index = 1;
            for (String uri : i.instrumentUris()) {
                summaries.add(describeInstrument(uri, "Instrument " + index++));
            }
        } else if (intent instanceof InstrumentExperienceComparisonIntent i) {
            int index = 1;
            for (String uri : i.instrumentUris()) {
                summaries.add(describeInstrument(uri, "Instrument " + index++));
            }
        } else if (intent instanceof ScaleItemConceptsIntent s) {
            summaries.add(describeScale(s.scaleUri(), "Scale"));
        } else if (intent instanceof ScaleNotationIntent s) {
            summaries.add(describeScale(s.scaleUri(), "Scale"));
        } else if (intent instanceof ConceptLocalizedStemsIntent c) {
            summaries.add(describeConcept(c.conceptUri(), "Concept"));
        } else if (intent instanceof ConceptInstrumentUsageIntent c) {
            summaries.add(describeConcept(c.conceptUri(), "Concept"));
        }
        return summaries;
    }

    private String describeInstrumentCollection(String uri, String labelPrefix) {
        String label = fetchLabel(uri);
        String definition = fetchDefinition(uri);
        StringBuilder summary = new StringBuilder();
        summary.append(labelPrefix).append(": ")
                .append(label != null ? label : uri)
                .append(" (").append(uri).append(")");
        if (definition != null && !definition.isBlank()) {
            summary.append(" - ").append(definition);
        }
        return summary.toString();
    }

    private String describeInstrument(String uri, String labelPrefix) {
        try {
            Instrument instrument = Instrument.getByUri(uri);
            if (instrument != null && instrument.getLabel() != null) {
                return labelPrefix + ": " + instrument.getLabel() + " (" + uri + ")";
            }
        } catch (Exception ex) {
            logger.debug("Failed to resolve instrument {}: {}", uri, ex.getMessage());
        }
        return labelPrefix + ": " + uri;
    }

    private String describeScale(String uri, String labelPrefix) {
        try {
            QuestionnaireScale scale = QuestionnaireScale.getByUri(uri);
            if (scale != null && scale.getLabel() != null) {
                return labelPrefix + ": " + scale.getLabel() + " (" + uri + ")";
            }
        } catch (Exception ex) {
            logger.debug("Failed to resolve scale {}: {}", uri, ex.getMessage());
        }
        return labelPrefix + ": " + uri;
    }

    private String describeConcept(String uri, String labelPrefix) {
        String label = fetchLabel(uri);
        String display = label != null ? label : uri;
        return labelPrefix + ": " + display + " (" + uri + ")";
    }

    private String describeLanguage(String uri, String labelPrefix) {
        try {
            Model model = POEMModel.getModel();
            Resource resource = model.getResource(uri);
            if (resource != null) {
                String label = resource.hasProperty(RDFS.label) ? resource.getProperty(RDFS.label).getString() : null;
                String notation = resource.hasProperty(SKOS.notation) ? resource.getProperty(SKOS.notation).getString() : null;
                Property countryProperty = model.createProperty("http://schema.org/countryCode");
                String country = resource.hasProperty(countryProperty) ? resource.getProperty(countryProperty).getString() : null;
                String display = label != null ? label : notation;
                if (display != null && country != null && !country.isBlank()) {
                    display = display + " (" + country + ")";
                }
                if (display != null) {
                    return labelPrefix + ": " + display + " (" + uri + ")";
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to resolve language {}: {}", uri, ex.getMessage());
        }
        return labelPrefix + ": " + uri;
    }

    private String fetchLabel(String uri) {
        try {
            Model model = POEMModel.getModel();
            Resource resource = model.getResource(uri);
            if (resource != null && resource.hasProperty(RDFS.label)) {
                return resource.getProperty(RDFS.label).getString();
            }
        } catch (Exception ex) {
            logger.debug("Failed to fetch label for {}: {}", uri, ex.getMessage());
        }
        return null;
    }

    private String fetchDefinition(String uri) {
        try {
            Model model = POEMModel.getModel();
            Resource resource = model.getResource(uri);
            if (resource != null && resource.hasProperty(SKOS.definition)) {
                return resource.getProperty(SKOS.definition).getString();
            }
        } catch (Exception ex) {
            logger.debug("Failed to fetch definition for {}: {}", uri, ex.getMessage());
        }
        return null;
    }

    private void addCollectionSummaries(List<String> collectionUris, List<String> summaries) {
        if (collectionUris == null || collectionUris.isEmpty()) {
            return;
        }
        int index = 1;
        for (String uri : collectionUris) {
            summaries.add(describeInstrumentCollection(uri, "Collection " + index++));
        }
    }

    private void addLanguageSummaries(List<String> languageUris, List<String> summaries) {
        if (languageUris == null || languageUris.isEmpty()) {
            return;
        }
        int index = 1;
        for (String uri : languageUris) {
            summaries.add(describeLanguage(uri, "Language " + index++));
        }
    }

    private void addScaleSummaries(List<String> scaleUris, List<String> summaries) {
        if (scaleUris == null || scaleUris.isEmpty()) {
            return;
        }
        int index = 1;
        for (String uri : scaleUris) {
            summaries.add(describeScale(uri, "Scale " + index++));
        }
    }
}
