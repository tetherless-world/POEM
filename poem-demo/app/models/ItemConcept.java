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
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;

import utils.POEMModel;

public class ItemConcept extends models.Resource {
    public static List<ItemConcept> getByScale(QuestionnaireScale scale) {
        List<ItemConcept> itemConcepts = new ArrayList<ItemConcept>();
        Model model = POEMModel.getModel();
        NodeIterator iter = model.listObjectsOfProperty(ResourceFactory.createResource(scale.getUri()), ResourceFactory.createProperty("http://semanticscience.org/resource/SIO_000059"));
        while (iter.hasNext()) {
            ItemConcept itemConcept = new ItemConcept();
            RDFNode node = iter.nextNode();
            itemConcept.setUri(node.asResource().getURI());
            itemConcept.setLabel(node.asResource().getProperty(RDFS.label).getString());
            itemConcepts.add(itemConcept);
        }
        return itemConcepts;
    }

    public static List<ItemConcept> getByInstruments(Instrument instrument0, Instrument instrument1) {
        List<ItemConcept> itemConcepts = new ArrayList<ItemConcept>();
        Model model = POEMModel.getModel();
        String queryString = String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX prov: <http://www.w3.org/ns/prov#>
            PREFIX sio: <http://semanticscience.org/resource/>
            SELECT ?itemConcept
            WHERE {
                <%s> sio:hasMember/sio:hasSource ?itemConcept .
                <%s> sio:hasMember/sio:hasSource ?itemConcept .
            }
        """, instrument0.getUri(), instrument1.getUri());
        
        System.out.println(queryString);
        Query query = QueryFactory.create(queryString);

        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            for ( ; results.hasNext() ; )
            {
                QuerySolution soln = results.nextSolution() ;
                Resource r = soln.getResource("itemConcept") ; // Get a result variable - must be a resource
                ItemConcept i = new ItemConcept();
                i.setUri(r.asResource().getURI());
                i.setLabel(r.asResource().getProperty(RDFS.label).getString());
                itemConcepts.add(i);
            }
        }

        return itemConcepts;
    }
}
