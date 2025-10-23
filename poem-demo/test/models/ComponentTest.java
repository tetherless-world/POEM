package models;

import org.junit.BeforeClass;
import org.junit.Test;
import utils.POEMModel;

import models.Component;

import java.util.List;

import static org.junit.Assert.*;

public class ComponentTest {

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testGetAll() {
        List<Component> components = Component.getAll();
        assertNotNull(components);
        assertFalse(components.isEmpty());
        for (Component component : components) {
            assertNotNull(component.getUri());
                // Do not assert label, only URI presence
        }
    }

    @Test
    public void testGetByUri() {
        List<Component> components = Component.getAll();
        assertFalse(components.isEmpty());
        Component first = components.get(0);
        Component byUri = Component.getByUri(first.getUri());
        assertEquals(first.getUri(), byUri.getUri());
            // Do not assert label equality, only URI
    }
}