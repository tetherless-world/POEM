package services.chat.intent;

import java.util.Objects;

public record InstrumentLanguagesIntent(String instrumentUri) implements ChatIntent {

    public InstrumentLanguagesIntent {
        Objects.requireNonNull(instrumentUri, "instrumentUri must not be null");
    }

    @Override
    public String name() {
        return "INSTRUMENT_LANGUAGES";
    }

    @Override
    public String description() {
        return "Identify the language metadata for an instrument";
    }

    @Override
    public String toSparql() {
        return """
            PREFIX sio:   <http://semanticscience.org/resource/>
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX schema: <http://schema.org/>
            PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>

            SELECT DISTINCT ?language ?label ?isoCode ?country
            WHERE {
              VALUES ?instrument { <%s> }
              ?instrument sio:hasAttribute ?language .
              OPTIONAL { ?language rdfs:label ?label }
              OPTIONAL { ?language schema:countryCode ?country }
              OPTIONAL { ?language skos:notation ?isoCode }
            }
            """.formatted(instrumentUri);
    }
}
