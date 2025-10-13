package services.chat.intent;

import java.util.Objects;

public record ConceptInstrumentUsageIntent(String conceptUri) implements ChatIntent {

    public ConceptInstrumentUsageIntent {
        Objects.requireNonNull(conceptUri, "conceptUri must not be null");
    }

    @Override
    public String name() {
        return "CONCEPT_INSTRUMENT_USAGE";
    }

    @Override
    public String description() {
        return "Show instruments using a given item stem concept";
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
}
