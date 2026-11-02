package models;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class InstrumentAboutTest {
    private Dataset dataset;

    @Before
    public void setUp() {
        dataset = DatasetFactory.createTxnMem();
        String data = """
            @prefix ex: <http://example.org/> .
            @prefix poem: <http://purl.org/twc/poem/> .
            @prefix sio: <http://semanticscience.org/resource/> .
            @prefix vstoi: <http://purl.org/twc/vstoi/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:instrument a poem:PsychometricQuestionnaire ;
                sio:SIO_000008 ex:scale1, ex:scale2 ; sio:hasAttribute ex:scale1 ;
                sio:SIO_000059 ex:component, ex:section, ex:directItem ;
                sio:hasMember ex:component .
            ex:component sio:hasMember ex:section .
            ex:section a poem:QuestionnaireSection ; sio:SIO_000059 ex:item1, ex:nested .
            ex:nested sio:hasMember ex:item1, ex:item2 .
            ex:item1 a vstoi:Item .
            ex:item2 a vstoi:Item .
            ex:directItem a vstoi:Item .
            ex:scale1 a poem:QuestionnaireScale ; rdfs:label "Scale one", "Scale 1" .
            ex:scale2 a poem:QuestionnaireScale .
            ex:unusedScale a poem:QuestionnaireScale ; rdfs:label "Unrelated" .
            ex:other a poem:PsychometricQuestionnaire ; sio:hasMember ex:otherItem .
            ex:otherItem a vstoi:Item .
            ex:empty a poem:PsychometricQuestionnaire .
            """;
        Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset.getDefaultModel(),
            new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE));
    }

    @After
    public void tearDown() {
        dataset.close();
    }

    private InstrumentAbout getByUri(String uri) {
        return Txn.calculateRead(dataset, () -> InstrumentAbout.getByUri(dataset, uri));
    }

    @Test
    public void countsDistinctItemsAcrossNestedAndDuplicateMembershipPaths() {
        assertEquals(3, getByUri("http://example.org/instrument").getItemCount());
    }

    @Test
    public void returnsDistinctScalesIncludingScalesWithoutLabels() {
        var about = getByUri("http://example.org/instrument");

        assertEquals("http://example.org/instrument", about.getUri());
        assertEquals(2, about.getScales().size());
        var labeled = about.getScales().stream()
            .filter(scale -> scale.getUri().equals("http://example.org/scale1"))
            .findFirst().orElseThrow();
        assertNotNull(labeled.getLabel());
        var unlabeled = about.getScales().stream()
            .filter(scale -> scale.getUri().equals("http://example.org/scale2"))
            .findFirst().orElseThrow();
        assertNull(unlabeled.getLabel());
    }

    @Test
    public void distinguishesEmptyInstrumentsFromMissingOrWronglyTypedResources() {
        var empty = getByUri("http://example.org/empty");

        assertEquals(0, empty.getItemCount());
        assertTrue(empty.getScales().isEmpty());
        assertNull(getByUri("http://example.org/missing"));
        assertNull(getByUri("http://example.org/scale1"));
    }

    @Test
    public void countsActualRcadsFullAndShortVersions() {
        Txn.executeWrite(dataset, () -> {
            dataset.getDefaultModel().removeAll();
            for (String file : new String[] {"instruments", "components", "sections", "items",
                    "scales", "scalesInstrument"}) {
                RDFDataMgr.read(dataset.getDefaultModel(), Path.of("dist", "data", file + ".ttl").toUri().toString());
            }
        });
        String prefix = "http://purl.org/twc/poem/individual/instrument/";

        assertEquals(47, getByUri(prefix + "1").getItemCount());
        assertEquals(25, getByUri(prefix + "3").getItemCount());
        assertFalse(getByUri(prefix + "1").getScales().isEmpty());
    }
}
