package models;

import org.junit.BeforeClass;
import org.junit.Test;
import utils.POEMModel;

import java.util.List;

import static org.junit.Assert.*;

public class QuestionnaireScaleTest {

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testGetAll() {
        List<QuestionnaireScale> scales = QuestionnaireScale.getAll();
        assertNotNull(scales);
        assertFalse(scales.isEmpty());
        for (QuestionnaireScale scale : scales) {
            assertNotNull(scale.getUri());
            assertNotNull(scale.getLabel());
        }
    }

    @Test
    public void testGetByUri() {
        List<QuestionnaireScale> scales = QuestionnaireScale.getAll();
        assertFalse(scales.isEmpty());
        QuestionnaireScale first = scales.get(0);
        QuestionnaireScale byUri = QuestionnaireScale.getByUri(first.getUri());
        assertEquals(first.getUri(), byUri.getUri());
        assertEquals(first.getLabel(), byUri.getLabel());
    }

    @Test
    public void testGetByInstrument() {
        List<Instrument> instruments = Instrument.getAll();
        assertFalse(instruments.isEmpty());
        Instrument firstInstrument = instruments.get(0);
        List<QuestionnaireScale> scales = QuestionnaireScale.getByInstrument(firstInstrument);
        assertNotNull(scales);
        // No assertion on size, but check objects
        for (QuestionnaireScale scale : scales) {
            assertNotNull(scale.getUri());
            assertNotNull(scale.getLabel());
        }
    }
}