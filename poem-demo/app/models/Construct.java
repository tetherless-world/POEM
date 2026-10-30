package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecutionFactory;

import utils.POEMModel;

/** A SNOMED term used as the subject of an item stem concept or scale. */
public class Construct extends models.Resource {

    private String prefLabel;

    public String getPrefLabel() {
        return prefLabel;
    }

    public void setPrefLabel(String prefLabel) {
        this.prefLabel = prefLabel;
    }

    public static List<Construct> getAll() {
        try (var transaction = POEMModel.readTransaction()) {
            return getAll(POEMModel.getDataset());
        }
    }

    // The caller owns the read transaction for this dataset.
    static List<Construct> getAll(Dataset dataset) {
        // Choose labels deterministically when a term has several, keeping one row per URI.
        String query = """
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

            SELECT ?term (MIN(STR(?termLabel)) AS ?label)
                         (MIN(STR(?termPrefLabel)) AS ?prefLabel)
            WHERE {
                {
                    SELECT DISTINCT ?term WHERE {
                        VALUES ?type { poem:ItemStemConcept poem:QuestionnaireScale }
                        ?instance a ?type ; sio:SIO_000332 ?term .
                        FILTER(isIRI(?term) && STRSTARTS(STR(?term), "http://snomed.info/id/"))
                    }
                }
                OPTIONAL { ?term rdfs:label ?termLabel . FILTER(isLiteral(?termLabel)) }
                OPTIONAL { ?term skos:prefLabel ?termPrefLabel . FILTER(isLiteral(?termPrefLabel)) }
            }
            GROUP BY ?term
            ORDER BY LCASE(COALESCE(?prefLabel, ?label, STR(?term))) STR(?term)
            """;

        List<Construct> constructs = new ArrayList<>();
        try (var execution = QueryExecutionFactory.create(query, dataset)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var solution = results.nextSolution();
                Construct construct = new Construct();
                construct.setUri(solution.getResource("term").getURI());
                if (solution.contains("label")) {
                    construct.setLabel(solution.getLiteral("label").getString());
                }
                if (solution.contains("prefLabel")) {
                    construct.setPrefLabel(solution.getLiteral("prefLabel").getString());
                }
                constructs.add(construct);
            }
        }
        return constructs;
    }
}
