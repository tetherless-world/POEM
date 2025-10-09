package services.chat;

import models.Instrument;
import models.QuestionnaireScale;
import play.Logger;
import services.chat.intent.ChatIntent;
import services.chat.intent.InstrumentExperienceComparisonIntent;
import services.chat.intent.InstrumentItemStructureIntent;
import services.chat.intent.InstrumentLanguagesIntent;
import services.chat.intent.InstrumentLineageIntent;
import services.chat.intent.InstrumentQuestionTextsIntent;
import services.chat.intent.InstrumentResponseOptionsIntent;
import services.chat.intent.InstrumentScalesIntent;
import services.chat.intent.InstrumentSimilarityByConceptsIntent;
import services.chat.intent.ScaleItemConceptsIntent;
import services.chat.intent.ScaleNotationIntent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Naive keyword-based detector that maps a user utterance onto a chat intent.
 * This is intentionally simple and meant to be replaced by an LLM classifier later.
 */
@Singleton
public class ChatIntentResolver {

    private static final Logger.ALogger logger = Logger.of(ChatIntentResolver.class);

    private final List<ResourceEntry> instrumentEntries;
    private final List<ResourceEntry> scaleEntries;

    @Inject
    public ChatIntentResolver() {
        this.instrumentEntries = buildInstrumentIndex();
        this.scaleEntries = buildScaleIndex();
    }

    public Optional<ChatIntent> resolve(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalised = message.toLowerCase(Locale.ROOT);
        String canonical = canonicalise(message);

        List<String> instruments = findMatches(normalised, canonical, instrumentEntries);
        List<String> scales = findMatches(normalised, canonical, scaleEntries);

        // Two-instrument intents first.
        if (instruments.size() >= 2) {
            if (containsAny(normalised, "similar", "common", "overlap", "share")) {
                return Optional.of(new InstrumentSimilarityByConceptsIntent(
                        List.of(instruments.get(0), instruments.get(1))));
            }
            if (containsAny(normalised, "experience", "response scale", "compare responses", "response experience")) {
                return Optional.of(new InstrumentExperienceComparisonIntent(
                        List.of(instruments.get(0), instruments.get(1))));
            }
        }

        if (!instruments.isEmpty()) {
            String instrument = instruments.get(0);
            logger.debug("Matched instrument {} for message '{}'", instrument, message);

            if (containsAny(normalised, "language", "translation", "translated", "locale")) {
                return Optional.of(new InstrumentLanguagesIntent(instrument));
            }

            if (containsAny(normalised, "when", "created", "origin", "based on", "source", "generated", "derived")) {
                return Optional.of(new InstrumentLineageIntent(instrument));
            }

            if (containsAny(normalised, "response option", "response options", "response scale", "codebook", "rating scale")) {
                return Optional.of(new InstrumentResponseOptionsIntent(instrument));
            }

            if (containsAny(normalised, "item text", "question text", "question wording", "item wording", "which items")) {
                return Optional.of(new InstrumentQuestionTextsIntent(instrument));
            }

            if (containsAny(normalised, "order", "item order", "structure", "how many items", "number of items", "item count")) {
                return Optional.of(new InstrumentItemStructureIntent(instrument));
            }

            if (containsAny(normalised, "scale", "subscale")) {
                return Optional.of(new InstrumentScalesIntent(instrument));
            }
        }

        if (!scales.isEmpty()) {
            String scale = scales.get(0);
            if (containsAny(normalised, "item concept", "compose", "made of", "make up", "includes")) {
                return Optional.of(new ScaleItemConceptsIntent(scale));
            }
            if (containsAny(normalised, "notation", "code", "abbreviation")) {
                return Optional.of(new ScaleNotationIntent(scale));
            }
        }

        return Optional.empty();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> findMatches(String normalised, String canonical, List<ResourceEntry> entries) {
        Set<String> matches = entries.stream()
                .filter(entry -> normalised.contains(entry.normalisedToken()) || canonical.contains(entry.canonicalToken()))
                .map(ResourceEntry::uri)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new ArrayList<>(matches);
    }

    private static List<ResourceEntry> buildInstrumentIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        for (Instrument instrument : Instrument.getAll()) {
            if (instrument.getLabel() == null) {
                continue;
            }
            entries.addAll(createEntries(instrument.getLabel(), instrument.getUri()));
        }
        return entries;
    }

    private static List<ResourceEntry> buildScaleIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        for (QuestionnaireScale scale : QuestionnaireScale.getAll()) {
            if (scale.getLabel() == null) {
                continue;
            }
            entries.addAll(createEntries(scale.getLabel(), scale.getUri()));
        }
        return entries;
    }

    private static List<ResourceEntry> createEntries(String label, String uri) {
        Objects.requireNonNull(label);
        Objects.requireNonNull(uri);
        List<ResourceEntry> entries = new ArrayList<>();
        for (String variant : generateVariants(label)) {
            String normalised = variant.toLowerCase(Locale.ROOT);
            String canonical = canonicalise(variant);
            entries.add(new ResourceEntry(normalised, canonical, uri));
        }
        return entries;
    }

    private static List<String> generateVariants(String label) {
        List<String> variants = new ArrayList<>();
        variants.add(label);
        variants.add(label.replace('-', ' '));
        variants.add(label.replace("(", "").replace(")", ""));
        variants.add(label.replace("(", "").replace(")", "").replace('-', ' '));
        return variants.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static String canonicalise(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record ResourceEntry(String normalisedToken, String canonicalToken, String uri) {
    }
}
