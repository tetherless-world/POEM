package controllers;

import org.junit.Test;
import play.mvc.Http;

import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.BAD_REQUEST;

public class InstrumentCollectionSearchApiTest {
    @Test
    public void rejectsInvalidCollectionDetailUrisWithoutLoadingTheStore() {
        var controller = new ApiController();
        for (String uri : new String[] {null, "", "relative", "http://invalid uri"}) {
            assertEquals(BAD_REQUEST, controller.getInstrumentCollectionAbout(uri).status());
        }
    }

    @Test
    public void rejectsMissingTerms() {
        var request = new Http.RequestBuilder().method("GET").uri("/api/instrument-collections/search").build();

        assertEquals(BAD_REQUEST, new ApiController().searchInstrumentCollections(request).status());
    }

    @Test
    public void rejectsNonSnomedTermsWithoutLoadingTheStore() {
        var request = new Http.RequestBuilder().method("GET")
            .uri("/api/instrument-collections/search?term=http%3A%2F%2Fexample.org%2F1").build();

        assertEquals(BAD_REQUEST, new ApiController().searchInstrumentCollections(request).status());
    }
}
