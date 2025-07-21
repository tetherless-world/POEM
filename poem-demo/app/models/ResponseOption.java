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

public class ResponseOption extends models.Resource {

    private int position;
    private String value;

    public int getPosition() {
        return position;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public static List<ResponseOption> getByCodebook(String codebookUri) {
        System.out.println("ResponseOption.getByCodebook: " + codebookUri);
        Model model = POEMModel.getModel();
        ParameterizedSparqlString query = new ParameterizedSparqlString();
        query.setCommandText("""
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            PREFIX sio: <http://semanticscience.org/resource/>
            SELECT ?responseOption ?label ?pos ?val
            WHERE {
                ?responseOption a vstoi:ResponseOption .
                ?codebook a vstoi:Codebook .
                ?codebook sio:SIO_000059 ?responseOption .
                ?responseOption rdfs:label ?label .
                ?position a sio:SIO_000613 .
                ?value a sio:SIO_001385 .
                ?responseOption sio:SIO_000008 ?position .
                ?position sio:SIO_000668 ?codebook .
                ?position sio:SIO_000300 ?pos .
                ?responseOption sio:SIO_000008 ?value .
                ?value sio:SIO_000300 ?val .
            }
            ORDER BY ?pos
        """);
        query.setIri("codebook", codebookUri);
        List<ResponseOption> responseOptions = new ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(query.asQuery(), model)) {
            ResultSet results = qe.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Resource responseOptionResource = soln.getResource("responseOption");
                String label = soln.getLiteral("label").getString();
                ResponseOption responseOption = new ResponseOption();
                responseOption.setUri(responseOptionResource.getURI());
                responseOption.setLabel(label);
                responseOption.setPosition(soln.getLiteral("pos").getInt());
                responseOption.setValue(soln.getLiteral("val").getString());
                responseOptions.add(responseOption);
            }
        }
        return responseOptions;
    }
}
