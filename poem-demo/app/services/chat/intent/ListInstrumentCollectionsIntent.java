package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lists instrument collections with optional filters.
 */
public record ListInstrumentCollectionsIntent(List<String> languageUris,
                                              List<String> scaleUris,
                                              int limit) implements ChatIntent {

    public static final String NAME = "LIST_INSTRUMENT_COLLECTIONS";
    public static final String DESCRIPTION = "List instrument collections, optionally filtered by language or scale";

    private static final int DEFAULT_LIMIT = 25;

    public ListInstrumentCollectionsIntent() {
        this(List.of(), List.of(), DEFAULT_LIMIT);
    }

    public ListInstrumentCollectionsIntent {
        Objects.requireNonNull(languageUris, "languageUris must not be null");
        Objects.requireNonNull(scaleUris, "scaleUris must not be null");
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
             .append("PREFIX sio:  <http://semanticscience.org/resource/>\n")
             .append("PREFIX schema: <http://schema.org/>\n")
             .append("SELECT ?collection\n")
             .append("       (SAMPLE(?label) AS ?collectionLabel)\n")
             .append("       (SAMPLE(?definition) AS ?collectionDefinition)\n")
             .append("       (COUNT(DISTINCT ?memberInstrument) AS ?memberInstrumentCount)\n")
             .append("WHERE {\n")
             .append("  ?collection a poem:InstrumentCollection .\n")
             .append("  OPTIONAL { ?collection rdfs:label ?label }\n")
             .append("  OPTIONAL { ?collection skos:definition ?definition }\n\n")
             .append("  OPTIONAL {\n")
             .append("    ?collection sio:SIO_000059 ?memberInstrument .\n")
             .append("    FILTER(CONTAINS(STR(?memberInstrument), \"/instrument/\"))\n")
             .append("  }\n\n");

        if (!languageUris.isEmpty()) {
            query.append("  VALUES ?languageCollectionFilter { ")
                 .append(languageUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?collection sio:SIO_000059 ?instrumentForLanguage .\n")
                 .append("  FILTER(CONTAINS(STR(?instrumentForLanguage), \"/instrument/\"))\n")
                 .append("  ?instrumentForLanguage sio:SIO_000008 ?languageCollectionFilter .\n")
                 .append("  ?languageCollectionFilter rdf:type sio:SIO_000104 .\n\n");
        }

        if (!scaleUris.isEmpty()) {
            query.append("  VALUES ?scaleCollectionFilter { ")
                 .append(scaleUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?collection sio:SIO_000059 ?instrumentForScale .\n")
                 .append("  FILTER(CONTAINS(STR(?instrumentForScale), \"/instrument/\"))\n")
                 .append("  ?instrumentForScale sio:SIO_000059 ?itemForScale .\n")
                 .append("  FILTER(CONTAINS(STR(?itemForScale), \"/item/\"))\n")
                 .append("  ?itemForScale sio:SIO_000253 ?stemForScale .\n")
                 .append("  ?stemForScale sio:SIO_000253 ?conceptForScale .\n")
                 .append("  ?scaleCollectionFilter sio:SIO_000059 ?conceptForScale .\n\n");
        }

        query.append("}\n")
             .append("GROUP BY ?collection\n")
             .append("ORDER BY LCASE(SAMPLE(?label))\n")
             .append("LIMIT ").append(limit);

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
            if ((instrumentUris != null && !instrumentUris.isEmpty())
                    || (collectionUris != null && !collectionUris.isEmpty())
                    || (conceptUris != null && !conceptUris.isEmpty())) {
                return Optional.empty();
            }
            List<String> scales = scaleUris == null ? List.of() : List.copyOf(scaleUris);
            return Optional.of(new ListInstrumentCollectionsIntent(List.of(), scales, DEFAULT_LIMIT));
        }
    }
}
