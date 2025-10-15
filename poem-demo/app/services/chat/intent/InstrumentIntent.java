package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Retrieves core metadata about an instrument (types, informant, language, item count).
 */
public record InstrumentIntent(String instrumentUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_METADATA";
    public static final String DESCRIPTION = "Retrieve instrument metadata (types, informant, language, item count)";

    public InstrumentIntent {
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
            PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sio:   <http://semanticscience.org/resource/>
            PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>
            PREFIX schema: <http://schema.org/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>

            SELECT ?instrument
                   (SAMPLE(?label) AS ?instrumentLabel)
                   (GROUP_CONCAT(DISTINCT COALESCE(?typeLabel, STR(?type)); separator=" | ") AS ?types)
                   (GROUP_CONCAT(DISTINCT COALESCE(?informantLabel, STR(?informant)); separator=" | ") AS ?informants)
                   (SAMPLE(?languageLabelLiteral) AS ?languageLabel)
                   (SAMPLE(?languageNotationLiteral) AS ?languageNotation)
                   (SAMPLE(?languageCountryLiteral) AS ?languageCountryCode)
                   (COUNT(DISTINCT ?itemForCount) AS ?itemCount)
            WHERE {
              VALUES ?instrument { <%s> }
              OPTIONAL { ?instrument rdfs:label ?label }
              OPTIONAL {
                ?instrument rdf:type ?type .
                OPTIONAL { ?type rdfs:label ?typeLabel }
              }
              OPTIONAL {
                ?instrument sio:SIO_000008 ?language .
                ?language rdf:type sio:SIO_000104 .
                OPTIONAL { ?language rdfs:label ?languageLabelLiteral }
                OPTIONAL { ?language skos:notation ?languageNotationLiteral }
                OPTIONAL { ?language schema:countryCode ?languageCountryLiteral }
              }
              OPTIONAL {
                ?instrument sio:SIO_000008 ?informant .
                ?informant a vstoi:Informant .
                OPTIONAL { ?informant rdfs:label ?informantLabel }
              }
              OPTIONAL {
                ?instrument sio:SIO_000059 ?itemForCount .
                FILTER(CONTAINS(STR(?itemForCount), "/item/"))
              }
            }
            GROUP BY ?instrument
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
        public Optional<ChatIntent> create(List<String> instrumentUris, List<String> scaleUris, List<String> conceptUris) {
            if (instrumentUris == null || instrumentUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentIntent(instrumentUris.get(0)));
        }
    }
}
