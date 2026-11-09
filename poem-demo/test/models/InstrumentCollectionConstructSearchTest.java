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

public class InstrumentCollectionConstructSearchTest {
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

            ex:scaleCollection a poem:InstrumentCollection ; rdfs:label "Scale collection" ;
                sio:SIO_000059 ex:scaleOnly ; sio:hasMember ex:scaleOnly .
            ex:itemCollection a poem:InstrumentCollection ; rdfs:label "Item collection" ;
                sio:hasMember ex:itemOnly .
            ex:bothCollection a poem:InstrumentCollection ; rdfs:label "Both collection", "Z duplicate label" ;
                sio:SIO_000059 ex:both, ex:scaleOnly, ex:itemOnly .
            ex:aliasCollection a poem:InstrumentCollection ; sio:hasMember ex:aliasOnly .
            ex:otherCollection a poem:InstrumentCollection ; sio:SIO_000059 ex:other .
            ex:emptyCollection a poem:InstrumentCollection .
            ex:wrongMembers a poem:InstrumentCollection ;
                sio:hasMember ex:notAnInstrument, ex:wrongScaleType, ex:wrongConceptType .
            ex:notACollection sio:hasMember ex:scaleOnly .
            ex:uncollected a poem:PsychometricQuestionnaire ; sio:hasAttribute ex:scale .

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

    private List<InstrumentCollection> search(String... terms) {
        return Txn.calculateRead(dataset, () -> InstrumentCollection.getByConstructs(dataset, List.of(terms)));
    }

    private List<String> uris(List<InstrumentCollection> instruments) {
        return instruments.stream().map(InstrumentCollection::getUri).sorted().toList();
    }

    @Test
    public void findsScaleMatchesAndDeduplicatesAcrossBothPaths() {
        var results = search(SCT + "1");

        assertEquals(List.of("http://example.org/bothCollection", "http://example.org/scaleCollection"), uris(results));
        var both = results.get(0);
        assertEquals("Both collection", both.getLabel());
        assertEquals(2, both.getConstructMatches().size());
        assertTrue(both.getConstructMatches().contains(new InstrumentCollection.ConstructMatch(SCT + "1", "scale")));
        assertTrue(both.getConstructMatches().contains(new InstrumentCollection.ConstructMatch(SCT + "1", "item")));
    }

    @Test
    public void findsItemConceptsAcrossNestedAndDuplicatePaths() {
        var results = search(SCT + "2");

        assertEquals(List.of("http://example.org/bothCollection", "http://example.org/itemCollection"), uris(results));
        assertEquals(List.of(new InstrumentCollection.ConstructMatch(SCT + "2", "item")), results.get(0).getConstructMatches());
    }

    @Test
    public void matchesAnySelectedTermAndIgnoresRepeatedTerms() {
        var results = search(SCT + "1", SCT + "2", SCT + "1");

        assertEquals(List.of("http://example.org/bothCollection", "http://example.org/itemCollection",
            "http://example.org/scaleCollection"), uris(results));
        assertEquals(3, results.get(0).getConstructMatches().size());
    }

    @Test
    public void combinesScaleAndItemMatchesFromDifferentMemberInstruments() {
        Txn.executeWrite(dataset, () -> dataset.getDefaultModel().remove(
            dataset.getDefaultModel().getResource("http://example.org/bothCollection"),
            dataset.getDefaultModel().createProperty("http://semanticscience.org/resource/SIO_000059"),
            dataset.getDefaultModel().getResource("http://example.org/both")));

        var collection = search(SCT + "1", SCT + "2").get(0);

        assertEquals("http://example.org/bothCollection", collection.getUri());
        assertEquals(2, collection.getConstructMatches().size());
        assertTrue(collection.getConstructMatches().contains(new InstrumentCollection.ConstructMatch(SCT + "1", "scale")));
        assertTrue(collection.getConstructMatches().contains(new InstrumentCollection.ConstructMatch(SCT + "2", "item")));
    }

    @Test
    public void supportsNamedSioPredicatesAndMissingCollectionLabels() {
        var results = search(SCT + "3");

        assertEquals(List.of("http://example.org/aliasCollection"), uris(results));
        assertNull(results.get(0).getLabel());
        assertEquals(2, results.get(0).getConstructMatches().size());
    }

    @Test
    public void emptyOrUnmatchedSelectionsReturnNoCollections() {
        assertTrue(InstrumentCollection.getByConstructs(List.of()).isEmpty());
        assertTrue(search().isEmpty());
        assertTrue(search(SCT + "999").isEmpty());
    }

    @Test
    public void rejectsInvalidAndInjectedTermValuesBeforeAccessingTheStore() {
        assertThrows(IllegalArgumentException.class, () -> InstrumentCollection.getByConstructs((List<String>) null));
        for (String invalid : Arrays.asList(null, "", "http://example.org/1", SCT + "1> } UNION {?s ?p ?o}")) {
            assertThrows(IllegalArgumentException.class, () -> InstrumentCollection.getByConstructs(Arrays.asList(invalid)));
        }
    }

    @Test
    public void searchesTheActualScaleAndItemMappings() {
        Txn.executeWrite(dataset, () -> {
            dataset.getDefaultModel().removeAll();
            for (String file : new String[] {"instrumentCollections", "instruments", "components", "sections", "items", "itemStems",
                    "itemStemConcepts", "scales", "scalesInstrument"}) {
                RDFDataMgr.read(dataset.getDefaultModel(), Path.of("dist", "data", file + ".ttl").toUri().toString());
            }
        });

        String rcads = "http://purl.org/twc/poem/individual/instrumentCollection/1";
        assertTrue(search(SCT + "25501002").stream().anyMatch(instrument -> instrument.getUri().equals(rcads)));
        assertTrue(search(SCT + "26677001").stream().anyMatch(instrument -> instrument.getUri().equals(rcads)));
    }
}
