package utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class POEMModel {
    public static Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/activities.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/codebooks.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/experiences.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/informants.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/instrumentItemMap.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/instruments.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/items.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/itemStemConcepts.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/itemStems.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/responseOptions.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/scaleItemConceptMap.ttl");
        model.read("/Users/hansi/git/POEM/poem-demo/public/data/scales.ttl");
        return model;
    }
}
