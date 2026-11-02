package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

import utils.POEMModel;

/** Scales and item count for one instrument, read from a consistent graph snapshot. */
public class InstrumentAbout {
    private final String uri;
    private final List<QuestionnaireScale> scales;
    private final long itemCount;

    private InstrumentAbout(String uri, List<QuestionnaireScale> scales, long itemCount) {
        this.uri = uri;
        this.scales = List.copyOf(scales);
        this.itemCount = itemCount;
    }

    public String getUri() {
        return uri;
    }

    public List<QuestionnaireScale> getScales() {
        return scales;
    }

    public long getItemCount() {
        return itemCount;
    }

    public static InstrumentAbout getByUri(String uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        try (var transaction = POEMModel.readTransaction()) {
            return getByUri(POEMModel.getDataset(), uri);
        }
    }

    // The caller owns the dataset's read transaction.
    static InstrumentAbout getByUri(Dataset dataset, String uri) {
        if (!dataset.getDefaultModel().contains(ResourceFactory.createResource(uri), RDF.type,
                ResourceFactory.createResource("http://purl.org/twc/poem/PsychometricQuestionnaire"))) {
            return null;
        }

        ParameterizedSparqlString scaleQuery = new ParameterizedSparqlString("""
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?scale (MIN(STR(?scaleLabel)) AS ?label)
            WHERE {
                ?instrument (sio:SIO_000008|sio:hasAttribute) ?scale .
                ?scale a poem:QuestionnaireScale .
                FILTER(isIRI(?scale))
                OPTIONAL { ?scale rdfs:label ?scaleLabel . FILTER(isLiteral(?scaleLabel)) }
            }
            GROUP BY ?scale
            ORDER BY LCASE(COALESCE(?label, STR(?scale))) STR(?scale)
            """);
        scaleQuery.setIri("instrument", uri);
        List<QuestionnaireScale> scales = new ArrayList<>();
        try (var execution = QueryExecutionFactory.create(scaleQuery.asQuery(), dataset)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var solution = results.nextSolution();
                QuestionnaireScale scale = new QuestionnaireScale();
                scale.setUri(solution.getResource("scale").getURI());
                if (solution.contains("label")) {
                    scale.setLabel(solution.getLiteral("label").getString());
                }
                scales.add(scale);
            }
        }

        ParameterizedSparqlString countQuery = new ParameterizedSparqlString("""
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT (COUNT(DISTINCT ?item) AS ?count)
            WHERE {
                ?instrument (sio:SIO_000059|sio:hasMember)+ ?item .
                ?item a vstoi:Item .
            }
            """);
        countQuery.setIri("instrument", uri);
        try (var execution = QueryExecutionFactory.create(countQuery.asQuery(), dataset)) {
            long itemCount = execution.execSelect().nextSolution().getLiteral("count").getLong();
            return new InstrumentAbout(uri, scales, itemCount);
        }
    }
}
