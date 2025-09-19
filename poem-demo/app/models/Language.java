package models;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

import utils.POEMModel;

public class Language extends models.Resource {
    private String notation;
    private String countryCode;
    private String label;

    public Language(String notation, String countryCode) {
        this.notation = notation;
        this.countryCode = countryCode;
    }

    public String getNotation() {
        return notation;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getBCP47() {
        if (notation == null) {
            return null;
        }
        String lowerNotation = notation.toLowerCase();
        if (countryCode == null) {
            return lowerNotation;
        }
        return lowerNotation + "-" + countryCode.toLowerCase();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public static List<Language> getAll() {
        List<Language> languages = new ArrayList<>();
        Model model = POEMModel.getModel();
        
        String queryString = 
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
            "PREFIX sio: <http://semanticscience.org/resource/> " +
            "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
            "PREFIX schema: <http://schema.org/> " +
            "SELECT ?language ?notation ?countryCode ?label " +
            "WHERE { " +
            "  ?language rdf:type sio:SIO_000104 . " +
            "  OPTIONAL { ?language skos:notation ?notation } " +
            "  OPTIONAL { ?language schema:countryCode ?countryCode } " +
            "  OPTIONAL { ?language rdfs:label ?label } " +
            "}";
        
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                String notation = soln.contains("notation") ? soln.getLiteral("notation").getString() : null;
                String countryCode = soln.contains("countryCode") ? soln.getLiteral("countryCode").getString() : null;
                String label = soln.contains("label") ? soln.getLiteral("label").getString() : null;
                String languageUri = soln.getResource("language").getURI();
                Language language = new Language(notation, countryCode);
                language.setLabel(label);
                language.setUri(languageUri);
                languages.add(language);
            }
        }
        
        return languages;
    }

    public static Language getByInstrument(String instrumentUri) {
        Model model = POEMModel.getModel();
        
        String queryString = 
            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
            "PREFIX sio: <http://semanticscience.org/resource/> " +
            "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
            "PREFIX schema: <http://schema.org/> " +
            "SELECT ?language ?notation ?countryCode ?label " +
            "WHERE { " +
            "  <" + instrumentUri + "> sio:SIO_000008 ?language . " +
            "  ?language rdf:type sio:SIO_000104 . " +
            "  OPTIONAL { ?language skos:notation ?notation } " +
            "  OPTIONAL { ?language schema:countryCode ?countryCode } " +
            "  OPTIONAL { ?language rdfs:label ?label } " +
            "}";
        
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                String notation = soln.contains("notation") ? soln.getLiteral("notation").getString() : null;
                String countryCode = soln.contains("countryCode") ? soln.getLiteral("countryCode").getString() : null;
                String label = soln.contains("label") ? soln.getLiteral("label").getString() : null;
                String languageUri = soln.getResource("language").getURI();
                Language language = new Language(notation, countryCode);
                language.setLabel(label);
                language.setUri(languageUri);
                return language;
            }
        }
        
        return null;
    }
}