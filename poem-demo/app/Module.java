import com.google.inject.AbstractModule;
import services.chat.classifier.IntentClassifier;
import services.chat.classifier.OpenAIIntentClassifier;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        bind(IntentClassifier.class).to(OpenAIIntentClassifier.class);
    }
}
