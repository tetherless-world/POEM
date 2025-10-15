package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentQuestionTextsIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_QUESTION_TEXTS";
    public static final String DESCRIPTION = "Retrieve item stems for an instrument";

    public InstrumentQuestionTextsIntent {
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

            SELECT DISTINCT ?order ?item ?itemStem ?stemLabel ?stemLanguage
            WHERE {
              VALUES ?instrument { <%s> }

              ?instrument sio:SIO_000059 ?item .
              ?item sio:hasSource ?itemStem .

              OPTIONAL {
                ?item sio:SIO_000008 ?orderNode .
                ?orderNode sio:SIO_000668 ?instrument ;
                           sio:SIO_000300 ?order .
              }

              OPTIONAL { ?itemStem rdfs:label ?stemLabel }
              OPTIONAL { ?itemStem <http://purl.org/dc/terms/language> ?stemLanguage }
            }
            ORDER BY ?order ?item
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
            return Optional.of(new InstrumentQuestionTextsIntent(instrumentUris.get(0)));
        }
    }
}
