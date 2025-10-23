package models;

import org.junit.BeforeClass;
import org.junit.Test;
import utils.POEMModel;

import java.util.List;

import static org.junit.Assert.*;

public class CodebookTest {

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testGetAll() {
        List<Codebook> codebooks = Codebook.getAll();
        assertNotNull(codebooks);
        assertFalse(codebooks.isEmpty());
        for (Codebook codebook : codebooks) {
            assertNotNull(codebook.getUri());
            assertNotNull(codebook.getLabel());
        }
    }

    @Test
    public void testGetByUri() {
        List<Codebook> codebooks = Codebook.getAll();
        assertFalse(codebooks.isEmpty());
        Codebook first = codebooks.get(0);
        Codebook byUri = Codebook.getByUri(first.getUri());
        assertEquals(first.getUri(), byUri.getUri());
        assertEquals(first.getLabel(), byUri.getLabel());
    }
}