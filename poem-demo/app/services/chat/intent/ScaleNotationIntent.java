package services.chat.intent;

import java.util.Objects;

public record ScaleNotationIntent(String scaleUri) implements ChatIntent {

    public ScaleNotationIntent {
        Objects.requireNonNull(scaleUri, "scaleUri must not be null");
    }

    @Override
    public String name() {
        return "SCALE_NOTATION";
    }

    @Override
    public String description() {
        return "Show label and notation for a scale";
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
}
