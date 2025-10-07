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

public class Item extends models.Resource {

    private int position;
    private Codebook codebook;

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Codebook getCodebook() {
        return codebook;
    }

    public void setCodebook(Codebook codebook) {
        this.codebook = codebook;
    }

    public Item() {
        super();
    }

    public static List<Item> getByInstrument(String instrumentUri) {
        System.out.println("Item.getByInstrument: " + instrumentUri);
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT ?item ?label ?pos
            WHERE {
                ?instrument a poem:PsychometricQuestionnaire .
                ?instrument sio:SIO_000059 ?item .
                ?item a vstoi:Item .
                ?item sio:SIO_000253/rdfs:label ?label .
                ?item sio:SIO_000008 ?position .
                ?position a sio:SIO_000613 .
                ?position sio:SIO_000668 ?instrument .
                ?position sio:SIO_000300 ?pos .
            }
            ORDER BY ?pos
        """);
        query.setIri("instrument", instrumentUri);
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
}
