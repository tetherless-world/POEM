package services.chat.intent;

import java.util.List;
import java.util.Optional;

/**
 * Lists instrument collections with basic metadata.
 */
public record ListInstrumentCollectionsIntent(int limit) implements ChatIntent {

    private static final play.Logger.ALogger logger = play.Logger.of(ListInstrumentCollectionsIntent.class);
    public static final String NAME = "LIST_INSTRUMENT_COLLECTIONS";
    public static final String DESCRIPTION = "List instrument collections with definitions and counts";

    private static final int DEFAULT_LIMIT = 25;

    public ListInstrumentCollectionsIntent() {
        this(DEFAULT_LIMIT);
    }

    public ListInstrumentCollectionsIntent {
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
        return """
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX sio:  <http://semanticscience.org/resource/>

            SELECT ?collection
                   (SAMPLE(?label) AS ?collectionLabel)
                   (SAMPLE(?definition) AS ?collectionDefinition)
                   (COUNT(DISTINCT ?memberInstrument) AS ?memberInstrumentCount)
            WHERE {
              ?collection a poem:InstrumentCollection .
              OPTIONAL { ?collection rdfs:label ?label }
              OPTIONAL { ?collection skos:definition ?definition }
              OPTIONAL {
                ?collection sio:SIO_000059 ?memberInstrument .
                FILTER(CONTAINS(STR(?memberInstrument), "/instrument/"))
              }
            }
            GROUP BY ?collection
            ORDER BY LCASE(SAMPLE(?label))
            LIMIT %d
            """.formatted(limit);
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
            // if ((collectionUris != null && !collectionUris.isEmpty())
            //         || (instrumentUris != null && !instrumentUris.isEmpty())
            //         || (scaleUris != null && !scaleUris.isEmpty())
            //         || (conceptUris != null && !conceptUris.isEmpty())) {
            //     return Optional.empty();
            // }
            return Optional.of(new ListInstrumentCollectionsIntent());
        }
    }
}
