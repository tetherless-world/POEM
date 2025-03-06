package utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class POEMModel {
    public static Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        model.read("./data/activities.ttl");
        model.read("./data/codebooks.ttl");
        model.read("./data/experiences.ttl");
        model.read("./data/informants.ttl");
        model.read("./data/instrumentItemMap.ttl");
        model.read("./data/instruments.ttl");
        model.read("./data/items.ttl");
        model.read("./data/itemStemConcepts.ttl");
        model.read("./data/itemStems.ttl");
        model.read("./data/responseOptions.ttl");
        model.read("./data/scaleItemConceptMap.ttl");
        model.read("./data/scales.ttl");
        return model;
    }
}
