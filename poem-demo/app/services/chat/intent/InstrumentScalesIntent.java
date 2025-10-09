package services.chat.intent;

import java.util.Objects;

public record InstrumentScalesIntent(String instrumentUri) implements ChatIntent {

    public InstrumentScalesIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_SCALES";
    }

    @Override
    public String description() {
        return "List scales associated with an instrument";
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
}
