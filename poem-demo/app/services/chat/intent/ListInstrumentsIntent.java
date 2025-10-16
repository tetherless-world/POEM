package services.chat.intent;

import java.util.List;
import java.util.Optional;

/**
 * Lists instruments with core metadata without requiring a specific instrument URI.
 */
public record ListInstrumentsIntent(int limit) implements ChatIntent {

    public static final String NAME = "LIST_INSTRUMENTS";
    public static final String DESCRIPTION = "List instruments with language, informant and item counts. Use this intent when the user asks for instruments but does not specify a particular instrument, with or without attributes. For example, 'List some instruments' or 'What instruments are in English?'";

    private static final int DEFAULT_LIMIT = 25;

    public ListInstrumentsIntent() {
        this(DEFAULT_LIMIT);
    }

    public ListInstrumentsIntent {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
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
                   (GROUP_CONCAT(DISTINCT COALESCE(?informantLabel, STR(?informant)); separator=" | ") AS ?informants)
                   (GROUP_CONCAT(DISTINCT COALESCE(?languageDisplay, STR(?language)); separator=" | ") AS ?languages)
                   (COUNT(DISTINCT ?item) AS ?itemCount)
            WHERE {
              ?instrument rdf:type ?type .
              FILTER(?type IN (<http://hl7.org/fhir/Questionnaire>, <http://purl.org/twc/poem/PsychometricQuestionnaire>))
              OPTIONAL { ?instrument rdfs:label ?label }

              OPTIONAL {
                ?instrument sio:SIO_000008 ?informant .
                ?informant a vstoi:Informant .
                OPTIONAL { ?informant rdfs:label ?informantLabel }
              }

              OPTIONAL {
                ?instrument sio:SIO_000008 ?language .
                ?language rdf:type sio:SIO_000104 .
                OPTIONAL { ?language rdfs:label ?languageLabelLiteral }
                OPTIONAL { ?language skos:notation ?languageNotationLiteral }
                OPTIONAL { ?language schema:countryCode ?languageCountryLiteral }
                BIND(
                    CONCAT(
                        COALESCE(STR(?languageLabelLiteral), STR(?language)),
                        IF(BOUND(?languageNotationLiteral), CONCAT(" [", STR(?languageNotationLiteral), "]"), ""),
                        IF(BOUND(?languageCountryLiteral), CONCAT(" (", STR(?languageCountryLiteral), ")"), "")
                    ) AS ?languageDisplay)
              }

              OPTIONAL {
                ?instrument sio:SIO_000059 ?item .
                FILTER(CONTAINS(STR(?item), "/item/"))
              }
            }
            GROUP BY ?instrument
            ORDER BY LCASE(SAMPLE(?label))
            LIMIT %d
            """.formatted(limit);
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
            // if ((instrumentUris != null && !instrumentUris.isEmpty())
            //         || (collectionUris != null && !collectionUris.isEmpty())
            //         || (scaleUris != null && !scaleUris.isEmpty())
            //         || (conceptUris != null && !conceptUris.isEmpty())) {
            //     return Optional.empty();
            // }
            return Optional.of(new ListInstrumentsIntent());
        }
    }
}
