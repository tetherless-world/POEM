package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ConceptLocalizedStemsIntent(String conceptUri) implements ChatIntent {

    public static final String NAME = "CONCEPT_LOCALIZED_STEMS";
    public static final String DESCRIPTION = "List localised stems for an item concept";

    public ConceptLocalizedStemsIntent {
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

            SELECT DISTINCT ?itemStem ?stemLabel ?language
            WHERE {
              VALUES ?concept { <%s> }
              ?itemStem sio:SIO_000253 ?concept .
              OPTIONAL { ?itemStem rdfs:label ?stemLabel }
              OPTIONAL { ?itemStem <http://purl.org/dc/terms/language> ?language }
            }
            ORDER BY ?language ?stemLabel
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
        public Optional<ChatIntent> create(List<String> collectionUris, List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            if (conceptUris == null || conceptUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ConceptLocalizedStemsIntent(conceptUris.get(0)));
        }
    }
}
