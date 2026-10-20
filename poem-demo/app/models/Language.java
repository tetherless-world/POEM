package models;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import utils.POEMModel;

public class Language extends models.Resource {
    private String notation;

    public Language(String notation) {
        this.notation = notation;
    }

    public String getNotation() {
        return notation;
    }

    public String getBCP47() {
        if (notation == null) {
            return null;
        }
        return notation.toLowerCase();
    }

    public String getDisplayLabel() {
        String label = getLabel();
        return label != null ? label : notation;
    }

    public static List<Language> getAll() {
        try (var transaction = POEMModel.readTransaction()) {
            List<Language> languages = new ArrayList<>();

            String queryString =
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX sio: <http://semanticscience.org/resource/> " +
                "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
                "SELECT ?language ?notation ?label " +
                "WHERE { " +
                "  ?language rdf:type sio:SIO_000104 . " +
                "  ?language skos:notation ?notation . " +
                "  ?language rdfs:label ?label . " +
                "}";

            try (QueryExecution qexec = QueryExecutionFactory.create(queryString, POEMModel.getDataset())) {
                ResultSet results = qexec.execSelect();
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    String notation = soln.getLiteral("notation").getString();
                    String label = soln.getLiteral("label").getString();
                    String languageUri = soln.getResource("language").getURI();
                    Language language = new Language(notation);
                    language.setLabel(label);
                    language.setUri(languageUri);
                    languages.add(language);
                }
            }

            return languages;
        }
    }

    public static Language getByInstrument(String instrumentUri) {
        try (var transaction = POEMModel.readTransaction()) {

            String queryString =
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX sio: <http://semanticscience.org/resource/> " +
                "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
                "SELECT ?language ?notation ?label " +
                "WHERE { " +
                "  <" + instrumentUri + "> sio:SIO_000008 ?language . " +
                "  ?language rdf:type sio:SIO_000104 . " +
                "  ?language skos:notation ?notation . " +
                "  ?language rdfs:label ?label . " +
                "}";

            try (QueryExecution qexec = QueryExecutionFactory.create(queryString, POEMModel.getDataset())) {
                ResultSet results = qexec.execSelect();
                if (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    String notation = soln.getLiteral("notation").getString();
                    String label = soln.getLiteral("label").getString();
                    String languageUri = soln.getResource("language").getURI();
                    Language language = new Language(notation);
                    language.setLabel(label);
                    language.setUri(languageUri);
                    return language;
                }
            }

            return null;
        }
    }
}
