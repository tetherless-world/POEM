package services.chat.intent;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds concrete {@link ChatIntent} instances from classifier output.
 */
public final class IntentFactory {

    private static final List<IntentDefinition> DEFINITIONS = List.of(
            new IntentDefinition("INSTRUMENT_SCALES", "List scales associated with an instrument"),
            new IntentDefinition("INSTRUMENT_LANGUAGES", "Identify the language metadata for an instrument"),
            new IntentDefinition("INSTRUMENT_QUESTION_TEXTS", "Retrieve item stems for an instrument"),
            new IntentDefinition("INSTRUMENT_RESPONSE_OPTIONS", "Describe response options used by an instrument"),
            new IntentDefinition("INSTRUMENT_LINEAGE", "Summarise provenance of an instrument"),
            new IntentDefinition("INSTRUMENT_SIMILARITY_BY_CONCEPTS", "Highlight shared item concepts across instruments"),
            new IntentDefinition("INSTRUMENT_ITEM_STRUCTURE", "List ordered items for an instrument"),
            new IntentDefinition("INSTRUMENT_EXPERIENCE_COMPARISON", "Compare response experiences across instruments"),
            new IntentDefinition("SCALE_ITEM_CONCEPTS", "Enumerate item concepts for a scale"),
            new IntentDefinition("SCALE_NOTATION", "Show label and notation for a scale"),
            new IntentDefinition("CONCEPT_LOCALIZED_STEMS", "List localised stems for an item concept"),
            new IntentDefinition("CONCEPT_INSTRUMENT_USAGE", "Show instruments using a given concept")
    );

    private IntentFactory() {
    }

    public static List<IntentDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<ChatIntent> create(String intentName,
                                              List<String> instrumentUris,
                                              List<String> scaleUris,
                                              List<String> conceptUris) {
        if (intentName == null || intentName.isBlank()) {
            return Optional.empty();
        }

        List<String> instruments = instrumentUris == null ? Collections.emptyList() : instrumentUris;
        List<String> scales = scaleUris == null ? Collections.emptyList() : scaleUris;
        List<String> concepts = conceptUris == null ? Collections.emptyList() : conceptUris;

        switch (intentName) {
            case "INSTRUMENT_SCALES":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentScalesIntent(instruments.get(0)));
            case "INSTRUMENT_LANGUAGES":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentLanguagesIntent(instruments.get(0)));
            case "INSTRUMENT_QUESTION_TEXTS":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentQuestionTextsIntent(instruments.get(0)));
            case "INSTRUMENT_RESPONSE_OPTIONS":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentResponseOptionsIntent(instruments.get(0)));
            case "INSTRUMENT_LINEAGE":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentLineageIntent(instruments.get(0)));
            case "INSTRUMENT_SIMILARITY_BY_CONCEPTS":
                return instruments.size() < 2
                        ? Optional.empty()
                        : Optional.of(new InstrumentSimilarityByConceptsIntent(instruments));
            case "INSTRUMENT_ITEM_STRUCTURE":
                return instruments.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new InstrumentItemStructureIntent(instruments.get(0)));
            case "INSTRUMENT_EXPERIENCE_COMPARISON":
                return instruments.size() < 2
                        ? Optional.empty()
                        : Optional.of(new InstrumentExperienceComparisonIntent(instruments));
            case "SCALE_ITEM_CONCEPTS":
                return scales.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ScaleItemConceptsIntent(scales.get(0)));
            case "SCALE_NOTATION":
                return scales.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ScaleNotationIntent(scales.get(0)));
            case "CONCEPT_LOCALIZED_STEMS":
                return concepts.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ConceptLocalizedStemsIntent(concepts.get(0)));
            case "CONCEPT_INSTRUMENT_USAGE":
                return concepts.isEmpty()
                        ? Optional.empty()
                        : Optional.of(new ConceptInstrumentUsageIntent(concepts.get(0)));
            default:
                return Optional.empty();
        }
    }

    public record IntentDefinition(String name, String description) {
    }
}
