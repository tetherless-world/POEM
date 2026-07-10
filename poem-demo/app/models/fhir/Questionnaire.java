package models.fhir;

import java.util.List;

import models.Instrument;
import models.Section;

public class Questionnaire {
    private Instrument instrument;

    public Questionnaire(Instrument instrument) {
        this.instrument = instrument;
    }

    public org.hl7.fhir.r5.model.Questionnaire toFhirR5() {
        org.hl7.fhir.r5.model.Questionnaire questionnaire = new org.hl7.fhir.r5.model.Questionnaire();
        questionnaire.setId(instrument.getUri());
        questionnaire.setName(instrument.getLabel());
        questionnaire.setStatus(org.hl7.fhir.r5.model.Enumerations.PublicationStatus.ACTIVE);
        for (models.Component component : instrument.getComponents()) {
            questionnaire.addItem(toFhirR5(component));
        }
        for (Section section : getSections()) {
            questionnaire.addItem(toFhirR5(section));
        }
        return questionnaire;
    }

    public org.hl7.fhir.r4b.model.Questionnaire toFhirR4B() {
        org.hl7.fhir.r4b.model.Questionnaire questionnaire = new org.hl7.fhir.r4b.model.Questionnaire();
        questionnaire.setId(instrument.getUri());
        questionnaire.setName(instrument.getLabel());
        questionnaire.setStatus(org.hl7.fhir.r4b.model.Enumerations.PublicationStatus.ACTIVE);
        for (models.Component component : instrument.getComponents()) {
            questionnaire.addItem(toFhirR4B(component));
        }
        for (Section section : getSections()) {
            questionnaire.addItem(toFhirR4B(section));
        }
        return questionnaire;
    }

    public org.hl7.fhir.r4.model.Questionnaire toFhirR4() {
        org.hl7.fhir.r4.model.Questionnaire questionnaire = new org.hl7.fhir.r4.model.Questionnaire();
        questionnaire.setId(instrument.getUri());
        questionnaire.setName(instrument.getLabel());
        questionnaire.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);
        for (models.Component component : instrument.getComponents()) {
            questionnaire.addItem(toFhirR4(component));
        }
        for (Section section : getSections()) {
            questionnaire.addItem(toFhirR4(section));
        }
        return questionnaire;
    }

    public org.hl7.fhir.dstu3.model.Questionnaire toFhirR3() {
        org.hl7.fhir.dstu3.model.Questionnaire questionnaire = new org.hl7.fhir.dstu3.model.Questionnaire();
        questionnaire.setId(instrument.getUri());
        questionnaire.setName(instrument.getLabel());
        questionnaire.setStatus(org.hl7.fhir.dstu3.model.Enumerations.PublicationStatus.ACTIVE);
        for (models.Component component : instrument.getComponents()) {
            questionnaire.addItem(toFhirR3(component));
        }
        for (Section section : getSections()) {
            questionnaire.addItem(toFhirR3(section));
        }
        return questionnaire;
    }

    public ca.uhn.fhir.model.dstu2.resource.Questionnaire toFhirR2() {
        return null;
    }

    private List<Section> getSections() {
        return Section.getByInstrument(instrument.getUri());
    }

    private org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent toFhirR5(models.Component component) {
        org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(component.getLabel());
        itemComponent.setLinkId(component.getUri());
        if (component.getLabel().contains("Name")) {
            itemComponent.setType(org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemType.STRING);
        } else if (component.getLabel().contains("Date")) {
            itemComponent.setType(org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemType.DATE);
        } else {
            itemComponent.setType(org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemType.DISPLAY);
        }
        return itemComponent;
    }

    private org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent toFhirR5(Section section) {
        org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent group = new org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent();
        group.setText(section.getLabel());
        group.setLinkId(section.getUri());
        group.setPrefix(Integer.toString(section.getPosition()));
        group.setType(org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemType.GROUP);
        for (Section childSection : section.getSections()) {
            group.addItem(toFhirR5(childSection));
        }
        for (models.Item item : section.getItems()) {
            group.addItem(toFhirR5(item));
        }
        return group;
    }

    private org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent toFhirR5(models.Item item) {
        org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(item.getLabel());
        itemComponent.setLinkId(item.getUri());
        itemComponent.setPrefix(Integer.toString(item.getPosition()));
        itemComponent.setType(org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemType.CODING);
        if (item.getCodebook() != null && item.getCodebook().getResponseOptions() != null) {
            for (models.ResponseOption responseOption : item.getCodebook().getResponseOptions()) {
                org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemAnswerOptionComponent answerOption = new org.hl7.fhir.r5.model.Questionnaire.QuestionnaireItemAnswerOptionComponent();
                answerOption.setValue(new org.hl7.fhir.r5.model.Coding("", responseOption.getValue(), responseOption.getLabel()));
                itemComponent.addAnswerOption(answerOption);
            }
        }
        return itemComponent;
    }

    private org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent toFhirR4B(models.Component component) {
        org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(component.getLabel());
        itemComponent.setLinkId(component.getUri());
        if (component.getLabel().contains("Name")) {
            itemComponent.setType(org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemType.STRING);
        } else if (component.getLabel().contains("Date")) {
            itemComponent.setType(org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemType.DATE);
        } else {
            itemComponent.setType(org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemType.DISPLAY);
        }
        return itemComponent;
    }

    private org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent toFhirR4B(Section section) {
        org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent group = new org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent();
        group.setText(section.getLabel());
        group.setLinkId(section.getUri());
        group.setPrefix(Integer.toString(section.getPosition()));
        group.setType(org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemType.GROUP);
        for (Section childSection : section.getSections()) {
            group.addItem(toFhirR4B(childSection));
        }
        for (models.Item item : section.getItems()) {
            group.addItem(toFhirR4B(item));
        }
        return group;
    }

    private org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent toFhirR4B(models.Item item) {
        org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(item.getLabel());
        itemComponent.setLinkId(item.getUri());
        itemComponent.setPrefix(Integer.toString(item.getPosition()));
        itemComponent.setType(org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemType.CHOICE);
        if (item.getCodebook() != null && item.getCodebook().getResponseOptions() != null) {
            for (models.ResponseOption responseOption : item.getCodebook().getResponseOptions()) {
                org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemAnswerOptionComponent answerOption = new org.hl7.fhir.r4b.model.Questionnaire.QuestionnaireItemAnswerOptionComponent();
                answerOption.setValue(new org.hl7.fhir.r4b.model.Coding("", responseOption.getValue(), responseOption.getLabel()));
                itemComponent.addAnswerOption(answerOption);
            }
        }
        return itemComponent;
    }

    private org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent toFhirR4(models.Component component) {
        org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(component.getLabel());
        itemComponent.setLinkId(component.getUri());
        if (component.getLabel().contains("Name")) {
            itemComponent.setType(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.STRING);
        } else if (component.getLabel().contains("Date")) {
            itemComponent.setType(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.DATE);
        } else {
            itemComponent.setType(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.DISPLAY);
        }
        return itemComponent;
    }

    private org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent toFhirR4(Section section) {
        org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent group = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        group.setText(section.getLabel());
        group.setLinkId(section.getUri());
        group.setPrefix(Integer.toString(section.getPosition()));
        group.setType(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.GROUP);
        for (Section childSection : section.getSections()) {
            group.addItem(toFhirR4(childSection));
        }
        for (models.Item item : section.getItems()) {
            group.addItem(toFhirR4(item));
        }
        return group;
    }

    private org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent toFhirR4(models.Item item) {
        org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(item.getLabel());
        itemComponent.setLinkId(item.getUri());
        itemComponent.setPrefix(Integer.toString(item.getPosition()));
        itemComponent.setType(org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType.CHOICE);
        if (item.getCodebook() != null && item.getCodebook().getResponseOptions() != null) {
            for (models.ResponseOption responseOption : item.getCodebook().getResponseOptions()) {
                org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemAnswerOptionComponent answerOption = new org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemAnswerOptionComponent();
                answerOption.setValue(new org.hl7.fhir.r4.model.Coding("", responseOption.getValue(), responseOption.getLabel()));
                itemComponent.addAnswerOption(answerOption);
            }
        }
        return itemComponent;
    }

    private org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent toFhirR3(models.Component component) {
        org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(component.getLabel());
        itemComponent.setLinkId(component.getUri());
        if (component.getLabel().contains("Name")) {
            itemComponent.setType(org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType.STRING);
        } else if (component.getLabel().contains("Date")) {
            itemComponent.setType(org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType.DATE);
        } else {
            itemComponent.setType(org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType.DISPLAY);
        }
        return itemComponent;
    }

    private org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent toFhirR3(Section section) {
        org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent group = new org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent();
        group.setText(section.getLabel());
        group.setLinkId(section.getUri());
        group.setPrefix(Integer.toString(section.getPosition()));
        group.setType(org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType.GROUP);
        for (Section childSection : section.getSections()) {
            group.addItem(toFhirR3(childSection));
        }
        for (models.Item item : section.getItems()) {
            group.addItem(toFhirR3(item));
        }
        return group;
    }

    private org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent toFhirR3(models.Item item) {
        org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent itemComponent = new org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent();
        itemComponent.setText(item.getLabel());
        itemComponent.setLinkId(item.getUri());
        itemComponent.setPrefix(Integer.toString(item.getPosition()));
        itemComponent.setType(org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType.CHOICE);
        if (item.getCodebook() != null && item.getCodebook().getResponseOptions() != null) {
            for (models.ResponseOption responseOption : item.getCodebook().getResponseOptions()) {
                org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemOptionComponent option = new org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemOptionComponent();
                option.setValue(new org.hl7.fhir.dstu3.model.Coding("", responseOption.getValue(), responseOption.getLabel()));
                itemComponent.addOption(option);
            }
        }
        return itemComponent;
    }
}
