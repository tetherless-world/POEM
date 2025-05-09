package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import utils.POEMModel;

public class Codebook extends models.Resource {

    public List<ResponseOption> responseOptions;

    public List<ResponseOption> getResponseOptions() {
        return responseOptions;
    }

    public void setResponseOptions(List<ResponseOption> responseOptions) {
        this.responseOptions = responseOptions;
    }

    public Codebook() {
        super();
    }

    public static Codebook getByItem(String itemUri) {
        System.out.println("Codebook.getByItem: " + itemUri);
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            PREFIX sio: <http://semanticscience.org/resource/>
            SELECT ?codebook ?label
            WHERE {
                ?item a vstoi:Item .
                ?item sio:SIO_000008 ?codebook .
                ?codebook a vstoi:Codebook .
                ?codebook rdfs:label ?label .
            }
        """);
        query.setIri("item", itemUri);
        Codebook codebook = new Codebook();
        try (QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model)) {
            ResultSet results = qe.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Resource codebookResource = soln.getResource("codebook");
                String label = soln.getLiteral("label").getString();
                codebook.setUri(codebookResource.getURI());
                codebook.setLabel(label);
                codebook.setResponseOptions(ResponseOption.getByCodebook(codebookResource.getURI()));
            }
        }
        return codebook;
    }
}
