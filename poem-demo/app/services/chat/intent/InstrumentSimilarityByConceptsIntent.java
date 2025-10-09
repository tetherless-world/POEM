package services.chat.intent;

import java.util.List;
import java.util.Objects;

public record InstrumentSimilarityByConceptsIntent(List<String> instrumentUris) implements ChatIntent {

    public InstrumentSimilarityByConceptsIntent {
        Objects.requireNonNull(instrumentUris, "instrumentUris must not be null");
        if (instrumentUris.size() < 2) {
            throw new IllegalArgumentException("instrumentUris must contain at least two entries");
        }
        instrumentUris = List.copyOf(instrumentUris);
    }

    @Override
    public String name() {
        return "INSTRUMENT_SIMILARITY_BY_CONCEPTS";
    }

    @Override
    public String description() {
        return "Highlight shared item concepts across instruments";
    }

    @Override
    public String toSparql() {
        String first = instrumentUris.get(0);
        String second = instrumentUris.get(1);
        return """
            PREFIX sio:  <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?concept ?conceptLabel
            WHERE {
              VALUES (?instrumentA ?instrumentB) { (<%s> <%s>) }
              ?instrumentA sio:SIO_000059 ?itemA .
              ?instrumentB sio:SIO_000059 ?itemB .
              ?itemA sio:hasSource ?stemA .
              ?itemB sio:hasSource ?stemB .
              ?stemA sio:SIO_000253 ?concept .
              ?stemB sio:SIO_000253 ?concept .
              OPTIONAL { ?concept rdfs:label ?conceptLabel }
            }
            ORDER BY ?conceptLabel
            """.formatted(first, second);
    }
}
