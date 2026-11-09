package controllers;

import org.junit.Test;
import play.mvc.Http;

import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.BAD_REQUEST;

public class InstrumentCollectionSearchApiTest {
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
