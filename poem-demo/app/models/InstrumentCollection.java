package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
