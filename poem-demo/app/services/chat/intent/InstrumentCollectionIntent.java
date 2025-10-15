package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Retrieves aggregated metadata for an instrument collection.
 */
public record InstrumentCollectionIntent(String collectionUri) implements ChatIntent {

    public static final String NAME = "INSTRUMENT_COLLECTION_METADATA";
    public static final String DESCRIPTION = "Retrieve metadata for an instrument collection";

    public InstrumentCollectionIntent {
        Objects.requireNonNull(collectionUri, "collectionUri must not be null");
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

            SELECT ?collection
                   (SAMPLE(?label) AS ?collectionLabel)
                   (SAMPLE(?definition) AS ?collectionDefinition)
                   (COUNT(DISTINCT ?memberInstrument) AS ?memberInstrumentCount)
                   (COUNT(DISTINCT ?memberItemForCount) AS ?memberItemCount)
                   (GROUP_CONCAT(DISTINCT ?languageDisplay; separator=" | ") AS ?languages)
                   (GROUP_CONCAT(DISTINCT ?respondentDisplay; separator=" | ") AS ?respondentTypes)
                   (GROUP_CONCAT(DISTINCT ?scaleDisplay; separator=" | ") AS ?scales)
            WHERE {
              VALUES ?collection { <%s> }
              OPTIONAL { ?collection rdfs:label ?label }
              OPTIONAL { ?collection skos:definition ?definition }

              ?collection sio:SIO_000059 ?memberInstrument .
              FILTER(CONTAINS(STR(?memberInstrument), "/instrument/"))

              OPTIONAL {
                ?memberInstrument sio:SIO_000059 ?memberItemForCount .
                FILTER(CONTAINS(STR(?memberItemForCount), "/item/"))
              }

              OPTIONAL {
                ?memberInstrument sio:SIO_000008 ?language .
                ?language rdf:type sio:SIO_000104 .
                OPTIONAL { ?language rdfs:label ?languageLabelLiteral }
                OPTIONAL { ?language skos:notation ?languageNotationLiteral }
                OPTIONAL { ?language schema:countryCode ?languageCountryLiteral }
                BIND(IF(BOUND(?languageLabelLiteral), STR(?languageLabelLiteral), STR(?language)) AS ?languageLabelValue)
                BIND(IF(BOUND(?languageNotationLiteral), STR(?languageNotationLiteral), "") AS ?languageNotationValue)
                BIND(IF(BOUND(?languageCountryLiteral), STR(?languageCountryLiteral), "") AS ?languageCountryValue)
                BIND(CONCAT(
                        ?languageLabelValue,
                        IF(?languageNotationValue != "", CONCAT(" [", ?languageNotationValue, "]"), ""),
                        IF(?languageCountryValue != "", CONCAT(" (", ?languageCountryValue, ")"), "")
                    ) AS ?languageDisplay)
              }

              OPTIONAL {
                ?memberInstrument sio:SIO_000008 ?respondent .
                ?respondent a vstoi:Informant .
                OPTIONAL { ?respondent rdfs:label ?respondentLabel }
                BIND(IF(BOUND(?respondentLabel), STR(?respondentLabel), STR(?respondent)) AS ?respondentDisplay)
              }

              OPTIONAL {
                ?memberInstrument sio:SIO_000059 ?memberItemForScale .
                FILTER(CONTAINS(STR(?memberItemForScale), "/item/"))
                ?memberItemForScale sio:SIO_000253 ?itemStem .
                ?itemStem sio:SIO_000253 ?itemConcept .
                ?scale sio:SIO_000059 ?itemConcept .
                OPTIONAL { ?scale rdfs:label ?scaleLabel }
                BIND(IF(BOUND(?scaleLabel), STR(?scaleLabel), STR(?scale)) AS ?scaleDisplay)
              }
            }
            GROUP BY ?collection
            """.formatted(collectionUri);
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
            if (collectionUris == null || collectionUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new InstrumentCollectionIntent(collectionUris.get(0)));
        }
    }
}
