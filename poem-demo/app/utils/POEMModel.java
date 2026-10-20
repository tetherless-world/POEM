package utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

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
        "sections.ttl",
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
        "agents.ttl",
        "methodTypes.ttl"
    };

    private static final Object MODEL_LOCK = new Object();
    private static final String METADATA_GRAPH = "urn:poem:store:metadata";
    private static volatile Dataset cachedDataset;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(POEMModel::close, "poem-tdb2-shutdown"));
    }

    /** Returns the persistent graph. Use it within a readTransaction() scope. */
    public static Model getModel() {
        return getDataset().getDefaultModel();
    }

    /** Returns the persistent dataset. Callers must use a transaction. */
    public static Dataset getDataset() {
        Dataset localDataset = cachedDataset;
        if (localDataset != null) {
            return localDataset;
        }
        synchronized (MODEL_LOCK) {
            if (cachedDataset == null) {
                cachedDataset = openStore(false);
            }
            return cachedDataset;
        }
    }

    /** Nested model lookups share the current thread's transaction. */
    public static ReadTransaction readTransaction() {
        return new ReadTransaction(getDataset());
    }

    public static final class ReadTransaction implements AutoCloseable {
        private final Dataset dataset;
        private boolean closed;

        ReadTransaction(Dataset dataset) {
            this.dataset = dataset.isInTransaction() ? null : dataset;
            if (this.dataset != null) {
                dataset.begin(ReadWrite.READ);
            }
        }

        @Override
        public void close() {
            if (!closed && dataset != null) {
                dataset.end();
            }
            closed = true;
        }
    }

    /** Atomically replaces the stored graph with the current source files. */
    public static void refresh() {
        synchronized (MODEL_LOCK) {
            if (cachedDataset == null) {
                cachedDataset = openStore(true);
            } else {
                initializeStore(cachedDataset, POEMModel::loadModel, true);
            }
        }
    }

    /** Releases the store's files; the next access reconnects to the same graph. */
    public static void close() {
        synchronized (MODEL_LOCK) {
            if (cachedDataset != null) {
                cachedDataset.close();
                cachedDataset = null;
            }
        }
    }

    private static Dataset openStore(boolean refresh) {
        Dataset dataset = TDB2Factory.connectDataset(
            System.getProperty("poem.tdb2.directory", "./tdb2"));
        try {
            initializeStore(dataset, POEMModel::loadModel, refresh);
            return dataset;
        } catch (RuntimeException | Error e) {
            dataset.close();
            throw e;
        }
    }

    static void initializeStore(Dataset dataset, Consumer<Model> loader, boolean refresh) {
        Txn.executeWrite(dataset, () -> {
            Model metadata = dataset.getNamedModel(METADATA_GRAPH);
            var store = ResourceFactory.createResource("urn:poem:store");
            var initialized = ResourceFactory.createProperty("urn:poem:store:initialized");
            if (refresh || !metadata.contains(store, initialized, metadata.createTypedLiteral(true))) {
                Model model = dataset.getDefaultModel();
                model.removeAll();
                loader.accept(model);
                metadata.removeAll();
                metadata.addLiteral(store, initialized, true);
            }
        });
    }

    private static void loadModel(Model model) {
        String prefix = Files.isDirectory(Path.of(DATA_PREFIX)) ? DATA_PREFIX : DIST_DATA_PREFIX;
        for (String file : DATA_FILES) {
            RDFDataMgr.read(model, Path.of(prefix, file).toUri().toString());
        }
        Path ontologyDirectory = Path.of("./ontology");
        if (!Files.isDirectory(ontologyDirectory)) {
            ontologyDirectory = Path.of("../ontology");
        }
        if (Files.isDirectory(ontologyDirectory)) {
            loadOntologies(model, ontologyDirectory);
        }
    }

    static void loadOntologies(Model model, Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> files = entries.filter(Files::isRegularFile)
                .filter(file -> RDFLanguages.filenameToLang(file.toString()) != null
                    || file.toString().endsWith(".ofn"))
                .sorted()
                .toList();
            for (Path file : files) {
                if (isFunctionalSyntax(file)) {
                    loadFunctionalOntology(model, file);
                } else {
                    RDFDataMgr.read(model, file.toUri().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read ontology directory " + directory, e);
        }
    }

    private static boolean isFunctionalSyntax(Path file) throws IOException {
        if (file.toString().endsWith(".ofn")) {
            return true;
        }
        if (!file.toString().endsWith(".owl")) {
            return false;
        }
        try (Stream<String> lines = Files.lines(file)) {
            String firstLine = lines.map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .findFirst().orElse("");
            return firstLine.startsWith("Prefix(") || firstLine.startsWith("Ontology(");
        }
    }

    private static void loadFunctionalOntology(Model model, Path file) throws IOException {
        // Use a temporary file to avoid keeping another copy of large ontologies in memory.
        Path converted = Files.createTempFile("poem-ontology-", ".ttl");
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(file.toFile());
            try (var output = Files.newOutputStream(converted)) {
                manager.saveOntology(ontology, new TurtleDocumentFormat(), output);
            }
            manager.removeOntology(ontology);
            RDFDataMgr.read(model, converted.toUri().toString());
        } catch (org.semanticweb.owlapi.model.OWLException e) {
            throw new IllegalStateException("Could not load ontology " + file, e);
        } finally {
            Files.deleteIfExists(converted);
        }
    }
}
