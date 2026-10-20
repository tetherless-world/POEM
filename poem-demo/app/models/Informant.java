package models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import utils.POEMModel;

public class Informant extends Resource {
    public static List<Informant> getAll() {
        List<Informant> informants = new ArrayList<>();
        ResIterator resources = POEMModel.getModel().listSubjectsWithProperty(
            RDF.type, ResourceFactory.createResource("http://purl.org/twc/vstoi/Informant"));
        try {
            while (resources.hasNext()) {
                var resource = resources.nextResource();
                var label = resource.getProperty(RDFS.label);
                if (resource.isURIResource() && label != null && label.getObject().isLiteral()) {
                    Informant informant = new Informant();
                    informant.setUri(resource.getURI());
                    informant.setLabel(label.getString());
                    informants.add(informant);
                }
            }
        } finally {
            resources.close();
        }
        informants.sort(Comparator.comparing(Informant::getLabel, String.CASE_INSENSITIVE_ORDER));
        return informants;
    }
}
