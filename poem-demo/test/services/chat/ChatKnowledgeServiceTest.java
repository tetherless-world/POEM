package services.chat;

import org.junit.BeforeClass;
import org.junit.Test;
import services.chat.intent.InstrumentExperienceComparisonIntent;
import services.chat.intent.InstrumentScalesIntent;
import services.chat.intent.InstrumentSimilarityByConceptsIntent;
import services.chat.intent.ScaleItemConceptsIntent;
import utils.POEMModel;

import java.util.List;

import static org.junit.Assert.*;

public class ChatKnowledgeServiceTest {

    private static final String INSTRUMENT_Y_EN = "http://purl.org/twc/poem/individual/instrument/1";
    private static final String INSTRUMENT_25_Y_EN = "http://purl.org/twc/poem/individual/instrument/3";
    private static final String SCALE_SOCIAL_PHOBIA = "http://purl.org/twc/poem/individual/scale/1";

    @BeforeClass
    public static void ensureModelLoaded() {
        POEMModel.refresh();
    }

    @Test(expected = NullPointerException.class)
    public void executeIntentRequiresNonNullIntent() {
        new ChatKnowledgeService().executeIntent(null);
    }

    @Test
    public void executesInstrumentScalesQuery() {
        ChatKnowledgeService service = new ChatKnowledgeService();
        ChatQueryResult result = service.executeIntent(new InstrumentScalesIntent(INSTRUMENT_Y_EN));

        assertFalse(result.isEmpty());
        assertTrue(result.getVariables().contains("scale"));
        assertNotNull(result.getRows().get(0).get("scale"));
    }

    @Test
    public void executesScaleConceptQuery() {
        ChatKnowledgeService service = new ChatKnowledgeService();
        ChatQueryResult result = service.executeIntent(new ScaleItemConceptsIntent(SCALE_SOCIAL_PHOBIA));

        assertFalse(result.isEmpty());
        assertTrue(result.getVariables().contains("concept"));
    }

    @Test
    public void executesSimilarityQueryWithTwoParameters() {
        ChatKnowledgeService service = new ChatKnowledgeService();
        ChatQueryResult result = service.executeIntent(
                new InstrumentSimilarityByConceptsIntent(List.of(INSTRUMENT_Y_EN, INSTRUMENT_25_Y_EN)));

        assertFalse(result.isEmpty());
        assertTrue(result.getVariables().contains("concept"));
    }

    @Test
    public void executesExperienceComparisonQuery() {
        ChatKnowledgeService service = new ChatKnowledgeService();
        ChatQueryResult result = service.executeIntent(
                new InstrumentExperienceComparisonIntent(List.of(INSTRUMENT_Y_EN, INSTRUMENT_25_Y_EN)));

        assertFalse(result.isEmpty());
        assertTrue(result.getVariables().contains("experience"));
    }
}
