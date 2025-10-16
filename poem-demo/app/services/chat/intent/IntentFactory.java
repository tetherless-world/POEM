package services.chat.intent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Builds concrete {@link ChatIntent} instances from classifier output.
 */
public final class IntentFactory {

    private static final Map<String, IntentProvider> PROVIDERS;
    private static final List<IntentDefinition> DEFINITIONS;

    static {
        Map<String, IntentProvider> providers = new LinkedHashMap<>();
        ServiceLoader.load(IntentProvider.class).forEach(provider -> addProvider(providers, provider));
        if (providers.isEmpty()) {
            registerFallbackProviders(providers);
        }
        PROVIDERS = Map.copyOf(providers);
        DEFINITIONS = PROVIDERS.values().stream()
                .map(provider -> new IntentDefinition(provider.name(), provider.description()))
                .collect(Collectors.toUnmodifiableList());
    }

    private IntentFactory() {
    }

    public static List<IntentDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<ChatIntent> create(String intentName,
                                              List<String> collectionUris,
                                              List<String> instrumentUris,
                                              List<String> scaleUris,
                                              List<String> conceptUris) {
        if (intentName == null || intentName.isBlank()) {
            return Optional.empty();
        }
        IntentProvider provider = PROVIDERS.get(intentName.toUpperCase(Locale.ROOT));
        if (provider == null) {
            return Optional.empty();
        }

        List<String> collections = collectionUris == null ? List.of() : List.copyOf(collectionUris);
        List<String> instruments = instrumentUris == null ? List.of() : List.copyOf(instrumentUris);
        List<String> scales = scaleUris == null ? List.of() : List.copyOf(scaleUris);
        List<String> concepts = conceptUris == null ? List.of() : List.copyOf(conceptUris);

        return provider.create(collections, instruments, scales, concepts);
    }

    public record IntentDefinition(String name, String description) {
    }

    private static void addProvider(Map<String, IntentProvider> providers, IntentProvider provider) {
        String key = provider.name().toUpperCase(Locale.ROOT);
        providers.putIfAbsent(key, provider);
    }

    private static void registerFallbackProviders(Map<String, IntentProvider> providers) {
        addProvider(providers, new InstrumentIntent.Provider());
        addProvider(providers, new InstrumentCollectionIntent.Provider());
        addProvider(providers, new ListInstrumentsIntent.Provider());
        addProvider(providers, new ListInstrumentCollectionsIntent.Provider());
        addProvider(providers, new InstrumentScalesIntent.Provider());
        addProvider(providers, new InstrumentLanguagesIntent.Provider());
        addProvider(providers, new InstrumentQuestionTextsIntent.Provider());
        addProvider(providers, new InstrumentResponseOptionsIntent.Provider());
        addProvider(providers, new InstrumentLineageIntent.Provider());
        addProvider(providers, new InstrumentSimilarityByConceptsIntent.Provider());
        addProvider(providers, new InstrumentItemStructureIntent.Provider());
        addProvider(providers, new InstrumentExperienceComparisonIntent.Provider());
        addProvider(providers, new ScaleItemConceptsIntent.Provider());
        addProvider(providers, new ScaleNotationIntent.Provider());
        addProvider(providers, new ConceptLocalizedStemsIntent.Provider());
        addProvider(providers, new ConceptInstrumentUsageIntent.Provider());
    }
}
