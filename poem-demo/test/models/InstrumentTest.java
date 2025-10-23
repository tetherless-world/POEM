package models;

import org.junit.BeforeClass;
import org.junit.Test;
import utils.POEMModel;

import java.util.List;

import static org.junit.Assert.*;

public class InstrumentTest {

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testGetAll() {
        List<Instrument> instruments = Instrument.getAll();
        assertNotNull(instruments);
        assertFalse(instruments.isEmpty());
        for (Instrument instrument : instruments) {
            assertNotNull(instrument.getUri());
            assertNotNull(instrument.getLabel());
        }
    }

    @Test
    public void testGetByUri() {
        List<Instrument> instruments = Instrument.getAll();
        assertFalse(instruments.isEmpty());
        Instrument first = instruments.get(0);
        Instrument byUri = Instrument.getByUri(first.getUri());
        assertEquals(first.getUri(), byUri.getUri());
        assertEquals(first.getLabel(), byUri.getLabel());
    }
}