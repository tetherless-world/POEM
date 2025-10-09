package services.chat;

import org.junit.BeforeClass;
import org.junit.Test;
import services.chat.intent.ChatIntent;
import services.chat.intent.InstrumentLanguagesIntent;
import services.chat.intent.InstrumentScalesIntent;
import services.chat.intent.InstrumentSimilarityByConceptsIntent;
import services.chat.intent.ScaleItemConceptsIntent;
import utils.POEMModel;

import java.util.Optional;

import static org.junit.Assert.*;

public class ChatIntentResolverTest {

    private static final String INSTRUMENT_Y_EN = "http://purl.org/twc/poem/individual/instrument/1";
    private static final String INSTRUMENT_25_Y_EN = "http://purl.org/twc/poem/individual/instrument/3";
    private static final String SCALE_SOCIAL_PHOBIA = "http://purl.org/twc/poem/individual/scale/1";

    private static ChatIntentResolver resolver;

    @BeforeClass
    public static void loadModel() {
        POEMModel.refresh();
        resolver = new ChatIntentResolver();
    }

    @Test
    public void detectsInstrumentScalesIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Which scales does RCADS-47-Y-EN include?");

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentScalesIntent intent);
        assertEquals(INSTRUMENT_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsLanguageIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "What language is RCADS-47-Y-EN available in?");

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentLanguagesIntent intent);
        assertEquals(INSTRUMENT_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsSimilarityIntentWithTwoInstruments() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "How are RCADS-47-Y-EN and RCADS-25-Y-EN similar? Do they share item concepts?");

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentSimilarityByConceptsIntent intent);
        assertTrue(intent.instrumentUris().contains(INSTRUMENT_Y_EN));
        assertTrue(intent.instrumentUris().contains(INSTRUMENT_25_Y_EN));
    }

    @Test
    public void detectsScaleConceptIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Which item concepts compose the Social Phobia (9.1) scale?");

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof ScaleItemConceptsIntent intent);
        assertEquals(SCALE_SOCIAL_PHOBIA, intent.scaleUri());
    }

    @Test
    public void emptyIntentForUnknownQuestion() {
        assertTrue(resolver.resolve("Tell me a joke.").isEmpty());
    }
}
