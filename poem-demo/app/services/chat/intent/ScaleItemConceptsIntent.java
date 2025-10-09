package services.chat.intent;

import java.util.Objects;

public record ScaleItemConceptsIntent(String scaleUri) implements ChatIntent {

    public ScaleItemConceptsIntent {
        Objects.requireNonNull(scaleUri, "scaleUri must not be null");
    }

    @Override
    public String name() {
        return "SCALE_ITEM_CONCEPTS";
    }

    @Override
    public String description() {
        return "Enumerate item concepts for a scale";
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
}
