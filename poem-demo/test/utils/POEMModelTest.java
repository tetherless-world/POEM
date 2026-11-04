package utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class POEMModelTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void combinesRdfAndFunctionalOntologiesWithExistingData() throws Exception {
        Path directory = temporaryFolder.newFolder("ontology").toPath();
        Files.writeString(directory.resolve("labels.ttl"),
            "<urn:test:Child> <http://www.w3.org/2000/01/rdf-schema#label> \"Child\" .");
        Files.writeString(directory.resolve("classes.owl"), """
            <?xml version="1.0"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:owl="http://www.w3.org/2002/07/owl#">
              <owl:Class rdf:about="urn:test:Parent"/>
            </rdf:RDF>
            """);
        Files.writeString(directory.resolve("snomed.owl"), """
            Prefix(:=<urn:test:>)
            Ontology(<urn:test:ontology>
                Declaration(Class(:Child))
                Declaration(Class(:Parent))
                SubClassOf(:Child :Parent)
            )
            """);
        Files.writeString(directory.resolve("README"), "Not an ontology");
        Path tools = Files.createDirectory(directory.resolve("tools"));
        Files.writeString(tools.resolve("ignored.owl"), "Not an ontology");

        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("tdb2").toString());
        dataset.begin(ReadWrite.WRITE);
        Model model = dataset.getDefaultModel();
        try {
            model.add(model.createResource("urn:test:item"), RDF.type,
                model.createResource("urn:test:Child"));

            POEMModel.loadOntologies(model, directory);

            assertTrue(model.contains(model.createResource("urn:test:item"), RDF.type,
                model.createResource("urn:test:Child")));
            assertTrue(model.contains(model.createResource("urn:test:Parent"), RDF.type, OWL.Class));
            assertTrue(model.contains(model.createResource("urn:test:Child"), RDFS.label, "Child"));
            assertTrue(model.contains(model.createResource("urn:test:Child"), RDFS.subClassOf,
                model.createResource("urn:test:Parent")));
            dataset.commit();
        } finally {
            dataset.end();
            dataset.close();
        }
    }

    @Test
    public void persistsCombinedGraphAndSkipsImportOnReopen() throws Exception {
        Path ontology = temporaryFolder.newFolder("source").toPath();
        Files.writeString(ontology.resolve("labels.ttl"),
            "<urn:test:Child> <http://www.w3.org/2000/01/rdf-schema#label> \"Child\" .");
        String location = temporaryFolder.newFolder("tdb2").toString();
        Dataset dataset = TDB2Factory.connectDataset(location);
        try {
            POEMModel.initializeStore(dataset, model -> {
                model.add(model.createResource("urn:test:item"), RDF.type,
                    model.createResource("urn:test:Child"));
                POEMModel.loadOntologies(model, ontology);
            }, false, "v1");
        } finally {
            dataset.close();
        }

        Dataset reopened = TDB2Factory.connectDataset(location);
        try {
            POEMModel.initializeStore(reopened, model -> {
                throw new AssertionError("An initialized store must not be imported again");
            }, false, "v1");
            try (var transaction = new POEMModel.ReadTransaction(reopened);
                 var query = QueryExecutionFactory.create("""
                     ASK { <urn:test:item> a ?class . ?class <http://www.w3.org/2000/01/rdf-schema#label> "Child" }
                     """, reopened.getDefaultModel())) {
                assertTrue(query.execAsk());
                assertEquals(2, reopened.getDefaultModel().size());
            }
        } finally {
            reopened.close();
        }
    }

    @Test
    public void refreshReplacesGraphAndFailedRefreshRollsBack() throws Exception {
        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("tdb2").toString());
        try {
            POEMModel.initializeStore(dataset, model -> addLabel(model, "old"), false, "v1");
            assertThrows(IllegalStateException.class, () ->
                POEMModel.initializeStore(dataset, model -> {
                    addLabel(model, "partial");
                    throw new IllegalStateException("Import failed");
                }, true, "v1"));
            assertFalse(dataset.isInTransaction());
            try (var transaction = new POEMModel.ReadTransaction(dataset)) {
                assertTrue(dataset.getDefaultModel().contains(null, RDFS.label, "old"));
                assertFalse(dataset.getDefaultModel().contains(null, RDFS.label, "partial"));
            }
            POEMModel.initializeStore(dataset, model -> addLabel(model, "new"), true, "v1");
            try (var transaction = new POEMModel.ReadTransaction(dataset)) {
                assertEquals(1, dataset.getDefaultModel().size());
                assertTrue(dataset.getDefaultModel().contains(null, RDFS.label, "new"));
            }
        } finally {
            dataset.close();
        }
    }

    @Test
    public void nestedReadsShareTransactionAndExceptionsReleaseIt() throws Exception {
        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("tdb2").toString());
        try {
            POEMModel.initializeStore(dataset, model -> addLabel(model, "test"), false, "v1");
            assertThrows(IllegalStateException.class, () -> {
                try (var outer = new POEMModel.ReadTransaction(dataset)) {
                    try (var inner = new POEMModel.ReadTransaction(dataset)) {
                        assertEquals(1, dataset.getDefaultModel().size());
                    }
                    assertTrue(dataset.isInTransaction());
                    throw new IllegalStateException("Read failed");
                }
            });
            assertFalse(dataset.isInTransaction());
        } finally {
            dataset.close();
        }
    }

    @Test
    public void concurrentReaderSeesConsistentGraphDuringRefresh() throws Exception {
        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("tdb2").toString());
        try {
            POEMModel.initializeStore(dataset, model -> addLabel(model, "old"), false, "v1");
            try (var transaction = new POEMModel.ReadTransaction(dataset)) {
                CompletableFuture.runAsync(() -> POEMModel.initializeStore(dataset,
                    model -> addLabel(model, "new"), true, "v1")).get(10, TimeUnit.SECONDS);
                assertTrue(dataset.getDefaultModel().contains(null, RDFS.label, "old"));
                assertFalse(dataset.getDefaultModel().contains(null, RDFS.label, "new"));
            }
            assertTrue(Txn.calculateRead(dataset,
                () -> dataset.getDefaultModel().contains(null, RDFS.label, "new")));
        } finally {
            dataset.close();
        }
    }

    private static void addLabel(Model model, String label) {
        model.add(model.createResource("urn:test:item"), RDFS.label, label);
    }

    @Test
    public void changedFingerprintReloadsAndFailedImportIsRetried() throws Exception {
        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("changed-store").toString());
        try {
            POEMModel.initializeStore(dataset, model -> addLabel(model, "old"), false, "v1");
            assertThrows(IllegalStateException.class, () ->
                POEMModel.initializeStore(dataset, model -> {
                    addLabel(model, "partial");
                    throw new IllegalStateException("Import failed");
                }, false, "v2"));
            assertTrue(Txn.calculateRead(dataset,
                () -> dataset.getDefaultModel().contains(null, RDFS.label, "old")));
            POEMModel.initializeStore(dataset, model -> addLabel(model, "new"), false, "v2");
            assertTrue(Txn.calculateRead(dataset,
                () -> dataset.getDefaultModel().size() == 1
                    && dataset.getDefaultModel().contains(null, RDFS.label, "new")));
        } finally {
            dataset.close();
        }
    }

    @Test
    public void fingerprintsTrackContentAndFileSetAcrossLocations() throws Exception {
        Path development = temporaryFolder.newFolder("development").toPath();
        Path packaged = temporaryFolder.newFolder("packaged").toPath();
        Path data = Files.writeString(development.resolve("data.ttl"), "data");
        Path ontology = Files.writeString(development.resolve("ontology.ttl"), "original");
        String original = POEMModel.sourceFingerprint(List.of(data), List.of(ontology));
        Path packagedData = Files.copy(data, packaged.resolve("data.ttl"));
        Path packagedOntology = Files.copy(ontology, packaged.resolve("ontology.ttl"));
        assertEquals(original, POEMModel.sourceFingerprint(List.of(packagedData), List.of(packagedOntology)));
        Files.writeString(data, "changed data");
        assertFalse(original.equals(POEMModel.sourceFingerprint(List.of(data), List.of(ontology))));
        Files.writeString(data, "data");
        Files.writeString(ontology, "modified");
        assertFalse(original.equals(POEMModel.sourceFingerprint(List.of(data), List.of(ontology))));
        assertFalse(original.equals(POEMModel.sourceFingerprint(List.of(data), List.of())));
        Files.writeString(data, "");
        assertThrows(IllegalStateException.class,
            () -> POEMModel.sourceFingerprint(List.of(data), List.of(ontology)));
        Files.delete(data);
        assertThrows(IllegalStateException.class,
            () -> POEMModel.sourceFingerprint(List.of(data), List.of(ontology)));
    }

    @Test
    public void resolvesDefaultsAndRejectsInvalidExplicitDirectory() throws Exception {
        String property = "poem.test.source.directory";
        Path existing = temporaryFolder.newFolder("sources").toPath();
        Path missing = existing.resolve("missing");
        String previous = System.getProperty(property);
        try {
            System.clearProperty(property);
            assertEquals(existing, POEMModel.resolveDirectory(property, missing, existing));
            assertThrows(IllegalStateException.class, () -> POEMModel.resolveDirectory(property, missing));
            System.setProperty(property, existing.toString());
            assertEquals(existing, POEMModel.resolveDirectory(property, missing));
            System.setProperty(property, missing.toString());
            assertThrows(IllegalStateException.class, () -> POEMModel.resolveDirectory(property, existing));
            assertThrows(IllegalStateException.class, () -> POEMModel.ontologyFiles(existing));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    public void legacyStoreWithoutFingerprintIsReimported() throws Exception {
        Dataset dataset = TDB2Factory.connectDataset(temporaryFolder.newFolder("legacy-store").toString());
        try {
            Txn.executeWrite(dataset, () -> {
                addLabel(dataset.getDefaultModel(), "legacy");
                Model metadata = dataset.getNamedModel("urn:poem:store:metadata");
                metadata.addLiteral(metadata.createResource("urn:poem:store"),
                    metadata.createProperty("urn:poem:store:initialized"), true);
            });
            POEMModel.initializeStore(dataset, model -> addLabel(model, "current"), false, "v1");
            assertTrue(Txn.calculateRead(dataset,
                () -> dataset.getDefaultModel().size() == 1
                    && dataset.getDefaultModel().contains(null, RDFS.label, "current")));
        } finally {
            dataset.close();
        }
    }
}
