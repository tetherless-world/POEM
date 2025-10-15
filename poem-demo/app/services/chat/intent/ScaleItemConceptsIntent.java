package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ScaleItemConceptsIntent(String scaleUri) implements ChatIntent {

    public static final String NAME = "SCALE_ITEM_CONCEPTS";
    public static final String DESCRIPTION = "Enumerate item concepts for a scale";

    public ScaleItemConceptsIntent {
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
            PREFIX sio:  <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?concept ?conceptLabel
            WHERE {
              VALUES ?scale { <%s> }
              ?scale sio:SIO_000059 ?concept .
              OPTIONAL { ?concept rdfs:label ?conceptLabel }
            }
            ORDER BY ?conceptLabel
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
        public Optional<ChatIntent> create(List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            if (scaleUris == null || scaleUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ScaleItemConceptsIntent(scaleUris.get(0)));
        }
    }
}
