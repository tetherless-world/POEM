package models;

import java.util.HashSet;

import org.junit.Test;

import static org.junit.Assert.*;

public class InstrumentCollectionTest {

    private static final String PREFIX = "http://purl.org/twc/poem/individual/instrumentCollection/";

    @Test
    public void getAllIncludesRcads() {
        var collections = InstrumentCollection.getAll();

        assertFalse(collections.isEmpty());
        assertTrue(collections.stream().anyMatch(collection -> (PREFIX + "1").equals(collection.getUri())));
    }

    @Test
    public void getByUriLoadsCollectionMetadata() {
        var collection = InstrumentCollection.getByUri(PREFIX + "1");

        assertNotNull(collection);
        assertEquals(PREFIX + "1", collection.getUri());
        assertEquals("RCADS", collection.getLabel());
        assertEquals("RCADS", collection.getNotation());
        assertEquals("Revised Child Anxiety and Depression Scale", collection.getDefinition());
    }

    @Test
    public void getByUriRejectsMissingResourcesAndOtherTypes() {
        assertNull(InstrumentCollection.getByUri(PREFIX + "missing"));
        assertNull(InstrumentCollection.getByUri("http://purl.org/twc/poem/individual/instrument/1"));
    }

    @Test
    public void membersAreDistinctAcrossBothSioPredicates() {
        var collection = InstrumentCollection.getByUri(PREFIX + "1");
        var instruments = collection.getInstruments();
        var uris = new HashSet<String>();

        assertFalse(instruments.isEmpty());
        for (var instrument : instruments) {
            assertNotNull(instrument.getUri());
            assertTrue("Duplicate member " + instrument.getUri(), uris.add(instrument.getUri()));
        }
        assertTrue(uris.contains("http://purl.org/twc/poem/individual/instrument/1"));
    }

    @Test
    public void collectionWithoutMembersReturnsEmptyList() {
        var collection = InstrumentCollection.getByUri(PREFIX + "2");

        assertNotNull(collection);
        assertTrue(collection.getInstruments().isEmpty());
    }
}
