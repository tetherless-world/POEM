package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import utils.POEMModel;

public class QuestionnaireScale extends models.Resource {
    public static List<QuestionnaireScale> getAll() {
        List<QuestionnaireScale> scales = new ArrayList<QuestionnaireScale>();
        Model model = POEMModel.getModel();
        ResIterator iter = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://purl.org/twc/poem/QuestionnaireScale"));
        while (iter.hasNext()) {
            Resource r = iter.nextResource();
            QuestionnaireScale scale = new QuestionnaireScale();
            scale.setUri(r.getURI());
            model.listObjectsOfProperty(r, RDFS.label).forEachRemaining(label -> {
                scale.setLabel(label.asLiteral().getString());
            });
            scales.add(scale);
        }
        return scales;
    }

    public static QuestionnaireScale getByUri(String uri) {
        QuestionnaireScale scale = new QuestionnaireScale();
        Model model = POEMModel.getModel();
        Resource resource = model.getResource(uri);
        scale.setUri(resource.getURI());
        scale.setLabel(resource.getProperty(RDFS.label).getString());
        return scale;
    }

    public static List<QuestionnaireScale> getByInstrument(Instrument instrument) {
        List<QuestionnaireScale> scales = new ArrayList<>();
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            SELECT DISTINCT ?uri ?label
            WHERE {
                ?instrument sio:SIO_000059 ?item .
                ?item sio:SIO_000253/sio:SIO_000253 ?itemStemConcept .
                ?uri sio:SIO_000059 ?itemStemConcept .
                ?uri rdfs:label ?label .
            }
        """);
        query.setIri("instrument", instrument.getUri());

        QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model);
        ResultSet results = qe.execSelect();
        while (results.hasNext()) {
            QuerySolution soln = results.nextSolution();
            QuestionnaireScale scale = new QuestionnaireScale();
            scale.setUri(soln.getResource("uri").getURI());
            scale.setLabel(soln.getLiteral("label").getString());
            scales.add(scale);
        }
        qe.close();

        return scales;
    }
}
