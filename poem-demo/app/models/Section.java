package models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

import utils.POEMModel;

public class Section extends models.Resource {

    private int position;
    private List<Section> sections;
    private List<Item> items;

    public Section() {
        super();
        this.sections = new ArrayList<Section>();
        this.items = new ArrayList<Item>();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public List<Section> getSections() {
        return sections;
    }

    public void setSections(List<Section> sections) {
        this.sections = sections;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static List<Section> getByInstrument(String instrumentUri) {
        return getByParent(instrumentUri, new HashSet<String>());
    }

    public static List<Section> getBySection(String sectionUri) {
        return getByParent(sectionUri, new HashSet<String>());
    }

    public static List<Item> getItemsBySection(String sectionUri) {
        System.out.println("Section.getItemsBySection: " + sectionUri);
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT DISTINCT ?item ?label ?pos
            WHERE {
                ?section (sio:SIO_000059|sio:SIO_000059) ?item .
                ?item a vstoi:Item .
                ?item sio:SIO_000253/rdfs:label ?label .
                ?item sio:SIO_000008 ?position .
                ?position a sio:SIO_000613 .
                ?position (sio:SIO_000668|sio:SIO_000668) ?section .
                ?position sio:SIO_000300 ?pos .
            }
            ORDER BY ?pos ?item
        """);
        query.setIri("section", sectionUri);

        List<Item> items = new ArrayList<Item>();
        try (QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model)) {
            ResultSet results = qe.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Item item = new Item();
                item.setUri(soln.getResource("item").getURI());
                item.setLabel(soln.getLiteral("label").getString());
                item.setPosition(soln.getLiteral("pos").getInt());
                item.setCodebook(Codebook.getByItem(item.getUri()));
                items.add(item);
            }
        }
        return items;
    }

    private static List<Section> getByParent(String parentUri, Set<String> visited) {
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            SELECT DISTINCT ?section ?label ?pos
            WHERE {
                ?parent (sio:SIO_000059|sio:SIO_000059) ?section .
                ?section a poem:QuestionnaireSection .
                ?section sio:SIO_000008 ?position .
                ?position a sio:SIO_000613 .
                ?position (sio:SIO_000668|sio:SIO_000668) ?parent .
                ?position sio:SIO_000300 ?pos .
                OPTIONAL { ?section rdfs:label ?label . }
            }
            ORDER BY ?pos ?section
        """);
        query.setIri("parent", parentUri);

        List<Section> sections = new ArrayList<Section>();
        try (QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model)) {
            ResultSet results = qe.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Section section = new Section();
                section.setUri(soln.getResource("section").getURI());
                section.setLabel(soln.contains("label") ? soln.getLiteral("label").getString() : section.getUri());
                section.setPosition(soln.getLiteral("pos").getInt());

                if (!visited.contains(section.getUri())) {
                    Set<String> childVisited = new HashSet<String>(visited);
                    childVisited.add(section.getUri());
                    section.setSections(getByParent(section.getUri(), childVisited));
                    section.setItems(getItemsBySection(section.getUri()));
                }
                sections.add(section);
            }
        }
        return sections;
    }
}
