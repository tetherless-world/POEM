package services.chat.intent;

import java.util.Objects;

public record InstrumentLineageIntent(String instrumentUri) implements ChatIntent {

    public InstrumentLineageIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_LINEAGE";
    }

    @Override
    public String description() {
        return "Summarise provenance of an instrument";
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
}
