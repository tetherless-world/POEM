package services.chat.intent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lists instruments, optionally filtered by collection memberships, languages, scales, and item counts.
 */
public record ListInstrumentsIntent(List<String> collectionUris,
                                    List<String> languageUris,
                                    List<String> scaleUris,
                                    Integer itemCountEquals,
                                    int limit) implements ChatIntent {

    public static final String NAME = "LIST_INSTRUMENTS";
    public static final String DESCRIPTION = "List instruments with optional filters (collection, language, scale, item count)";

    private static final int DEFAULT_LIMIT = 25;

    public ListInstrumentsIntent() {
        this(List.of(), List.of(), List.of(), null, DEFAULT_LIMIT);
    }

    public ListInstrumentsIntent {
        Objects.requireNonNull(collectionUris, "collectionUris must not be null");
        Objects.requireNonNull(languageUris, "languageUris must not be null");
        Objects.requireNonNull(scaleUris, "scaleUris must not be null");
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
        StringBuilder query = new StringBuilder();
        query.append("PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n")
             .append("PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>\n")
             .append("PREFIX sio:   <http://semanticscience.org/resource/>\n")
             .append("PREFIX skos:  <http://www.w3.org/2004/02/skos/core#>\n")
             .append("PREFIX schema: <http://schema.org/>\n")
             .append("PREFIX vstoi: <http://purl.org/twc/vstoi/>\n\n")
             .append("SELECT ?instrument\n")
             .append("       (SAMPLE(?label) AS ?instrumentLabel)\n")
             .append("       (GROUP_CONCAT(DISTINCT COALESCE(?informantLabel, STR(?informant)); separator=\" | \") AS ?informants)\n")
             .append("       (GROUP_CONCAT(DISTINCT COALESCE(?languageDisplay, STR(?language)); separator=\" | \") AS ?languages)\n")
             .append("       (COUNT(DISTINCT ?item) AS ?itemCount)\n")
             .append("WHERE {\n")
             .append("  ?instrument rdf:type ?type .\n")
             .append("  FILTER(?type IN (<http://hl7.org/fhir/Questionnaire>, <http://purl.org/twc/poem/PsychometricQuestionnaire>))\n")
             .append("  OPTIONAL { ?instrument rdfs:label ?label }\n\n");

        if (!collectionUris.isEmpty()) {
            query.append("  VALUES ?collectionFilter { ")
                 .append(collectionUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?collectionFilter sio:SIO_000059 ?instrument .\n\n");
        }

        if (!languageUris.isEmpty()) {
            query.append("  VALUES ?languageRequired { ")
                 .append(languageUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?instrument sio:SIO_000008 ?languageRequired .\n")
                 .append("  ?languageRequired rdf:type sio:SIO_000104 .\n\n");
        }

        if (!scaleUris.isEmpty()) {
            query.append("  VALUES ?scaleRequired { ")
                 .append(scaleUris.stream().map(uri -> "<" + uri + ">").collect(Collectors.joining(" ")))
                 .append(" }\n")
                 .append("  ?instrument sio:SIO_000059 ?itemForScaleFilter .\n")
                 .append("  FILTER(CONTAINS(STR(?itemForScaleFilter), \"/item/\"))\n")
                 .append("  ?itemForScaleFilter sio:SIO_000253 ?stemFilter .\n")
                 .append("  ?stemFilter sio:SIO_000253 ?conceptFilter .\n")
                 .append("  ?scaleRequired sio:SIO_000059 ?conceptFilter .\n\n");
        }

        query.append("  OPTIONAL {\n")
             .append("    ?instrument sio:SIO_000008 ?informant .\n")
             .append("    ?informant a vstoi:Informant .\n")
             .append("    OPTIONAL { ?informant rdfs:label ?informantLabel }\n")
             .append("  }\n\n")
             .append("  OPTIONAL {\n")
             .append("    ?instrument sio:SIO_000008 ?language .\n")
             .append("    ?language rdf:type sio:SIO_000104 .\n")
             .append("    OPTIONAL { ?language rdfs:label ?languageLabelLiteral }\n")
             .append("    OPTIONAL { ?language skos:notation ?languageNotationLiteral }\n")
             .append("    OPTIONAL { ?language schema:countryCode ?languageCountryLiteral }\n")
             .append("    BIND(\n")
             .append("        CONCAT(\n")
             .append("            COALESCE(STR(?languageLabelLiteral), STR(?language)),\n")
             .append("            IF(BOUND(?languageNotationLiteral), CONCAT(\" [\", STR(?languageNotationLiteral), \"]\"), \"\"),\n")
             .append("            IF(BOUND(?languageCountryLiteral), CONCAT(\" (\", STR(?languageCountryLiteral), \")\"), \"\")\n")
             .append("        ) AS ?languageDisplay)\n")
             .append("  }\n\n")
             .append("  OPTIONAL {\n")
             .append("    ?instrument sio:SIO_000059 ?item .\n")
             .append("    FILTER(CONTAINS(STR(?item), \"/item/\"))\n")
             .append("  }\n")
             .append("}\n")
             .append("GROUP BY ?instrument\n");

        if (itemCountEquals != null) {
            query.append("HAVING (COUNT(DISTINCT ?item) = ")
                 .append(itemCountEquals)
                 .append(")\n");
        }

        query.append("ORDER BY LCASE(SAMPLE(?label))\n")
             .append("LIMIT ").append(limit);

        return query.toString();
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
            if (instrumentUris != null && !instrumentUris.isEmpty()) {
                return Optional.empty();
            }
            List<String> collections = collectionUris == null ? List.of() : List.copyOf(collectionUris);
            List<String> scales = scaleUris == null ? List.of() : List.copyOf(scaleUris);
            if (conceptUris != null && !conceptUris.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ListInstrumentsIntent(collections, List.of(), scales, null, DEFAULT_LIMIT));
        }
    }
}
