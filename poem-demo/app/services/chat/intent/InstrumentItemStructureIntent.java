package services.chat.intent;

import java.util.Objects;

public record InstrumentItemStructureIntent(String instrumentUri) implements ChatIntent {

    public InstrumentItemStructureIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_ITEM_STRUCTURE";
    }

    @Override
    public String description() {
        return "List ordered items for an instrument";
    }

    @Override
    public String toSparql() {
        return """
            PREFIX sio:  <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?order ?item ?itemStem ?stemLabel
            WHERE {
              VALUES ?instrument { <%s> }

              ?instrument sio:SIO_000059 ?item .
              OPTIONAL { ?item sio:hasSource ?itemStem }
              OPTIONAL { ?itemStem rdfs:label ?stemLabel }

              OPTIONAL {
                ?item sio:SIO_000008 ?orderNode .
                ?orderNode sio:SIO_000668 ?instrument ;
                           sio:SIO_000300 ?order .
              }
            }
            ORDER BY ?order ?item
            """.formatted(instrumentUri);
    }
}
