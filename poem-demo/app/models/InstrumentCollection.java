package models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import utils.POEMModel;

public class InstrumentCollection extends models.Resource {

    private static final org.apache.jena.rdf.model.Resource TYPE =
        ResourceFactory.createResource("http://purl.org/twc/poem/InstrumentCollection");

    private final List<ConstructMatch> constructMatches = new ArrayList<>();

    public record ConstructMatch(String termUri, String matchType) {}

    public List<ConstructMatch> getConstructMatches() {
        return List.copyOf(constructMatches);
    }

    /** Returns each collection with a member instrument matching any selected SNOMED term, with its label and match reasons. */
    public static List<InstrumentCollection> getByConstructs(List<String> snomedTermUris) {
        List<String> terms = normalizeConstructTerms(snomedTermUris);
        if (terms.isEmpty()) {
            return List.of();
        }
        try (var transaction = POEMModel.readTransaction()) {
            return getByConstructs(POEMModel.getDataset(), terms);
        }
    }

    private static List<String> normalizeConstructTerms(List<String> termUris) {
        if (termUris == null) {
            throw new IllegalArgumentException("SNOMED terms are required");
        }
        var terms = new LinkedHashSet<String>();
        for (String uri : termUris) {
            if (uri == null || !uri.trim().matches("http://snomed\\.info/id/[0-9]+")) {
                throw new IllegalArgumentException("Each term must be a SNOMED URI such as http://snomed.info/id/21897009");
            }
            terms.add(uri.trim());
        }
        return List.copyOf(terms);
    }

    // The caller owns the dataset's read transaction.
    static List<InstrumentCollection> getByConstructs(Dataset dataset, List<String> snomedTermUris) {
        List<String> terms = normalizeConstructTerms(snomedTermUris);
        if (terms.isEmpty()) {
            return List.of();
        }
        StringBuilder values = new StringBuilder("VALUES ?term { ");
        for (int index = 0; index < terms.size(); index++) {
            values.append("?selectedTerm").append(index).append(' ');
        }
        values.append('}');
        ParameterizedSparqlString query = new ParameterizedSparqlString("""
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT DISTINCT ?collection ?term ?matchType
            WHERE {
            """ + values + """

                ?collection a poem:InstrumentCollection ;
                    (sio:SIO_000059|sio:hasMember) ?instrument .
                FILTER(isIRI(?collection))
                ?instrument a poem:PsychometricQuestionnaire .
                FILTER(isIRI(?instrument))
                {
                    ?instrument (sio:SIO_000008|sio:hasAttribute) ?scale .
                    ?scale a poem:QuestionnaireScale ; (sio:SIO_000332|sio:isAbout) ?term .
                    BIND("scale" AS ?matchType)
                }
                UNION
                {
                    ?instrument (sio:SIO_000059|sio:hasMember)+ ?item .
                    ?item a vstoi:Item ; (sio:SIO_000253|sio:hasSource) ?stem .
                    ?stem (sio:SIO_000253|sio:hasSource) ?concept .
                    ?concept a poem:ItemStemConcept ; (sio:SIO_000332|sio:isAbout) ?term .
                    BIND("item" AS ?matchType)
                }
            }
            ORDER BY ?collection ?term ?matchType
            """);
        for (int index = 0; index < terms.size(); index++) {
            query.setIri("selectedTerm" + index, terms.get(index));
        }

        Map<String, InstrumentCollection> matches = new LinkedHashMap<>();
        try (var execution = QueryExecutionFactory.create(query.asQuery(), dataset)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var solution = results.nextSolution();
                String uri = solution.getResource("collection").getURI();
                InstrumentCollection collection = matches.computeIfAbsent(uri, key -> {
                    InstrumentCollection match = new InstrumentCollection();
                    match.setUri(key);
                    var labels = dataset.getDefaultModel().listObjectsOfProperty(
                        ResourceFactory.createResource(key), RDFS.label);
                    try {
                        match.setLabel(labels.toList().stream().filter(node -> node.isLiteral())
                            .map(node -> node.asLiteral().getString()).sorted().findFirst().orElse(null));
                    } finally {
                        labels.close();
                    }
                    return match;
                });
                collection.constructMatches.add(new ConstructMatch(
                    solution.getResource("term").getURI(), solution.getLiteral("matchType").getString()));
            }
        }
        List<InstrumentCollection> collections = new ArrayList<>(matches.values());
        collections.sort(Comparator.comparing(InstrumentCollection::getLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(InstrumentCollection::getUri));
        return collections;
    }

    private String definition;
    private String notation;

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getNotation() {
        return notation;
    }

    public void setNotation(String notation) {
        this.notation = notation;
    }

    public static List<InstrumentCollection> getAll() {
        try (var transaction = POEMModel.readTransaction()) {
            List<InstrumentCollection> collections = new ArrayList<>();
            var resources = POEMModel.getModel().listSubjectsWithProperty(RDF.type, TYPE);
            try {
                while (resources.hasNext()) {
                    var resource = resources.nextResource();
                    if (resource.isURIResource()) {
                        collections.add(fromResource(resource));
                    }
                }
            } finally {
                resources.close();
            }
            return collections;
        }
    }

    /** Returns null when the URI does not identify an instrument collection. */
    public static InstrumentCollection getByUri(String uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        try (var transaction = POEMModel.readTransaction()) {
            Model model = POEMModel.getModel();
            var resource = model.getResource(uri);
            return resource.hasProperty(RDF.type, TYPE) ? fromResource(resource) : null;
        }
    }

    /** Returns distinct member instruments with their URI and label. */
    public List<Instrument> getInstruments() {
        Objects.requireNonNull(getUri(), "collection URI must not be null");
        try (var transaction = POEMModel.readTransaction()) {
            ParameterizedSparqlString query = new ParameterizedSparqlString("""
                PREFIX poem: <http://purl.org/twc/poem/>
                PREFIX sio: <http://semanticscience.org/resource/>
                SELECT DISTINCT ?instrument
                WHERE {
                    ?collection a poem:InstrumentCollection ;
                        (sio:SIO_000059|sio:hasMember) ?instrument .
                    ?instrument a poem:PsychometricQuestionnaire .
                    FILTER(isIRI(?instrument))
                }
                ORDER BY ?instrument
                """);
            query.setIri("collection", getUri());
            List<Instrument> instruments = new ArrayList<>();
            try (var execution = QueryExecutionFactory.create(query.asQuery(), POEMModel.getDataset())) {
                var results = execution.execSelect();
                while (results.hasNext()) {
                    var resource = results.nextSolution().getResource("instrument");
                    Instrument instrument = new Instrument();
                    instrument.setUri(resource.getURI());
                    instrument.setLabel(literalValue(resource, RDFS.label));
                    instruments.add(instrument);
                }
            }
            return instruments;
        }
    }

    private static InstrumentCollection fromResource(org.apache.jena.rdf.model.Resource resource) {
        InstrumentCollection collection = new InstrumentCollection();
        collection.setUri(resource.getURI());
        collection.setLabel(literalValue(resource, RDFS.label));
        collection.setDefinition(literalValue(resource, SKOS.definition));
        collection.setNotation(literalValue(resource, SKOS.notation));
        return collection;
    }

    private static String literalValue(org.apache.jena.rdf.model.Resource resource, Property property) {
        var statement = resource.getProperty(property);
        return statement != null && statement.getObject().isLiteral() ? statement.getString() : null;
    }
}
