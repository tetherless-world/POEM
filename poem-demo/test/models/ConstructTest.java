package models;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConstructTest {

    private Dataset dataset;

    @Before
    public void setUp() {
        dataset = DatasetFactory.createTxnMem();
        String data = """
            @prefix poem: <http://purl.org/twc/poem/> .
            @prefix sio: <http://semanticscience.org/resource/> .
            @prefix sct: <http://snomed.info/id/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix ex: <http://example.org/> .

            ex:item a poem:ItemStemConcept ; sio:SIO_000332 sct:1, sct:2, ex:otherTerm .
            ex:scale a poem:QuestionnaireScale ; sio:SIO_000332 sct:1, sct:3 .
            ex:instrument a poem:PsychometricQuestionnaire ; sio:SIO_000332 sct:4 .
            ex:aliasOnly a poem:ItemStemConcept ; sio:isAbout sct:5 .
            sct:1 rdfs:label "Anxiety (finding)"@en ; skos:prefLabel "Anxiety"@en .
            sct:2 skos:prefLabel "Worry"@en .
            sct:4 rdfs:label "Referenced by another type" .
            sct:6 rdfs:label "Unused SNOMED term" .
            """;
        Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset.getDefaultModel(),
            new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE));
    }

    @After
    public void tearDown() {
        dataset.close();
    }

    private List<Construct> getAll() {
        return Txn.calculateRead(dataset, () -> Construct.getAll(dataset));
    }

    @Test
    public void returnsDistinctSnomedTermsFromBothRequestedTypes() {
        var constructs = getAll();
        var uris = constructs.stream().map(Construct::getUri).sorted().toList();

        assertEquals(List.of("http://snomed.info/id/1", "http://snomed.info/id/2",
            "http://snomed.info/id/3"), uris);
    }

    @Test
    public void includesBothLabels() {
        var construct = getAll().stream()
            .filter(term -> term.getUri().equals("http://snomed.info/id/1"))
            .findFirst().orElseThrow();

        assertEquals("Anxiety (finding)", construct.getLabel());
        assertEquals("Anxiety", construct.getPrefLabel());
    }

    @Test
    public void retainsTermsWithMissingLabels() {
        var constructs = getAll();
        var preferredOnly = constructs.stream()
            .filter(term -> term.getUri().equals("http://snomed.info/id/2"))
            .findFirst().orElseThrow();
        var unlabeled = constructs.stream()
            .filter(term -> term.getUri().equals("http://snomed.info/id/3"))
            .findFirst().orElseThrow();

        assertNull(preferredOnly.getLabel());
        assertEquals("Worry", preferredOnly.getPrefLabel());
        assertNull(unlabeled.getLabel());
        assertNull(unlabeled.getPrefLabel());
    }

    @Test
    public void multipleLabelsDoNotDuplicateTerms() {
        Txn.executeWrite(dataset, () -> dataset.getDefaultModel().add(
            dataset.getDefaultModel().createResource("http://snomed.info/id/1"),
            org.apache.jena.vocabulary.RDFS.label, "Another label"));

        assertEquals(3, getAll().size());
    }
}
