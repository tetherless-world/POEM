package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ConceptInstrumentUsageIntent(String conceptUri) implements ChatIntent {

    public static final String NAME = "CONCEPT_INSTRUMENT_USAGE";
    public static final String DESCRIPTION = "Show instruments using a given concept";

    public ConceptInstrumentUsageIntent {
        Objects.requireNonNull(conceptUri, "conceptUri must not be null");
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

            SELECT DISTINCT ?instrument ?instrumentLabel
            WHERE {
              VALUES ?concept { <%s> }
              ?instrument sio:SIO_000059 ?item .
              ?item sio:hasSource ?itemStem .
              ?itemStem sio:SIO_000253 ?concept .
              OPTIONAL { ?instrument rdfs:label ?instrumentLabel }
            }
            ORDER BY ?instrumentLabel
            """.formatted(conceptUri);
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
            if (conceptUris == null || conceptUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ConceptInstrumentUsageIntent(conceptUris.get(0)));
        }
    }
}
