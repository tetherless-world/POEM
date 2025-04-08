package models;

import java.util.ArrayList;
import java.util.List;

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

import utils.POEMModel;

public class Instrument extends models.Resource {

    private List<Item> items;

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public Instrument() {
        super();
        this.items = new ArrayList<Item>();
    }

    public static List<Instrument> getAll() {
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
            instruments.add(instrument);
        }
        return instruments;
    }

    public static Instrument getByUri(String uri) {
        System.out.println("Instrument.getByUri: " + uri);
        Instrument instrument = new Instrument();
        Model model = POEMModel.getModel();
        Resource resource = model.getResource(uri);
        instrument.setUri(resource.getURI());
        instrument.setLabel(resource.getProperty(RDFS.label).getString());
        instrument.setItems(Item.getByInstrument(resource.getURI()));
        return instrument;
    }

    public static List<Instrument> getSource(Instrument instrument) {
        List<Instrument> instruments = new ArrayList<Instrument>();
        Model model = POEMModel.getModel();
        String queryString = String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX prov: <http://www.w3.org/ns/prov#>
            SELECT ?source
            WHERE {
                <%s> prov:wasGeneratedBy ?activity .
                ?activity prov:used ?source .
            }
        """, instrument.getUri());
        System.out.println(queryString);
        Query query = QueryFactory.create(queryString);

        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
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

    public static List<Instrument> getTarget(Instrument instrument) {
        List<Instrument> instruments = new ArrayList<Instrument>();
        Model model = POEMModel.getModel();
        String queryString = String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX prov: <http://www.w3.org/ns/prov#>
            SELECT ?target
            WHERE {
                ?activity prov:used <%s> .
                ?target prov:wasGeneratedBy ?activity .
            }
        """, instrument.getUri());
        System.out.println(queryString);
        Query query = QueryFactory.create(queryString);

        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
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
