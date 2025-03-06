package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import utils.POEMModel;

public class QuestionnaireScale extends models.Resource {
    public static List<QuestionnaireScale> getAll() {
        List<QuestionnaireScale> scales = new ArrayList<QuestionnaireScale>();
        Model model = POEMModel.getModel();
        ResIterator iter = model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource("http://purl.org/twc/poem/QuestionnaireScale"));
        while (iter.hasNext()) {
            Resource r = iter.nextResource();
            QuestionnaireScale scale = new QuestionnaireScale();
            scale.setUri(r.getURI());
            model.listObjectsOfProperty(r, RDFS.label).forEachRemaining(label -> {
                scale.setLabel(label.asLiteral().getString());
            });
            scales.add(scale);
        }
        return scales;
    }

    public static QuestionnaireScale getByUri(String uri) {
        QuestionnaireScale scale = new QuestionnaireScale();
        Model model = POEMModel.getModel();
        Resource resource = model.getResource(uri);
        scale.setUri(resource.getURI());
        scale.setLabel(resource.getProperty(RDFS.label).getString());
        return scale;
    }
}
