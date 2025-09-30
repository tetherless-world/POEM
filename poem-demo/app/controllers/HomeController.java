package controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.inject.Inject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import forms.Query;
import models.Instrument;
import models.ItemConcept;
import models.QuestionnaireScale;
import play.data.Form;
import play.mvc.*;

import static play.libs.Scala.asScala;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    @Inject play.data.FormFactory formFactory;
    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index(Http.Request request) {
        List<Instrument> instruments = Instrument.getAll();
        List<QuestionnaireScale> scales = QuestionnaireScale.getAll();
        return ok(views.html.index.render(asScala(instruments), asScala(scales), request));
    }

    public Result query(Http.Request request) {
        Form<Query> queryForm = formFactory.form(Query.class);
        Query query = queryForm.bindFromRequest(request).get();
        System.out.println(query.getQuery());
        System.out.println(query.getParameter());
        if (query.getQuery().equals("query0")) {
            Instrument instrument = Instrument.getByUri(query.getParameter());
            List<QuestionnaireScale> scales = QuestionnaireScale.getByInstrument(instrument);
            return ok(new Gson().toJson(scales));
        } else if (query.getQuery().equals("query1")) {
            QuestionnaireScale scale = QuestionnaireScale.getByUri(query.getParameter());
            List<ItemConcept> itemConcepts = ItemConcept.getByScale(scale);
            return ok(new Gson().toJson(itemConcepts));
        } else if (query.getQuery().equals("query2")) {
            //Instrument instrument = Instrument.getByUri(query.getParameter());
            List<Instrument> sources = Instrument.getSource(query.getParameter());
            return ok(new Gson().toJson(sources));
        } else if (query.getQuery().equals("query3")) {
            //Instrument instrument = Instrument.getByUri(query.getParameter());
            List<Instrument> targets = Instrument.getTarget(query.getParameter());
            return ok(new Gson().toJson(targets));
        } else if (query.getQuery().equals("query4")) {
            Instrument instrument0 = Instrument.getByUri(query.getParameter());
            Instrument instrument1 = Instrument.getByUri(query.getParameter1());
            List<ItemConcept> itemConcepts = ItemConcept.getByInstruments(instrument0, instrument1);
            return ok(new Gson().toJson(itemConcepts));
        } else if (query.getQuery().equals("query9")) {
            List<Instrument> targets = Instrument.getTarget(query.getParameter());
            return ok(new Gson().toJson(targets));
        }
        List<String> places = Arrays.asList("Buenos Aires", "Córdoba", "La Plata");
        return ok(new Gson().toJson(places));
    }

    public Result excel(String uri) {
        Instrument instrument = Instrument.getByUri(uri);
        if (instrument == null) {
            return notFound("Instrument not found");
        }
        models.excel.Questionnaire excelQuestionnaire = new models.excel.Questionnaire(instrument);
        byte[] excelBytes = excelQuestionnaire.toXLSX();
        String filename = instrument.getLabel() + ".xlsx";
        return ok(excelBytes)
            .withHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .as("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }


    public Result fhir(String uri, String format, String version) {
        Instrument instrument = Instrument.getByUri(uri);
        if (instrument == null) {
            return notFound("Instrument not found");
        }
        models.fhir.Questionnaire questionnaire = new models.fhir.Questionnaire(instrument);

        if (format.equals("json")) {
            if (version.equals("r5")) {
                FhirContext fhirContext = FhirContext.forR5();
                IParser parser = fhirContext.newJsonParser();
                String serialized = parser.encodeResourceToString(questionnaire.toFhirR5());
                return ok(serialized)
                    // .withHeader("Content-Disposition", "attachment; filename=\"" + instrument.getLabel() + "_FHIR-R5.json\"")
                    .as("application/json");
            } else if (version.equals("r4b")) {
                FhirContext fhirContext = FhirContext.forR4B();
                IParser parser = fhirContext.newJsonParser();
                String serialized = parser.encodeResourceToString(questionnaire.toFhirR4B());
                return ok(serialized)
                    // .withHeader("Content-Disposition", "attachment; filename=\"" + instrument.getLabel() + "_FHIR-R4B.json\"")
                    .as("application/json");
            } else if (version.equals("r4")) {
                FhirContext fhirContext = FhirContext.forR4();
                IParser parser = fhirContext.newJsonParser();
                String serialized = parser.encodeResourceToString(questionnaire.toFhirR4());
                return ok(serialized)
                    // .withHeader("Content-Disposition", "attachment; filename=\"" + instrument.getLabel() + "_FHIR-R4.json\"")
                    .as("application/json");
            } else if (version.equals("r3")) {
                FhirContext fhirContext = FhirContext.forDstu3();
                IParser parser = fhirContext.newJsonParser();
                String serialized = parser.encodeResourceToString(questionnaire.toFhirR3());
                return ok(serialized)
                    // .withHeader("Content-Disposition", "attachment; filename=\"" + instrument.getLabel() + "_FHIR-R3.json\"")
                    .as("application/json");
            } else if (version.equals("r2")) {
                FhirContext fhirContext = FhirContext.forDstu2();
                IParser parser = fhirContext.newJsonParser();
                String serialized = parser.encodeResourceToString(questionnaire.toFhirR2());
                return ok(serialized)
                    // .withHeader("Content-Disposition", "attachment; filename=\"" + instrument.getLabel() + "_FHIR-R2.json\"")
                    .as("application/json");
            }
        }
        return ok("FHIR endpoint is not implemented yet.");
    }
}
