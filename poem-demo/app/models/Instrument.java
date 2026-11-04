package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.OWL;

import utils.POEMModel;

public class Instrument extends models.Resource {

    private List<Component> components;
    private List<Item> items;
    private Language language;
    private int deprecated;
    private final List<ConstructMatch> constructMatches = new ArrayList<>();

    public record ConstructMatch(String termUri, String matchType) {}

    public List<ConstructMatch> getConstructMatches() {
        return List.copyOf(constructMatches);
    }

    /** Returns each instrument matching any selected SNOMED term, with its label and match reasons. */
    public static List<Instrument> getByConstructs(List<String> snomedTermUris) {
        List<String> terms = normalizeConstructTerms(snomedTermUris);
        if (terms.isEmpty()) {
            return List.of();
        }
        try (var transaction = POEMModel.readTransaction()) {
            return getByConstructs(POEMModel.getDataset(), terms);
        }
    }

    private static List<String> normalizeConstructTerms(List<String> termUris) {
        if (termUris == null) {
            throw new IllegalArgumentException("SNOMED terms are required");
        }
        var terms = new LinkedHashSet<String>();
        for (String uri : termUris) {
            if (uri == null || !uri.trim().matches("http://snomed\\.info/id/[0-9]+")) {
                throw new IllegalArgumentException("Each term must be a SNOMED URI such as http://snomed.info/id/21897009");
            }
            terms.add(uri.trim());
        }
        return List.copyOf(terms);
    }

    // The caller owns the dataset's read transaction.
    static List<Instrument> getByConstructs(Dataset dataset, List<String> snomedTermUris) {
        List<String> terms = normalizeConstructTerms(snomedTermUris);
        if (terms.isEmpty()) {
            return List.of();
        }
        StringBuilder values = new StringBuilder("VALUES ?term { ");
        for (int index = 0; index < terms.size(); index++) {
            values.append("?selectedTerm").append(index).append(' ');
        }
        values.append('}');
        ParameterizedSparqlString query = new ParameterizedSparqlString("""
            PREFIX poem: <http://purl.org/twc/poem/>
            PREFIX sio: <http://semanticscience.org/resource/>
            PREFIX vstoi: <http://purl.org/twc/vstoi/>
            SELECT DISTINCT ?instrument ?term ?matchType
            WHERE {
            """ + values + """

                ?instrument a poem:PsychometricQuestionnaire .
                FILTER(isIRI(?instrument))
                {
                    ?instrument (sio:SIO_000008|sio:hasAttribute) ?scale .
                    ?scale a poem:QuestionnaireScale ; (sio:SIO_000332|sio:isAbout) ?term .
                    BIND("scale" AS ?matchType)
                }
                UNION
                {
                    ?instrument (sio:SIO_000059|sio:hasMember)+ ?item .
                    ?item a vstoi:Item ; (sio:SIO_000253|sio:hasSource) ?stem .
                    ?stem (sio:SIO_000253|sio:hasSource) ?concept .
                    ?concept a poem:ItemStemConcept ; (sio:SIO_000332|sio:isAbout) ?term .
                    BIND("item" AS ?matchType)
                }
            }
            ORDER BY ?instrument ?term ?matchType
            """);
        for (int index = 0; index < terms.size(); index++) {
            query.setIri("selectedTerm" + index, terms.get(index));
        }

        Map<String, Instrument> matches = new LinkedHashMap<>();
        try (var execution = QueryExecutionFactory.create(query.asQuery(), dataset)) {
            var results = execution.execSelect();
            while (results.hasNext()) {
                var solution = results.nextSolution();
                String uri = solution.getResource("instrument").getURI();
                Instrument instrument = matches.computeIfAbsent(uri, key -> {
                    Instrument match = new Instrument();
                    match.setUri(key);
                    var labels = dataset.getDefaultModel().listObjectsOfProperty(
                        ResourceFactory.createResource(key), RDFS.label);
                    try {
                        match.setLabel(labels.toList().stream().filter(node -> node.isLiteral())
                            .map(node -> node.asLiteral().getString()).sorted().findFirst().orElse(null));
                    } finally {
                        labels.close();
                    }
                    return match;
                });
                instrument.constructMatches.add(new ConstructMatch(
                    solution.getResource("term").getURI(), solution.getLiteral("matchType").getString()));
            }
        }
        List<Instrument> instruments = new ArrayList<>(matches.values());
        instruments.sort(Comparator.comparing(Instrument::getLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Instrument::getUri));
        return instruments;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Component> getComponents() {
        return components;
    }

    public void setComponents(List<Component> components) {
        this.components = components;
    }

    public Instrument() {
        super();
        this.items = new ArrayList<Item>();
    }

    public static List<Instrument> getAll() {
        try (var transaction = POEMModel.readTransaction()) {
            List<Instrument> instruments = new ArrayList<Instrument>();
            Model model = POEMModel.getModel();
            ResIterator iter = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://purl.org/twc/poem/PsychometricQuestionnaire"));
            while (iter.hasNext()) {
                Resource r = iter.nextResource();
                Instrument instrument = new Instrument();
                instrument.setUri(r.getURI());
                model.listObjectsOfProperty(r, RDFS.label).forEachRemaining(label -> {
                    instrument.setLabel(label.asLiteral().getString());
                });
                instrument.setLanguage(Language.getByInstrument(r.getURI()));
                 if (r.getProperty(OWL.deprecated) != null) {
                     instrument.setDeprecated(r.getProperty(OWL.deprecated).getInt());
                 } else {
                     instrument.setDeprecated(0);
                 }
                instruments.add(instrument);
            }
            return instruments;
        }
    }

    public static Instrument getByUri(String uri) {
        try (var transaction = POEMModel.readTransaction()) {
            System.out.println("Instrument.getByUri: " + uri);
            Instrument instrument = new Instrument();
            Model model = POEMModel.getModel();
            Resource resource = model.getResource(uri);
            instrument.setUri(resource.getURI());
            instrument.setLabel(resource.getProperty(RDFS.label).getString());
            //instrument.setItems(Item.getByInstrument(resource.getURI()));
            instrument.setComponents(Component.getByInstrument(resource.getURI()));
            instrument.setLanguage(Language.getByInstrument(resource.getURI()));
            if (resource.getProperty(OWL.deprecated) != null) {
                instrument.setDeprecated(resource.getProperty(OWL.deprecated).getInt());
            } else {
                instrument.setDeprecated(0);
            }
            return instrument;
        }
    }

    public static List<Instrument> getSource(String uri) {
        try (var transaction = POEMModel.readTransaction()) {
            List<Instrument> instruments = new ArrayList<Instrument>();
            String queryString = String.format("""
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX prov: <http://www.w3.org/ns/prov#>
                SELECT ?source
                WHERE {
                    ?activity prov:generated <%s> .
                    ?activity prov:used ?source .
                }
            """, uri);
            System.out.println(queryString);
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionFactory.create(query, POEMModel.getDataset())) {
                ResultSet results = qexec.execSelect();
                for ( ; results.hasNext() ; )
                {
                    QuerySolution soln = results.nextSolution() ;
                    Resource r = soln.getResource("source") ; // Get a result variable - must be a resource
                    Instrument i = new Instrument();
                    i.setUri(r.asResource().getURI());
                    i.setLabel(r.asResource().getProperty(RDFS.label).getString());
                    instruments.add(i);
                }
            }

            return instruments;
        }
    }

    public static List<Instrument> getSource(Instrument instrument) {
        return getSource(instrument.getUri());
    }

    public static List<Instrument> getTarget(String uri) {
        try (var transaction = POEMModel.readTransaction()) {
            List<Instrument> instruments = new ArrayList<Instrument>();
            String queryString = String.format("""
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX prov: <http://www.w3.org/ns/prov#>
                SELECT ?target
                WHERE {
                    ?activity prov:used <%s> .
                    ?activity prov:generated ?target .
                }
            """, uri);
            System.out.println(queryString);
            Query query = QueryFactory.create(queryString);

            try (QueryExecution qexec = QueryExecutionFactory.create(query, POEMModel.getDataset())) {
                ResultSet results = qexec.execSelect();
                for ( ; results.hasNext() ; )
                {
                    QuerySolution soln = results.nextSolution() ;
                    Resource r = soln.getResource("target") ; // Get a result variable - must be a resource
                    Instrument i = new Instrument();
                    i.setUri(r.asResource().getURI());
                    i.setLabel(r.asResource().getProperty(RDFS.label).getString());
                    instruments.add(i);
                }
            }

            return instruments;
        }
    }

    public static List<Instrument> getTarget(Instrument instrument) {
        return getTarget(instrument.getUri());
    }

    public Language getLanguage() {
        return language;
    }

    public List<String> getInformantUris() {
        try (var transaction = POEMModel.readTransaction()) {
            List<String> informants = new ArrayList<>();
            Model model = POEMModel.getModel();
            var attributes = model.listObjectsOfProperty(model.getResource(getUri()),
                ResourceFactory.createProperty("http://semanticscience.org/resource/SIO_000008"));
            try {
                while (attributes.hasNext()) {
                    var attribute = attributes.next();
                    if (attribute.isURIResource() && attribute.asResource().hasProperty(RDF.type,
                            ResourceFactory.createResource("http://purl.org/twc/vstoi/Informant"))) {
                        informants.add(attribute.asResource().getURI());
                    }
                }
            } finally {
                attributes.close();
            }
            return informants;
        }
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public int getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(int deprecated) {
        this.deprecated = deprecated;
    }
}
