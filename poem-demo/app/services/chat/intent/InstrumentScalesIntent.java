package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentScalesIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_SCALES";
    public static final String DESCRIPTION = "List scales associated with an instrument";

    public InstrumentScalesIntent {
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
            PREFIX sio:   <http://semanticscience.org/resource/>
            PREFIX poem:  <http://purl.org/twc/poem/>
            PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>

            SELECT DISTINCT ?scale ?scaleLabel ?notation
            WHERE {
              VALUES ?instrument { <%s> }
              ?instrument sio:SIO_000008 ?scale .
              ?scale rdf:type poem:QuestionnaireScale .
              OPTIONAL { ?scale rdfs:label ?scaleLabel }
              OPTIONAL { ?scale skos:notation ?notation }
            }
            ORDER BY ?scaleLabel
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
        public Optional<ChatIntent> create(List<String> collectionUris, List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            if (instrumentUris == null || instrumentUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentScalesIntent(instrumentUris.get(0)));
        }
    }
}
