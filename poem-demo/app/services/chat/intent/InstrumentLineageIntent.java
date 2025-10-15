package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InstrumentLineageIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_LINEAGE";
    public static final String DESCRIPTION = "Summarise provenance of an instrument";

    public InstrumentLineageIntent {
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
            PREFIX prov: <http://www.w3.org/ns/prov#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT DISTINCT ?activity ?activityLabel ?endedAt ?relationship ?relatedInstrument ?relatedLabel
            WHERE {
              VALUES ?instrument { <%s> }
              {
                ?instrument prov:wasGeneratedBy ?activity .
                OPTIONAL { ?activity prov:used ?relatedInstrument }
                BIND("used" AS ?relationship)
              }
              UNION
              {
                ?activity prov:used ?instrument ;
                          prov:generated ?relatedInstrument .
                BIND("generated" AS ?relationship)
              }
              OPTIONAL { ?activity rdfs:label ?activityLabel }
              OPTIONAL { ?activity prov:endedAtTime ?endedAt }
              OPTIONAL { ?relatedInstrument rdfs:label ?relatedLabel }
            }
            ORDER BY ?activity ?relationship ?relatedLabel
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
            return Optional.of(new InstrumentLineageIntent(instrumentUris.get(0)));
        }
    }
}
