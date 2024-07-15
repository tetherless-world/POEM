package controllers;

import java.util.List;

import models.Instrument;
import models.QuestionnaireScale;
import play.mvc.*;

import static play.libs.Scala.asScala;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(views.html.index.render());
    }

    public Result index2(Http.Request request) {
        List<Instrument> instruments = Instrument.getAll();
        List<QuestionnaireScale> scales = QuestionnaireScale.getAll();
        //return ok(views.html.index.render());
        return ok(views.html.index2.render(asScala(instruments), asScala(scales), request));
    }
}
