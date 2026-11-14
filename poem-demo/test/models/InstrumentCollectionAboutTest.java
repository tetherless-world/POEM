package models;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.system.Txn;
import org.junit.Test;

import static org.junit.Assert.*;

public class InstrumentCollectionAboutTest {
    @Test
    public void summarizesOnlyDistinctMemberInstrumentsAndTheirAttributes() {
        Dataset dataset = DatasetFactory.createTxnMem();
        try {
            String data = """
                @prefix ex: <http://example.org/> .
                @prefix poem: <http://purl.org/twc/poem/> .
                @prefix sio: <http://semanticscience.org/resource/> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                ex:collection a poem:InstrumentCollection ; rdfs:label "Collection" ;
                    skos:definition "Description" ; skos:notation "EX" ;
                    sio:hasMember ex:a, ex:b, ex:notAnInstrument ; sio:SIO_000059 ex:a ;
                    sio:hasAttribute ex:unusedScale .
                ex:a a poem:PsychometricQuestionnaire ; sio:hasAttribute ex:scale, ex:en ;
                    sio:SIO_000008 ex:scale, ex:en .
                ex:b a poem:PsychometricQuestionnaire ; sio:SIO_000008 ex:scale, ex:es, ex:unlabeled .
                ex:scale a poem:QuestionnaireScale ; rdfs:label "Scale" .
                ex:unlabeled a poem:QuestionnaireScale .
                ex:en a sio:SIO_000104 ; rdfs:label "English" .
                ex:es a sio:SIO_000104 ; skos:notation "es" .
                ex:unusedScale a poem:QuestionnaireScale .
                ex:other a poem:PsychometricQuestionnaire ; sio:hasAttribute ex:unusedScale .
                ex:empty a poem:InstrumentCollection ; rdfs:comment "Fallback description" .
                """;
            Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset.getDefaultModel(),
                new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE));
            Txn.executeRead(dataset, () -> {
                var about = InstrumentCollectionAbout.getByUri(dataset, "http://example.org/collection");
                assertEquals("Collection", about.getLabel());
                assertEquals("Description", about.getDefinition());
                assertEquals("EX", about.getNotation());
                assertEquals(2, about.getInstrumentCount());
                assertEquals(2, about.getScales().size());
                assertEquals(2, about.getLanguages().size());
                assertEquals("English", about.getLanguages().get(0).getLabel());
                assertEquals("es", about.getLanguages().get(1).getLabel());
                assertTrue(about.getScales().stream().anyMatch(scale -> scale.getLabel() == null));

                var empty = InstrumentCollectionAbout.getByUri(dataset, "http://example.org/empty");
                assertEquals(0, empty.getInstrumentCount());
                assertEquals("Fallback description", empty.getDefinition());
                assertTrue(empty.getLanguages().isEmpty());
                assertTrue(empty.getScales().isEmpty());
                assertNull(InstrumentCollectionAbout.getByUri(dataset, "http://example.org/missing"));
                assertNull(InstrumentCollectionAbout.getByUri(dataset, "http://example.org/a"));
            });
        } finally {
            dataset.close();
        }
    }
}
