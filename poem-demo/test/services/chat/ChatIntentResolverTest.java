package services.chat;

import org.junit.BeforeClass;
import org.junit.Test;
import models.chat.ChatMessage;
import services.chat.classifier.NoopIntentClassifier;
import services.chat.intent.ChatIntent;
import services.chat.intent.InstrumentCollectionIntent;
import services.chat.intent.InstrumentIntent;
import services.chat.intent.ListInstrumentCollectionsIntent;
import services.chat.intent.ListInstrumentsIntent;
import services.chat.intent.InstrumentLanguagesIntent;
import services.chat.intent.InstrumentScalesIntent;
import services.chat.intent.InstrumentSimilarityByConceptsIntent;
import services.chat.intent.ScaleItemConceptsIntent;
import utils.POEMModel;

import java.util.Collections;
import java.util.List;
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
        resolver = new ChatIntentResolver(new NoopIntentClassifier());
    }

    @Test
    public void detectsInstrumentScalesIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Which scales does RCADS-47-Y-EN include?",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentScalesIntent);
        InstrumentScalesIntent intent = (InstrumentScalesIntent) resolved.get();
        assertEquals(INSTRUMENT_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsLanguageIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "What language is RCADS-47-Y-EN available in?",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentLanguagesIntent);
        InstrumentLanguagesIntent intent = (InstrumentLanguagesIntent) resolved.get();
        assertEquals(INSTRUMENT_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsSimilarityIntentWithTwoInstruments() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "How are RCADS-47-Y-EN and RCADS-25-Y-EN similar? Do they share item concepts?",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentSimilarityByConceptsIntent);
        InstrumentSimilarityByConceptsIntent intent = (InstrumentSimilarityByConceptsIntent) resolved.get();
        assertTrue(intent.instrumentUris().contains(INSTRUMENT_Y_EN));
        assertTrue(intent.instrumentUris().contains(INSTRUMENT_25_Y_EN));
    }

    @Test
    public void detectsScaleConceptIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Which item concepts compose the Social Phobia (9.1) scale?",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof ScaleItemConceptsIntent);
        ScaleItemConceptsIntent intent = (ScaleItemConceptsIntent) resolved.get();
        assertEquals(SCALE_SOCIAL_PHOBIA, intent.scaleUri());
    }

    @Test
    public void resolvesUsingConversationHistory() {
        List<ChatMessage> history = List.of(
                new ChatMessage(ChatMessage.Role.USER, "Let's talk about RCADS-25-Y-EN"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "Sure."),
                new ChatMessage(ChatMessage.Role.USER, "What scales does it measure?")
        );

        Optional<ChatIntent> resolved = resolver.resolve("What scales does it measure?", history);

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentScalesIntent);
        InstrumentScalesIntent intent = (InstrumentScalesIntent) resolved.get();
        assertEquals(INSTRUMENT_25_Y_EN, intent.instrumentUri());
    }

    @Test
    public void matchesInstrumentWithFlexibleName() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Show scales for RCADS 25 English",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentScalesIntent);
        InstrumentScalesIntent intent = (InstrumentScalesIntent) resolved.get();
        assertEquals(INSTRUMENT_25_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsInstrumentMetadataIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Provide metadata about RCADS-47-Y-EN",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentIntent);
        InstrumentIntent intent = (InstrumentIntent) resolved.get();
        assertEquals(INSTRUMENT_Y_EN, intent.instrumentUri());
    }

    @Test
    public void detectsInstrumentCollectionIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "Give me the details for the RCADS instrument collection",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof InstrumentCollectionIntent);
        InstrumentCollectionIntent intent = (InstrumentCollectionIntent) resolved.get();
        assertEquals("http://purl.org/twc/poem/individual/instrumentCollection/1", intent.collectionUri());
    }

    @Test
    public void detectsListInstrumentCollectionsIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "List all instrument collections",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof ListInstrumentCollectionsIntent);
    }

    @Test
    public void detectsListInstrumentsIntent() {
        Optional<ChatIntent> resolved = resolver.resolve(
                "List all instruments",
                Collections.emptyList());

        assertTrue(resolved.isPresent());
        assertTrue(resolved.get() instanceof ListInstrumentsIntent);
    }

    @Test
    public void emptyIntentForUnknownQuestion() {
        assertTrue(resolver.resolve("Tell me a joke.", Collections.emptyList()).isEmpty());
    }
}
