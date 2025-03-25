package utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.sparql.exec.RowSet.Exception;

public class POEMModel {
    public static Model getModel() {
        Model model = ModelFactory.createDefaultModel();
        try {
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
            model.read("./data/scalesInstrument.ttl");
        } catch (RiotNotFoundException e) {
            try {
                model.read("./dist/data/activities.ttl");
                model.read("./dist/data/codebooks.ttl");
                model.read("./dist/data/experiences.ttl");
                model.read("./dist/data/informants.ttl");
                model.read("./dist/data/instrumentItemMap.ttl");
                model.read("./dist/data/instruments.ttl");
                model.read("./dist/data/items.ttl");
                model.read("./dist/data/itemStemConcepts.ttl");
                model.read("./dist/data/itemStems.ttl");
                model.read("./dist/data/responseOptions.ttl");
                model.read("./dist/data/scaleItemConceptMap.ttl");
                model.read("./dist/data/scales.ttl");
                model.read("./dist/data/scalesInstrument.ttl");
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
}
