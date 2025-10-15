package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentExperienceComparisonIntent(List<String> instrumentUris) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_EXPERIENCE_COMPARISON";
    public static final String DESCRIPTION = "Compare response experiences across instruments";

    public InstrumentExperienceComparisonIntent {
        Objects.requireNonNull(instrumentUris, "instrumentUris must not be null");
        if (instrumentUris.size() < 2) {
            throw new IllegalArgumentException("instrumentUris must contain at least two entries");
        }
        instrumentUris = List.copyOf(instrumentUris);
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
            if (instrumentUris == null || instrumentUris.size() < 2) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentExperienceComparisonIntent(instrumentUris));
        }
    }
}
