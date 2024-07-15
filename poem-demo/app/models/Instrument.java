package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public class Instrument extends models.Resource {
    public static List<Instrument> getAll() {
        List<Instrument> instruments = new ArrayList<Instrument>();
        Model model = ModelFactory.createDefaultModel();
        model.read("https://raw.githubusercontent.com/tetherless-world/POEM/evidence-modeling/POEM-RCADS.rdf");
        ResIterator iter = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://purl.org/twc/poem#PsychometricQuestionnaire"));
        while (iter.hasNext()) {
            Resource r = iter.nextResource();
            Instrument instrument = new Instrument();
            instrument.setUri(r.getURI());
            model.listObjectsOfProperty(r, RDFS.label).forEachRemaining(label -> {
                instrument.setLabel(label.asLiteral().getString());
            });
            System.out.println(r.getURI());
            instruments.add(instrument);
        }
        return instruments;
    }
}
