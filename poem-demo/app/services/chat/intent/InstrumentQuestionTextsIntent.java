package services.chat.intent;

import java.util.Objects;

public record InstrumentQuestionTextsIntent(String instrumentUri) implements ChatIntent {

    public InstrumentQuestionTextsIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_QUESTION_TEXTS";
    }

    @Override
    public String description() {
        return "Retrieve item stems for an instrument";
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
}
