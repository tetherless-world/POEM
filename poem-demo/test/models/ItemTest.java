package models;

import org.junit.BeforeClass;
import org.junit.Test;
import utils.POEMModel;

import models.Item;

import java.util.List;

import static org.junit.Assert.*;

public class ItemTest {

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testGetAll() {
        List<Item> items = Item.getAll();
        assertNotNull(items);
        assertFalse(items.isEmpty());
        for (Item item : items) {
            assertNotNull(item.getUri());
                // Do not assert label, only URI presence
        }
    }

    @Test
    public void testGetByUri() {
        List<Item> items = Item.getAll();
        assertFalse(items.isEmpty());
        Item first = items.get(0);
        Item byUri = Item.getByUri(first.getUri());
        assertEquals(first.getUri(), byUri.getUri());
            // Do not assert label equality, only URI
    }
}