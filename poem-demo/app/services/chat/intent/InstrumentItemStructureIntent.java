package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentItemStructureIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_ITEM_STRUCTURE";
    public static final String DESCRIPTION = "List ordered items for an instrument";

    public InstrumentItemStructureIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
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
            PREFIX sio:  <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?order ?item ?itemStem ?stemLabel
            WHERE {
              VALUES ?instrument { <%s> }

              ?instrument sio:SIO_000059 ?item .
              OPTIONAL { ?item sio:hasSource ?itemStem }
              OPTIONAL { ?itemStem rdfs:label ?stemLabel }

              OPTIONAL {
                ?item sio:SIO_000008 ?orderNode .
                ?orderNode sio:SIO_000668 ?instrument ;
                           sio:SIO_000300 ?order .
              }
            }
            ORDER BY ?order ?item
            """.formatted(instrumentUri);
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
        public Optional<ChatIntent> create(List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            if (instrumentUris == null || instrumentUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentItemStructureIntent(instrumentUris.get(0)));
        }
    }
}
