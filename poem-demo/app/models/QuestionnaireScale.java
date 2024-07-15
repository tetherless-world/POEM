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

public class QuestionnaireScale extends models.Resource {
    public static List<QuestionnaireScale> getAll() {
        List<QuestionnaireScale> scales = new ArrayList<QuestionnaireScale>();
        Model model = ModelFactory.createDefaultModel();
        model.read("https://raw.githubusercontent.com/tetherless-world/POEM/evidence-modeling/POEM-RCADS.rdf");
        ResIterator iter = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://purl.org/twc/poem#QuestionnaireScale"));
        while (iter.hasNext()) {
            Resource r = iter.nextResource();
            QuestionnaireScale scale = new QuestionnaireScale();
            scale.setUri(r.getURI());
            model.listObjectsOfProperty(r, RDFS.label).forEachRemaining(label -> {
                scale.setLabel(label.asLiteral().getString());
            });
            System.out.println(r.getURI());
            scales.add(scale);
        }
        return scales;
    }
}
