package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ScaleNotationIntent(String scaleUri) implements ChatIntent {

    public static final String NAME = "SCALE_NOTATION";
    public static final String DESCRIPTION = "Show label and notation for a scale";

    public ScaleNotationIntent {
        Objects.requireNonNull(scaleUri, "scaleUri must not be null");
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
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

            SELECT ?scale ?label ?notation
            WHERE {
              VALUES ?scale { <%s> }
              OPTIONAL { ?scale rdfs:label ?label }
              OPTIONAL { ?scale skos:notation ?notation }
            }
            """.formatted(scaleUri);
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
            if (scaleUris == null || scaleUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ScaleNotationIntent(scaleUris.get(0)));
        }
    }
}
