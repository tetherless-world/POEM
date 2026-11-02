package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import models.Language;
import models.Instrument;
import models.InstrumentAbout;

import java.util.List;

public class ApiController extends Controller {
    public Result getInstrumentAbout(String uri) {
        try {
            if (uri == null || uri.isBlank() || !java.net.URI.create(uri).isAbsolute()) {
                return badRequest(Json.newObject().put("error", "A valid instrument URI is required"));
            }
        } catch (IllegalArgumentException e) {
            return badRequest(Json.newObject().put("error", "A valid instrument URI is required"));
        }

        try {
            InstrumentAbout about = InstrumentAbout.getByUri(uri);
            if (about == null) {
                return notFound(Json.newObject().put("error", "Instrument not found"));
            }
            return ok(Json.toJson(about));
        } catch (Exception e) {
            play.Logger.of(ApiController.class).error("Failed to retrieve instrument details for {}", uri, e);
            return internalServerError(Json.newObject().put("error", "Failed to retrieve instrument details"));
        }
    }

    
    // Inject your database service/repository here
    // @Inject
    // private DatabaseService databaseService;
    
    public Result getLanguages() {
        try {
            List<Language> languages = Language.getAll();
            
            ArrayNode languagesJson = Json.newArray();
            
            for (Language language : languages) {
                ObjectNode languageNode = Json.newObject();
                languageNode.put("value", language.getBCP47());
                languageNode.put("label", language.getDisplayLabel());
                languagesJson.add(languageNode);
            }
            
            return ok(languagesJson);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("error", "Failed to retrieve languages");
            error.put("message", e.getMessage());
            return internalServerError(error);
        }
    }
    
    public Result getQuestionnaires() {
        try {
            List<Instrument> questionnaires = Instrument.getAll();
            
            ArrayNode questionnairesJson = Json.newArray();
            
            for (Instrument questionnaire : questionnaires) {
                if (questionnaire.getDeprecated() == 0 && questionnaire.getLabel().startsWith("RCADS")) {
                    ObjectNode questionnaireNode = Json.newObject();
                    questionnaireNode.put("value", questionnaire.getUri());
                    questionnaireNode.put("label", questionnaire.getLabel());
                    questionnaireNode.put("language", questionnaire.getLanguage() != null ? questionnaire.getLanguage().getBCP47() : null);
                    questionnairesJson.add(questionnaireNode);
                }
            }
            
            return ok(questionnairesJson);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("error", "Failed to retrieve questionnaires");
            error.put("message", e.getMessage());
            return internalServerError(error);
        }
    }
}
