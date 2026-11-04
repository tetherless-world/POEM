package models;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
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

public class InstrumentConstructSearchTest {
    private static final String SCT = "http://snomed.info/id/";
    private Dataset dataset;

    @Before
    public void setUp() {
        dataset = DatasetFactory.createTxnMem();
        String data = """
            @prefix ex: <http://example.org/> .
            @prefix poem: <http://purl.org/twc/poem/> .
            @prefix sio: <http://semanticscience.org/resource/> .
            @prefix sct: <http://snomed.info/id/> .
            @prefix vstoi: <http://purl.org/twc/vstoi/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:scaleOnly a poem:PsychometricQuestionnaire ; rdfs:label "Scale instrument" ;
                sio:SIO_000008 ex:scale ; sio:hasAttribute ex:scale .
            ex:scale a poem:QuestionnaireScale ; sio:SIO_000332 sct:1 .

            ex:itemOnly a poem:PsychometricQuestionnaire ; rdfs:label "Item instrument" ;
                sio:SIO_000059 ex:component ; sio:hasMember ex:section .
            ex:component sio:hasMember ex:section .
            ex:section sio:SIO_000059 ex:nested .
            ex:nested sio:hasMember ex:item .
            ex:item a vstoi:Item ; sio:SIO_000253 ex:stem ; sio:hasSource ex:stem .
            ex:stem sio:SIO_000253 ex:concept .
            ex:concept a poem:ItemStemConcept ; sio:SIO_000332 sct:2 .

            ex:both a poem:PsychometricQuestionnaire ; rdfs:label "Both instrument", "Z duplicate label" ;
                sio:SIO_000008 ex:scale ; sio:SIO_000059 ex:bothItem, ex:bothSection .
            ex:bothSection sio:SIO_000059 ex:bothItem .
            ex:bothItem a vstoi:Item ; sio:SIO_000253 ex:bothStem .
            ex:bothStem sio:SIO_000253 ex:bothConcept .
            ex:bothConcept a poem:ItemStemConcept ; sio:SIO_000332 sct:1 .

            ex:aliasOnly a poem:PsychometricQuestionnaire ; sio:hasAttribute ex:aliasScale ;
                sio:hasMember ex:aliasItem .
            ex:aliasScale a poem:QuestionnaireScale ; sio:isAbout sct:3 .
            ex:aliasItem a vstoi:Item ; sio:hasSource ex:aliasStem .
            ex:aliasStem sio:hasSource ex:aliasConcept .
            ex:aliasConcept a poem:ItemStemConcept ; sio:isAbout sct:3 .

            ex:other a poem:PsychometricQuestionnaire ; sio:SIO_000008 ex:otherScale .
            ex:otherScale a poem:QuestionnaireScale ; sio:SIO_000332 sct:4 .
            sct:4 rdfs:subClassOf sct:1 .
            ex:notAnInstrument sio:SIO_000008 ex:scale .
            ex:wrongScaleType a poem:PsychometricQuestionnaire ; sio:SIO_000008 ex:notAScale .
            ex:notAScale sio:SIO_000332 sct:1 .
            ex:wrongConceptType a poem:PsychometricQuestionnaire ; sio:hasMember ex:wrongItem .
            ex:wrongItem a vstoi:Item ; sio:hasSource ex:wrongStem .
            ex:wrongStem sio:hasSource ex:notAConcept .
            ex:notAConcept sio:SIO_000332 sct:1 .
            """;
        Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset.getDefaultModel(),
            new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE));
    }

    @After
    public void tearDown() {
        dataset.close();
    }

    private List<Instrument> search(String... terms) {
        return Txn.calculateRead(dataset, () -> Instrument.getByConstructs(dataset, List.of(terms)));
    }

    private List<String> uris(List<Instrument> instruments) {
        return instruments.stream().map(Instrument::getUri).sorted().toList();
    }

    @Test
    public void findsScaleMatchesAndDeduplicatesAcrossBothPaths() {
        var results = search(SCT + "1");

        assertEquals(List.of("http://example.org/both", "http://example.org/scaleOnly"), uris(results));
        var both = results.get(0);
        assertEquals("Both instrument", both.getLabel());
        assertEquals(2, both.getConstructMatches().size());
        assertTrue(both.getConstructMatches().contains(new Instrument.ConstructMatch(SCT + "1", "scale")));
        assertTrue(both.getConstructMatches().contains(new Instrument.ConstructMatch(SCT + "1", "item")));
    }

    @Test
    public void findsItemConceptsAcrossNestedAndDuplicatePaths() {
        var results = search(SCT + "2");

        assertEquals(List.of("http://example.org/itemOnly"), uris(results));
        assertEquals(List.of(new Instrument.ConstructMatch(SCT + "2", "item")), results.get(0).getConstructMatches());
    }

    @Test
    public void matchesAnySelectedTermAndIgnoresRepeatedTerms() {
        var results = search(SCT + "1", SCT + "2", SCT + "1");

        assertEquals(List.of("http://example.org/both", "http://example.org/itemOnly",
            "http://example.org/scaleOnly"), uris(results));
        assertEquals(2, results.get(0).getConstructMatches().size());
    }

    @Test
    public void supportsNamedSioPredicatesAndMissingInstrumentLabels() {
        var results = search(SCT + "3");

        assertEquals(List.of("http://example.org/aliasOnly"), uris(results));
        assertNull(results.get(0).getLabel());
        assertEquals(2, results.get(0).getConstructMatches().size());
    }

    @Test
    public void emptyOrUnmatchedSelectionsReturnNoInstruments() {
        assertTrue(Instrument.getByConstructs(List.of()).isEmpty());
        assertTrue(search().isEmpty());
        assertTrue(search(SCT + "999").isEmpty());
    }

    @Test
    public void rejectsInvalidAndInjectedTermValuesBeforeAccessingTheStore() {
        assertThrows(IllegalArgumentException.class, () -> Instrument.getByConstructs((List<String>) null));
        for (String invalid : Arrays.asList(null, "", "http://example.org/1", SCT + "1> } UNION {?s ?p ?o}")) {
            assertThrows(IllegalArgumentException.class, () -> Instrument.getByConstructs(Arrays.asList(invalid)));
        }
    }

    @Test
    public void searchesTheActualScaleAndItemMappings() {
        Txn.executeWrite(dataset, () -> {
            dataset.getDefaultModel().removeAll();
            for (String file : new String[] {"instruments", "components", "sections", "items", "itemStems",
                    "itemStemConcepts", "scales", "scalesInstrument"}) {
                RDFDataMgr.read(dataset.getDefaultModel(), Path.of("dist", "data", file + ".ttl").toUri().toString());
            }
        });

        String rcads = "http://purl.org/twc/poem/individual/instrument/1";
        assertTrue(search(SCT + "25501002").stream().anyMatch(instrument -> instrument.getUri().equals(rcads)));
        assertTrue(search(SCT + "26677001").stream().anyMatch(instrument -> instrument.getUri().equals(rcads)));
    }
}
