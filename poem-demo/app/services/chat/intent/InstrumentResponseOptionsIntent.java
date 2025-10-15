package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentResponseOptionsIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_RESPONSE_OPTIONS";
    public static final String DESCRIPTION = "Describe response options used by an instrument";

    public InstrumentResponseOptionsIntent {
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
            if (instrumentUris == null || instrumentUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentResponseOptionsIntent(instrumentUris.get(0)));
        }
    }
}
