package services.chat.intent;

import java.util.List;
import java.util.Objects;

public record InstrumentExperienceComparisonIntent(List<String> instrumentUris) implements ChatIntent {

    public InstrumentExperienceComparisonIntent {
        Objects.requireNonNull(instrumentUris, "instrumentUris must not be null");
        if (instrumentUris.size() < 2) {
            throw new IllegalArgumentException("instrumentUris must contain at least two entries");
        }
        instrumentUris = List.copyOf(instrumentUris);
    }

    @Override
    public String name() {
        return "INSTRUMENT_EXPERIENCE_COMPARISON";
    }

    @Override
    public String description() {
        return "Compare response experiences across instruments";
    }

    @Override
    public String toSparql() {
        String first = instrumentUris.get(0);
        String second = instrumentUris.get(1);
        return """
            PREFIX sio:  <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?instrument ?experience ?experienceLabel
            WHERE {
              VALUES ?instrument { <%s> <%s> }
              ?instrument sio:SIO_000059 ?item .
              ?item sio:SIO_000008 ?codebook .
              ?codebook sio:SIO_000008 ?experience .
              OPTIONAL { ?experience rdfs:label ?experienceLabel }
            }
            ORDER BY ?instrument ?experienceLabel
            """.formatted(first, second);
    }
}
