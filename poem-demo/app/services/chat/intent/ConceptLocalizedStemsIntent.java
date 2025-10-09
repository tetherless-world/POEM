package services.chat.intent;

import java.util.Objects;

public record ConceptLocalizedStemsIntent(String conceptUri) implements ChatIntent {

    public ConceptLocalizedStemsIntent {
        Objects.requireNonNull(conceptUri, "conceptUri must not be null");
    }

    @Override
    public String name() {
        return "CONCEPT_LOCALIZED_STEMS";
    }

    @Override
    public String description() {
        return "List localised stems for an item concept";
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
}
