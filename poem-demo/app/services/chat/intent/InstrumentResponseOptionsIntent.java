package services.chat.intent;

import java.util.Objects;

public record InstrumentResponseOptionsIntent(String instrumentUri) implements ChatIntent {

    public InstrumentResponseOptionsIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_RESPONSE_OPTIONS";
    }

    @Override
    public String description() {
        return "Describe response options used by an instrument";
    }

    @Override
    public String toSparql() {
        return """
            PREFIX sio:   <http://semanticscience.org/resource/>
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>

            SELECT DISTINCT ?item ?codebook ?experienceLabel ?responseOption ?responseLabel ?order
            WHERE {
              VALUES ?instrument { <%s> }

              ?instrument sio:SIO_000059 ?item .
              ?item sio:SIO_000008 ?codebook .
              ?codebook a vstoi:Codebook ;
                        sio:SIO_000008 ?experience ;
                        sio:SIO_000059 ?responseOption .

              OPTIONAL { ?experience rdfs:label ?experienceLabel }
              OPTIONAL { ?responseOption rdfs:label ?responseLabel }

              OPTIONAL {
                ?responseOption sio:SIO_000008 ?optionOrderNode .
                ?optionOrderNode sio:SIO_000668 ?codebook ;
                                 sio:SIO_000300 ?order .
              }
            }
            ORDER BY ?item ?order
            """.formatted(instrumentUri);
    }
}
