package services.chat;

import models.Instrument;
import models.Language;
import models.QuestionnaireScale;
import models.chat.ChatMessage;
import play.Logger;
import services.chat.classifier.IntentClassifier;
import services.chat.classifier.IntentClassifier.Candidate;
import services.chat.classifier.IntentClassifier.ClassificationContext;
import services.chat.classifier.NoopIntentClassifier;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves user utterances to chat intents using an optional LLM classifier with a heuristic fallback.
 */
@Singleton
public class ChatIntentResolver {

    private static final Logger.ALogger logger = Logger.of(ChatIntentResolver.class);

    private static final Map<String, List<String>> SPECIAL_TOKEN_ALIASES;
    private static final Map<String, String> LANGUAGE_LABEL_BY_CODE;
    private static final Map<String, Set<String>> LANGUAGE_CODES_BY_LABEL_TOKEN;

    static {
        List<Language> languages = Language.getAll();
        Map<String, String> codeToLabel = new HashMap<>();
        Map<String, Set<String>> labelTokenToCodes = new HashMap<>();
        for (Language language : languages) {
            if (language.getNotation() == null) {
                continue;
            }
            String code = language.getNotation().toLowerCase(Locale.ROOT);
            String label = language.getLabel() == null
                    ? code
                    : language.getLabel().toLowerCase(Locale.ROOT);
            codeToLabel.put(code, label);
            if (language.getLabel() != null) {
                for (String token : basicTokens(language.getLabel())) {
                    labelTokenToCodes.computeIfAbsent(token, k -> new LinkedHashSet<>()).add(code);
                }
            }
        }
        LANGUAGE_LABEL_BY_CODE = Collections.unmodifiableMap(codeToLabel);
        LANGUAGE_CODES_BY_LABEL_TOKEN = labelTokenToCodes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("y", List.of("youth"));
        aliases.put("youth", List.of("y"));
        aliases.put("cg", List.of("caregiver", "parent"));
        aliases.put("caregiver", List.of("cg", "parent"));
        aliases.put("parent", List.of("caregiver", "cg"));
        aliases.put("t", List.of("teacher"));
        aliases.put("teacher", List.of("t"));
        aliases.put("a", List.of("adult"));
        aliases.put("adult", List.of("a"));

        for (Map.Entry<String, String> entry : LANGUAGE_LABEL_BY_CODE.entrySet()) {
            String code = entry.getKey();
            String label = entry.getValue();
            aliases.computeIfAbsent(code, k -> new ArrayList<>()).add(label);
            for (String token : basicTokens(label)) {
                aliases.computeIfAbsent(token, k -> new ArrayList<>()).add(code);
            }
        }

        SPECIAL_TOKEN_ALIASES = aliases.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(new LinkedHashSet<>(e.getValue()))
                ));
    }

    private final IntentClassifier classifier;
    private final List<ResourceEntry> instrumentEntries;
    private final List<ResourceEntry> scaleEntries;

    public ChatIntentResolver() {
        this(new NoopIntentClassifier());
    }

    @Inject
    public ChatIntentResolver(IntentClassifier classifier) {
        this.classifier = classifier;
        this.instrumentEntries = buildInstrumentIndex();
        this.scaleEntries = buildScaleIndex();
    }

    public Optional<ChatIntent> resolve(String latestMessage, List<ChatMessage> history) {
        String contextText = buildContextText(latestMessage, history);
        logger.debug("Context text for intent resolution: '{}'", contextText);
        if (contextText.isBlank()) {
            return Optional.empty();
        }

        String normalised = contextText.toLowerCase(Locale.ROOT);
        String canonical = canonicalise(contextText);
        Set<String> messageTokens = tokenize(contextText);

        List<ResourceEntry> instrumentMatches = findMatches(messageTokens, instrumentEntries);
        List<ResourceEntry> scaleMatches = findMatches(messageTokens, scaleEntries);

        ClassificationContext classificationContext = new ClassificationContext(
                toCandidates(instrumentMatches, 5),
                toCandidates(scaleMatches, 5),
                List.of()
        );

        Optional<ChatIntent> classifiedIntent = classifier.classify(latestMessage, classificationContext);
        if (classifiedIntent.isPresent()) {
            logger.debug("LLM classified intent {} for message '{}'", classifiedIntent.get().name(), latestMessage);
            return classifiedIntent;
        } else {
            logger.debug("LLM classification returned no result for message '{}'", latestMessage);
        }

        List<String> instruments = instrumentMatches.stream()
                .map(ResourceEntry::uri)
                .collect(Collectors.toList());
        List<String> scales = scaleMatches.stream()
                .map(ResourceEntry::uri)
                .collect(Collectors.toList());

        // Heuristic fallback
        if (instruments.size() >= 2) {
            if (containsAny(normalised, "similar", "common", "overlap", "share")) {
                return Optional.of(new InstrumentSimilarityByConceptsIntent(
                        instruments.subList(0, Math.min(2, instruments.size()))));
            }
            if (containsAny(normalised, "experience", "response scale", "compare responses", "response experience")) {
                return Optional.of(new InstrumentExperienceComparisonIntent(
                        instruments.subList(0, Math.min(2, instruments.size()))));
            }
        }

        if (!instruments.isEmpty()) {
            String instrument = instruments.get(0);
            logger.debug("Heuristic matched instrument {} for message '{}'", instrument, latestMessage);

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

    private static String buildContextText(String latestMessage, List<ChatMessage> history) {
        StringBuilder builder = new StringBuilder();
        if (history != null) {
            for (ChatMessage message : history) {
                if (message.getRole() == ChatMessage.Role.USER) {
                    builder.append(message.getContent()).append(' ');
                }
            }
        }
        if (latestMessage != null && !latestMessage.isBlank()) {
            builder.append(latestMessage);
        }
        return builder.toString().trim();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<ResourceEntry> findMatches(Set<String> messageTokens, List<ResourceEntry> entries) {
        List<EntryScore> scores = new ArrayList<>();
        for (ResourceEntry entry : entries) {
            int score = overlapScore(messageTokens, entry.tokens());
            if (score > 0) {
                scores.add(new EntryScore(entry, score));
            }
        }
        scores.sort(Comparator.<EntryScore, Integer>comparing(EntryScore::score).reversed());
        return scores.stream().map(EntryScore::entry).collect(Collectors.toList());
    }

    private static int overlapScore(Set<String> messageTokens, Set<String> entryTokens) {
        int score = 0;
        for (String token : entryTokens) {
            if (messageTokens.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static List<ResourceEntry> buildInstrumentIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        for (Instrument instrument : Instrument.getAll()) {
            if (instrument.getLabel() == null) {
                continue;
            }
            Set<String> tokens = new LinkedHashSet<>();
            for (String variant : generateVariants(instrument.getLabel())) {
                tokens.addAll(tokenize(variant));
            }
            addAcronym(tokens, instrument.getLabel());
            if (instrument.getLanguage() != null) {
                Language language = instrument.getLanguage();
                tokens.addAll(tokenize(language.getLabel()));
                tokens.addAll(tokenize(language.getNotation()));
            }
            entries.add(new ResourceEntry(instrument.getLabel(), instrument.getUri(), tokens));
        }
        return entries;
    }

    private static List<ResourceEntry> buildScaleIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        for (QuestionnaireScale scale : QuestionnaireScale.getAll()) {
            if (scale.getLabel() == null) {
                continue;
            }
            Set<String> tokens = new LinkedHashSet<>();
            for (String variant : generateVariants(scale.getLabel())) {
                tokens.addAll(tokenize(variant));
            }
            addAcronym(tokens, scale.getLabel());
            tokens.addAll(tokenize(scale.getUri()));
            entries.add(new ResourceEntry(scale.getLabel(), scale.getUri(), tokens));
        }
        return entries;
    }

    private static List<Candidate> toCandidates(List<ResourceEntry> entries, int limit) {
        return entries.stream()
                .limit(limit)
                .map(entry -> new Candidate(entry.uri(), entry.label()))
                .collect(Collectors.toList());
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

    private static List<String> basicTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!raw.isBlank()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static void addAcronym(Set<String> tokens, String label) {
        if (label == null) {
            return;
        }
        StringBuilder acronym = new StringBuilder();
        for (String part : label.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                acronym.append(part.charAt(0));
            }
        }
        if (acronym.length() >= 2) {
            tokens.add(acronym.toString().toLowerCase(Locale.ROOT));
        }
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String collapsed = lower.replaceAll("[^a-z0-9]", "");
        if (!collapsed.isBlank()) {
            tokens.add(collapsed);
        }
        for (String raw : lower.split("[^a-z0-9]+")) {
            if (raw.isBlank()) {
                continue;
            }
            tokens.add(raw);
            addAliases(tokens, raw);
        }
        return tokens;
    }

    private static void addAliases(Set<String> tokens, String token) {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        stack.push(token);
        seen.add(token);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            List<String> aliases = SPECIAL_TOKEN_ALIASES.get(current);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (seen.add(alias)) {
                        tokens.add(alias);
                        stack.push(alias);
                    }
                }
            }
            Set<String> codes = LANGUAGE_CODES_BY_LABEL_TOKEN.get(current);
            if (codes != null) {
                for (String code : codes) {
                    if (seen.add(code)) {
                        tokens.add(code);
                        stack.push(code);
                    }
                }
            }
            String label = LANGUAGE_LABEL_BY_CODE.get(current);
            if (label != null) {
                for (String aliasToken : label.split("[^a-z0-9]+")) {
                    String trimmed = aliasToken.trim();
                    if (!trimmed.isEmpty() && seen.add(trimmed)) {
                        tokens.add(trimmed);
                        stack.push(trimmed);
                    }
                }
            }
        }
    }

    private static String canonicalise(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record ResourceEntry(String label, String uri, Set<String> tokens) {
    }

    private record EntryScore(ResourceEntry entry, int score) {
    }
}
