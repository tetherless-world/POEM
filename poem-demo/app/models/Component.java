package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

import utils.POEMModel;

public class Component extends models.Resource {
    
    private int position;

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Component() {
        super();
    }

    public static List<Component> getByInstrument(String instrumentUri) {
        System.out.println("Component.getByInstrument: " + instrumentUri);
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT ?component ?label ?pos
            WHERE {
                ?instrument a poem:PsychometricQuestionnaire .
                ?instrument sio:SIO_000059 ?component .
                ?component a vstoi:Component .
                ?component rdfs:label ?label .
                ?component sio:SIO_000008 ?position .
                ?position a sio:SIO_000613 .
                ?position sio:SIO_000668 ?instrument .
                ?position sio:SIO_000300 ?pos .
            }
            ORDER BY ?pos
        """);
        query.setIri("instrument", instrumentUri);
        QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model);
        ResultSet results = qe.execSelect();
        List<Component> components = new ArrayList<Component>();
        while (results.hasNext()) {
            System.out.println("Component found:");
            QuerySolution soln = results.nextSolution();
            Component component = new Component();
            component.setUri(soln.getResource("component").getURI());
            component.setLabel(soln.getLiteral("label").getString());
            component.setPosition(soln.getLiteral("pos").getInt());
            components.add(component);
        }
        return components;
    }
}
