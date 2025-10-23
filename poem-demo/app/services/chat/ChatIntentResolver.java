package services.chat;

import models.Instrument;
import models.Language;
import models.QuestionnaireScale;
import models.chat.ChatMessage;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import play.Logger;
import services.chat.classifier.IntentClassifier;
import services.chat.classifier.IntentClassifier.Candidate;
import services.chat.classifier.IntentClassifier.ClassificationContext;
import services.chat.classifier.NoopIntentClassifier;
import services.chat.intent.ChatIntent;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final int MAX_FILTER_MATCHES = 5;
    private static final Pattern ITEM_COUNT_PATTERN =
            Pattern.compile("\\b(\\d{1,3})\\s*(?:-|\\s)?(?:item|items|question|questions)\\b");

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
    private final List<ResourceEntry> collectionEntries;
    private final List<ResourceEntry> instrumentEntries;
    private final List<ResourceEntry> scaleEntries;
    private final List<ResourceEntry> languageEntries;

    public ChatIntentResolver() {
        this(new NoopIntentClassifier());
    }

    @Inject
    public ChatIntentResolver(IntentClassifier classifier) {
        this.classifier = classifier;
        this.collectionEntries = buildInstrumentCollectionIndex();
        this.instrumentEntries = buildInstrumentIndex();
        this.scaleEntries = buildScaleIndex();
        this.languageEntries = buildLanguageIndex();
    }

    public Optional<ChatIntent> resolve(String latestMessage, List<ChatMessage> history) {
        String contextText = buildContextText(latestMessage, history);
        logger.debug("Context text for intent resolution: '{}'", contextText);
        if (contextText.isBlank()) {
            return Optional.empty();
        }

        String normalised = contextText.toLowerCase(Locale.ROOT);
        Set<String> messageTokens = tokenize(contextText);

        List<ResourceEntry> collectionMatches = findMatches(messageTokens, collectionEntries);
        List<ResourceEntry> instrumentMatches = findMatches(messageTokens, instrumentEntries);
        List<ResourceEntry> scaleMatches = findMatches(messageTokens, scaleEntries);
        List<ResourceEntry> languageMatches = findMatches(messageTokens, languageEntries);
        // logger.debug("Collection matches: {}", collectionMatches.stream().map(e -> e.label() + "@" + e.uri()).collect(Collectors.toList()));
        // logger.debug("Instrument matches: {}", instrumentMatches.stream().map(e -> e.label() + "@" + e.uri()).collect(Collectors.toList()));
        // logger.debug("Scale matches: {}", scaleMatches.stream().map(e -> e.label() + "@" + e.uri()).collect(Collectors.toList()));
        // logger.debug("Language matches: {}", languageMatches.stream().map(e -> e.label() + "@" + e.uri()).collect(Collectors.toList()));

        ClassificationContext classificationContext = new ClassificationContext(
                toCandidates(collectionMatches, 5),
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

        List<String> collections = collectionMatches.stream()
                .map(ResourceEntry::uri)
                .collect(Collectors.toList());
        List<String> instruments = instrumentMatches.stream()
                .map(ResourceEntry::uri)
                .collect(Collectors.toList());
        List<String> scales = scaleMatches.stream()
                .map(ResourceEntry::uri)
                .collect(Collectors.toList());
        List<String> collectionFilters = limitUris(collectionMatches, MAX_FILTER_MATCHES);
        List<String> languageFilters = limitUris(languageMatches, MAX_FILTER_MATCHES);
        List<String> scaleFilters = limitUris(scaleMatches, MAX_FILTER_MATCHES);
        Integer itemCountEquals = extractItemCount(contextText);

        if (wantsCollectionList(normalised)) {
            return Optional.of(new ListInstrumentCollectionsIntent(languageFilters, scaleFilters, 0));
        }

        if (wantsInstrumentList(normalised)) {
            return Optional.of(new ListInstrumentsIntent(collectionFilters, languageFilters, scaleFilters, itemCountEquals, 0));
        }

        if (!collections.isEmpty()) {
            String collection = collections.get(0);
            if (mentionsCollection(normalised) || wantsCollectionMetadata(normalised)) {
                // If the message explicitly mentions a well-known collection keyword, prefer it.
                if (normalised.contains("rcads")) {
                    for (ResourceEntry entry : collectionMatches) {
                        if (entry.label().toLowerCase(Locale.ROOT).contains("rcads")) {
                            return Optional.of(new InstrumentCollectionIntent(entry.uri()));
                        }
                    }
                }
                // prefer a collection whose tokens overlap the message tokens (e.g., 'rcads'),
                // but ignore generic tokens like 'collection' which match all entries.
                Set<String> generic = Set.of("collection", "collections", "family", "families", "instrumentcollection", "instrumentfamily", "instrument", "instruments");
                for (ResourceEntry entry : collectionMatches) {
                    for (String t : entry.tokens()) {
                        if (generic.contains(t)) {
                            continue;
                        }
                        if (messageTokens.contains(t)) {
                            return Optional.of(new InstrumentCollectionIntent(entry.uri()));
                        }
                    }
                }
                return Optional.of(new InstrumentCollectionIntent(collection));
            }
        }

        // Heuristic for generic scale questions (always triggers for matching phrases)
        if (containsAny(normalised, "psychometric scale", "psychometric scales", "list scales", "what are scales", "show scales", "which scales", "scale list")) {
            return Optional.of(new services.chat.intent.ListScalesIntent(List.of(), List.of(), 0));
        }

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

        // If the user explicitly references a scale (e.g., 'Social Phobia (9.1)' or asks about item concepts),
        // prefer scale-based intents even if an instrument match exists.
        boolean explicitScaleRef = contextText.contains("(") || containsAny(normalised, "item concept", "compose", "made of", "make up", "includes");
        if (explicitScaleRef && !scales.isEmpty()) {
            String scale = scales.get(0);
            return Optional.of(new ScaleItemConceptsIntent(scale));
        }

        if (!instruments.isEmpty()) {
            // Always return InstrumentScalesIntent with first instrument match for scale-related queries
            if (containsAny(normalised, "scale", "subscale") && !instruments.isEmpty()) {
                String bestInstrument = instruments.get(0);
                logger.debug("Heuristic matched instrument {} for message '{}'", bestInstrument, latestMessage);
                return Optional.of(new InstrumentScalesIntent(bestInstrument));
            }
            // Try to find the best instrument match by maximizing token overlap for other queries
            String bestInstrument = null;
            int bestScore = -1;
            for (ResourceEntry entry : instrumentEntries) {
                int score = overlapScore(messageTokens, entry.tokens());
                if (score > bestScore && instruments.contains(entry.uri())) {
                    bestScore = score;
                    bestInstrument = entry.uri();
                } else if (score > bestScore && score > 0) {
                    bestScore = score;
                    bestInstrument = entry.uri();
                }
            }
            if (bestInstrument == null) {
                bestInstrument = instruments.stream()
                    .filter(uri -> uri != null && !uri.isBlank())
                    .findFirst()
                    .orElse(null);
            }
            if (bestInstrument != null) {
                logger.debug("Heuristic matched instrument {} for message '{}'", bestInstrument, latestMessage);

                if (wantsMetadata(normalised)) {
                    return Optional.of(new InstrumentIntent(bestInstrument));
                }

                if (containsAny(normalised, "psychometric scale", "psychometric scales", "list scales", "what are scales", "show scales", "which scales", "scale list")) {
                    return Optional.of(new services.chat.intent.ListScalesIntent(List.of(), List.of(), 0));
                }
                if (containsAny(normalised, "language", "translation", "translated", "locale")) {
                    return Optional.of(new InstrumentLanguagesIntent(bestInstrument));
                }

                if (containsAny(normalised, "when", "created", "origin", "based on", "source", "generated", "derived")) {
                    return Optional.of(new InstrumentLineageIntent(bestInstrument));
                }

                if (containsAny(normalised, "response option", "response options", "response scale", "codebook", "rating scale")) {
                    return Optional.of(new InstrumentResponseOptionsIntent(bestInstrument));
                }

                if (containsAny(normalised, "item text", "question text", "question wording", "item wording", "which items")) {
                    return Optional.of(new InstrumentQuestionTextsIntent(bestInstrument));
                }

                if (containsAny(normalised, "order", "item order", "structure", "how many items", "number of items", "item count")) {
                    return Optional.of(new InstrumentItemStructureIntent(bestInstrument));
                }
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

    private static boolean wantsMetadata(String normalised) {
        return containsAny(normalised,
                "metadata",
                "information",
                "details",
                "overview",
                "summary",
                "describe",
                "description",
                "tell me about",
                "what is",
                "informant",
                "type",
                "item count",
                "number of items",
                "items total");
    }

    private static boolean wantsCollectionList(String normalised) {
        return containsAny(normalised,
                "list instrument collections",
                "list all instrument collections",
                "show instrument collections",
                "list collections",
                "which instrument collections",
                "which collections",
                "what instrument collections exist",
                "instrument collection list",
                "list instrument families",
                "show instrument families",
                "all instrument collections");
    }

    private static boolean wantsInstrumentList(String normalised) {
        return containsAny(normalised,
                "list all instruments",
                "list instruments",
                "show instruments",
                "what instruments",
                "which instruments",
                "instrument list",
                "catalog of instruments",
                "all instruments");
    }

    private static boolean wantsCollectionMetadata(String normalised) {
        return containsAny(normalised,
                "collection",
                "collections",
                "instrument collection",
                "family",
                "families",
                "instrument family");
    }

    private static boolean mentionsCollection(String normalised) {
        return containsAny(normalised, "collection", "collections", "family", "families", "instrument collection", "instrument family");
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
        scores.sort(Comparator.<EntryScore, Integer>comparing(EntryScore::score).reversed()
                .thenComparing((EntryScore es) -> {
                    String l = es.entry().label().toLowerCase(Locale.ROOT);
                    String base = l.replaceAll("(?:(?:-y-)|(?:-cg-)|(?:-t-)|(?:-a-)|(?:-en)|(?:-fi)|(?:-is)|(?:-nl))", "");
                    return base.replaceAll("[^a-z0-9]+", "");
                })
                .thenComparingInt(es -> es.entry().label().toLowerCase(Locale.ROOT).contains("-y-") ? 0 : 1)
                .thenComparing(es -> es.entry().label().toLowerCase(Locale.ROOT)));
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

    private static List<ResourceEntry> buildInstrumentCollectionIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        String queryText = """
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

            SELECT ?collection ?label ?definition
            WHERE {
              ?collection a poem:InstrumentCollection .
              OPTIONAL { ?collection rdfs:label ?label }
              OPTIONAL { ?collection skos:definition ?definition }
            }
        """;

        Model model = POEMModel.getModel();
        Query query = QueryFactory.create(queryText);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution sol = results.next();
                Resource resource = sol.getResource("collection");
                String uri = resource.getURI();
                String label = sol.contains("label") ? sol.getLiteral("label").getString() : uri;
                Set<String> tokens = new LinkedHashSet<>();
                for (String variant : generateVariants(label)) {
                    tokens.addAll(tokenize(variant));
                }
                if (sol.contains("definition")) {
                    tokens.addAll(basicTokens(sol.getLiteral("definition").getString()));
                }
                tokens.add("collection");
                tokens.add("collections");
                tokens.add("family");
                tokens.add("families");
                tokens.add("instrumentcollection");
                tokens.add("instrumentfamily");
                entries.add(new ResourceEntry(label, uri, tokens));
            }
        }
        entries.sort(Comparator.comparing(e -> e.label().toLowerCase(Locale.ROOT)));
        return entries;
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
        entries.sort(Comparator.comparing(e -> e.label().toLowerCase(Locale.ROOT)));
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
        entries.sort(Comparator.comparing(e -> e.label().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private static List<Candidate> toCandidates(List<ResourceEntry> entries, int limit) {
        return entries.stream()
                .limit(limit)
                .map(entry -> new Candidate(entry.uri(), entry.label()))
                .collect(Collectors.toList());
    }

    private static List<ResourceEntry> buildLanguageIndex() {
        List<ResourceEntry> entries = new ArrayList<>();
        for (Language language : Language.getAll()) {
            if (language.getUri() == null) {
                continue;
            }
            Set<String> tokens = new LinkedHashSet<>();
            if (language.getLabel() != null) {
                for (String variant : generateVariants(language.getLabel())) {
                    tokens.addAll(tokenize(variant));
                }
            }
            if (language.getNotation() != null) {
                tokens.add(language.getNotation().toLowerCase(Locale.ROOT));
                tokens.add(language.getNotation().replace("-", "").toLowerCase(Locale.ROOT));
                for (String part : language.getNotation().split("[-_\\s]+")) {
                    if (!part.isBlank()) {
                        tokens.add(part.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (language.getBCP47() != null) {
                tokens.add(language.getBCP47().toLowerCase(Locale.ROOT));
                tokens.add(language.getBCP47().replace("-", "").toLowerCase(Locale.ROOT));
            }
            if (language.getCountryCode() != null) {
                tokens.add(language.getCountryCode().toLowerCase(Locale.ROOT));
            }
            if (tokens.isEmpty()) {
                tokens.add(language.getUri());
            }
            String label = language.getLabelWithCountry();
            entries.add(new ResourceEntry(label != null ? label : language.getUri(), language.getUri(), tokens));
        }
        entries.sort(Comparator.comparing(e -> e.label().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private static List<String> limitUris(List<ResourceEntry> entries, int limit) {
        if (entries.isEmpty() || limit <= 0) {
            return List.of();
        }
        LinkedHashSet<String> uris = new LinkedHashSet<>();
        for (ResourceEntry entry : entries) {
            if (uris.add(entry.uri()) && uris.size() >= limit) {
                break;
            }
        }
        return List.copyOf(uris);
    }

    private static Integer extractItemCount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = ITEM_COUNT_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
