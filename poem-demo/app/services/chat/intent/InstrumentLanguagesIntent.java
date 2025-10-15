package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentLanguagesIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_LANGUAGES";
    public static final String DESCRIPTION = "Identify the language metadata for an instrument";

    public InstrumentLanguagesIntent {
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
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX schema: <http://schema.org/>
            PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>

            SELECT DISTINCT ?language ?label ?isoCode ?country
            WHERE {
              VALUES ?instrument { <%s> }
              ?instrument sio:hasAttribute ?language .
              OPTIONAL { ?language rdfs:label ?label }
              OPTIONAL { ?language schema:countryCode ?country }
              OPTIONAL { ?language skos:notation ?isoCode }
            }
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
            return Optional.of(new InstrumentLanguagesIntent(instrumentUris.get(0)));
        }
    }
}
