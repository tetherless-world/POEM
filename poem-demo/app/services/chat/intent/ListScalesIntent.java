package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lists psychometric scales with optional filters.
 */
public record ListScalesIntent(List<String> instrumentUris,
                               List<String> languageUris,
                               int limit) implements ChatIntent {

    public static final String NAME = "LIST_SCALES";
    public static final String DESCRIPTION = "List psychometric scales, optionally filtered by instrument or language";

    private static final int DEFAULT_LIMIT = 25;

    public ListScalesIntent() {
        this(List.of(), List.of(), DEFAULT_LIMIT);
    }

    public ListScalesIntent {
        Objects.requireNonNull(instrumentUris, "instrumentUris must not be null");
        Objects.requireNonNull(languageUris, "languageUris must not be null");
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String toSparql() {
        StringBuilder query = new StringBuilder();
        query.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n")
             .append("PREFIX poem: <http://purl.org/twc/poem/>\n")
             .append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n")
             .append("PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n")
             .append("SELECT ?scale (SAMPLE(?label) AS ?scaleLabel) (SAMPLE(?notation) AS ?scaleNotation) WHERE {\n")
             .append("  ?scale rdf:type poem:QuestionnaireScale .\n")
             .append("  OPTIONAL { ?scale rdfs:label ?label }\n")
             .append("  OPTIONAL { ?scale skos:notation ?notation }\n");

        if (!instrumentUris.isEmpty()) {
            query.append("  VALUES ?instrumentFilter { ")
                 .append(instrumentUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?instrumentFilter sio:SIO_000008 ?scale .\n");
        }

        if (!languageUris.isEmpty()) {
            query.append("  VALUES ?languageFilter { ")
                 .append(languageUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?scale sio:SIO_000008 ?languageFilter .\n")
                 .append("  ?languageFilter rdf:type sio:SIO_000104 .\n");
        }

        query.append("}\nGROUP BY ?scale\nORDER BY LCASE(SAMPLE(?label))\nLIMIT ").append(limit);
        return query.toString();
    }

    public static final class Provider implements IntentProvider {
        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return DESCRIPTION;
        }

        @Override
        public Optional<ChatIntent> create(List<String> collectionUris, List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            // Only create if no specific instrument/collection/scale/concept is requested
            if ((collectionUris != null && !collectionUris.isEmpty())
                    || (scaleUris != null && !scaleUris.isEmpty())
                    || (conceptUris != null && !conceptUris.isEmpty())) {
                return Optional.empty();
            }
            List<String> instruments = instrumentUris == null ? List.of() : List.copyOf(instrumentUris);
            List<String> languages = List.of(); // Could be extended to support language filter
            return Optional.of(new ListScalesIntent(instruments, languages, DEFAULT_LIMIT));
        }
    }
}