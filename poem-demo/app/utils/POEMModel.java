package utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotNotFoundException;

public class POEMModel {

    private static final String DATA_PREFIX = "./data/";
    private static final String DIST_DATA_PREFIX = "./dist/data/";
    private static final String[] DATA_FILES = {
        "activities.ttl",
        "codebooks.ttl",
        "experiences.ttl",
        "informants.ttl",
        "instrumentItemMap.ttl",
        "instruments.ttl",
        "instrumentCollections.ttl",
        "items.ttl",
        "itemStemConcepts.ttl",
        "itemStems.ttl",
        "responseOptions.ttl",
        "scaleItemConceptMap.ttl",
        "scales.ttl",
        "scalesInstrument.ttl",
        "components.ttl",
        "languages.ttl",
        "persons.ttl",
        "agents.ttl"
    };

    private static final Object MODEL_LOCK = new Object();
    private static volatile Model cachedModel;

    public static Model getModel() {
        Model localModel = cachedModel;
        if (localModel != null) {
            return localModel;
        }
        synchronized (MODEL_LOCK) {
            if (cachedModel == null) {
                cachedModel = loadModel();
            }
            return cachedModel;
        }
    }

    public static void refresh() {
        synchronized (MODEL_LOCK) {
            if (cachedModel != null) {
                cachedModel.close();
            }
            cachedModel = loadModel();
        }
    }

    private static Model loadModel() {
        try {
            return loadModelFromPrefix(DATA_PREFIX);
        } catch (RiotNotFoundException e) {
            try {
                return loadModelFromPrefix(DIST_DATA_PREFIX);
            } catch (RuntimeException e2) {
                e2.printStackTrace();
                return ModelFactory.createDefaultModel();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ModelFactory.createDefaultModel();
        }
    }

    private static Model loadModelFromPrefix(String prefix) {
        Model model = ModelFactory.createDefaultModel();
        try {
            for (String file : DATA_FILES) {
                model.read(prefix + file);
            }
            return model;
        } catch (RuntimeException e) {
            model.close();
            throw e;
        }
    }
}
