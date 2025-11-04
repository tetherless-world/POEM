package services.chat;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;

import play.Logger;
import utils.POEMModel;

import services.chat.intent.ChatIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Executes SPARQL queries associated with chat intents against the POEM graph.
 */
@Singleton
public class ChatKnowledgeService {

    private static final Logger.ALogger logger = Logger.of(ChatKnowledgeService.class);

    @Inject
    public ChatKnowledgeService() {
    }

    public ChatQueryResult executeIntent(ChatIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        String sparql = intent.toSparql();
        //logger.debug("Executing SPARQL for intent {}: {}", intent.name(), sparql);
        Query query = QueryFactory.create(sparql);

        try (QueryExecution execution = QueryExecutionFactory.create(query, POEMModel.getModel())) {
            ResultSet resultSet = execution.execSelect();
            List<String> vars = resultSet.getResultVars();
            List<Map<String, String>> rows = new ArrayList<>();

            while (resultSet.hasNext()) {
                QuerySolution solution = resultSet.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (String var : vars) {
                    RDFNode node = solution.get(var);
                    row.put(var, renderNode(node));
                }
                rows.add(row);
            }

            return new ChatQueryResult(vars, rows);
        }
    }

    private static String renderNode(RDFNode node) {
        if (node == null) {
            return null;
        }
        if (node.isLiteral()) {
            return node.asLiteral().getString();
        }
        if (node.isResource()) {
            org.apache.jena.rdf.model.Resource resource = node.asResource();
            return resource.isAnon() ? resource.getId().getLabelString() : resource.getURI();
        }
        return node.toString();
    }
}
