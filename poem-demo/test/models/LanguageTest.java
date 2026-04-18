package models;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LanguageTest {

    @Test
    public void gsonSerializationDoesNotDuplicateLabelField() {
        Language language = new Language("eng", "US");
        language.setUri("http://example.org/languages/eng-US");
        language.setLabel("English");

        String json = new Gson().toJson(language);

        assertTrue(json.contains("\"label\":\"English\""));
        assertEquals(1, countOccurrences(json, "\"label\""));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
