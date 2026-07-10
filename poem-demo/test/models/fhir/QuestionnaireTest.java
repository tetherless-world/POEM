package models.fhir;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import models.Component;
import models.Instrument;
import utils.POEMModel;

import static org.junit.Assert.*;

public class QuestionnaireTest {

    private static final String INSTRUMENT_URI = "http://purl.org/twc/poem/individual/instrument/1";

    @BeforeClass
    public static void setup() {
        POEMModel.refresh();
    }

    @Test
    public void testR4ItemsAreNestedUnderSectionGroups() {
        Instrument instrument = new Instrument();
        instrument.setUri(INSTRUMENT_URI);
        instrument.setLabel("RCADS-47-Y-EN");
        instrument.setComponents(Component.getByInstrument(INSTRUMENT_URI));

        org.hl7.fhir.r4.model.Questionnaire fhir = new Questionnaire(instrument).toFhirR4();

        org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent sectionGroup = findTopLevelSectionGroup(fhir.getItem());
        assertNotNull(sectionGroup);
        assertEquals(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.GROUP, sectionGroup.getType());
        assertTrue(sectionGroup.getLinkId().startsWith("http://purl.org/twc/poem/individual/section/"));
        assertTrue(hasNestedQuestion(sectionGroup));
        assertFalse(hasTopLevelQuestion(fhir.getItem()));
    }

    private org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent findTopLevelSectionGroup(
        List<org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent> items
    ) {
        for (org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent item : items) {
            if (item.getType() == org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.GROUP
                && item.getLinkId().startsWith("http://purl.org/twc/poem/individual/section/")) {
                return item;
            }
        }
        return null;
    }

    private boolean hasNestedQuestion(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent group) {
        for (org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent item : group.getItem()) {
            if (item.getType() == org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.CHOICE) {
                return true;
            }
            if (item.getType() == org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.GROUP && hasNestedQuestion(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTopLevelQuestion(List<org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent> items) {
        for (org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent item : items) {
            if (item.getType() == org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.CHOICE) {
                return true;
            }
        }
        return false;
    }
}
