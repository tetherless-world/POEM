package controllers;

import org.junit.Test;
import play.mvc.Http;

import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.BAD_REQUEST;

public class InstrumentSearchApiTest {
    @Test
    public void rejectsMissingTerms() {
        var request = new Http.RequestBuilder().method("GET").uri("/api/instruments/search").build();

        assertEquals(BAD_REQUEST, new ApiController().searchInstruments(request).status());
    }

    @Test
    public void rejectsNonSnomedTermsWithoutLoadingTheStore() {
        var request = new Http.RequestBuilder().method("GET")
            .uri("/api/instruments/search?term=http%3A%2F%2Fexample.org%2F1").build();

        assertEquals(BAD_REQUEST, new ApiController().searchInstruments(request).status());
    }
}
