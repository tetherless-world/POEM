package services.chat.classifier;

import services.chat.intent.ChatIntent;

import java.util.Optional;

/**
 * Intent classifier that never returns a result, used as a safe fallback.
 */
public class NoopIntentClassifier implements IntentClassifier {
    @Override
    public Optional<ChatIntent> classify(String message, ClassificationContext context) {
        return Optional.empty();
    }
}
