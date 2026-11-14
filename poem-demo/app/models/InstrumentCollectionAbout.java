package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import utils.POEMModel;

/** Collection metadata and distinct member summaries from one graph snapshot. */
public class InstrumentCollectionAbout extends Resource {
    private String definition;
    private String notation;
    private long instrumentCount;
    private List<Resource> scales = List.of();
    private List<Resource> languages = List.of();

    public String getDefinition() { return definition; }
    public String getNotation() { return notation; }
    public long getInstrumentCount() { return instrumentCount; }
    public List<Resource> getScales() { return scales; }
    public List<Resource> getLanguages() { return languages; }

    public static InstrumentCollectionAbout getByUri(String uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        try (var transaction = POEMModel.readTransaction()) {
            return getByUri(POEMModel.getDataset(), uri);
        }
    }

    // The caller owns the dataset's read transaction.
    static InstrumentCollectionAbout getByUri(Dataset dataset, String uri) {
        var resource = dataset.getDefaultModel().getResource(uri);
        if (!resource.hasProperty(RDF.type,
                ResourceFactory.createResource("http://purl.org/twc/poem/InstrumentCollection"))) return null;

        InstrumentCollectionAbout about = new InstrumentCollectionAbout();
        about.setUri(uri);
        about.setLabel(literal(resource, RDFS.label));
        about.definition = literal(resource, SKOS.definition);
        if (about.definition == null) about.definition = literal(resource, RDFS.comment);
        about.notation = literal(resource, SKOS.notation);

        ParameterizedSparqlString count = new ParameterizedSparqlString(PREFIXES + """
            SELECT (COUNT(DISTINCT ?instrument) AS ?count) WHERE {
            """ + MEMBERS + "}");
        count.setIri("collection", uri);
        try (var execution = QueryExecutionFactory.create(count.asQuery(), dataset)) {
            about.instrumentCount = execution.execSelect().nextSolution().getLiteral("count").getLong();
        }
        about.scales = attributes(dataset, uri, "poem:QuestionnaireScale");
        about.languages = attributes(dataset, uri, "sio:SIO_000104");
        return about;
    }

    private static final String PREFIXES = """
        PREFIX poem: <http://purl.org/twc/poem/>
        PREFIX sio: <http://semanticscience.org/resource/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        """;
    private static final String MEMBERS = """
        ?collection (sio:SIO_000059|sio:hasMember) ?instrument .
        ?instrument a poem:PsychometricQuestionnaire .
        FILTER(isIRI(?instrument))
        """;

    private static List<Resource> attributes(Dataset dataset, String uri, String type) {
        ParameterizedSparqlString query = new ParameterizedSparqlString(PREFIXES + """
            SELECT ?attribute (MIN(STR(?name)) AS ?label) (MIN(STR(?code)) AS ?notation)
            WHERE {
            """ + MEMBERS + """
                ?instrument (sio:SIO_000008|sio:hasAttribute) ?attribute .
                FILTER(isIRI(?attribute))
            """ + "?attribute a " + type + " .\n" + """
                OPTIONAL { ?attribute rdfs:label ?name . FILTER(isLiteral(?name)) }
                OPTIONAL { ?attribute skos:notation ?code . FILTER(isLiteral(?code)) }
            }
            GROUP BY ?attribute
            ORDER BY LCASE(COALESCE(?label, ?notation, STR(?attribute))) STR(?attribute)
            """);
        query.setIri("collection", uri);
        List<Resource> attributes = new ArrayList<>();
        try (var execution = QueryExecutionFactory.create(query.asQuery(), dataset)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var solution = results.nextSolution();
                Resource attribute = new Resource();
                attribute.setUri(solution.getResource("attribute").getURI());
                if (solution.contains("label")) attribute.setLabel(solution.getLiteral("label").getString());
                else if (solution.contains("notation")) attribute.setLabel(solution.getLiteral("notation").getString());
                attributes.add(attribute);
            }
        }
        return List.copyOf(attributes);
    }

    private static String literal(org.apache.jena.rdf.model.Resource resource,
            org.apache.jena.rdf.model.Property property) {
        var values = resource.listProperties(property);
        try {
            return values.toList().stream().filter(statement -> statement.getObject().isLiteral())
                .map(statement -> statement.getString()).sorted().findFirst().orElse(null);
        } finally {
            values.close();
        }
    }
}
