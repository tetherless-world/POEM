package services.chat.classifier;

import services.chat.intent.ChatIntent;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a user message into a structured {@link ChatIntent}.
 */
public interface IntentClassifier {

    Optional<ChatIntent> classify(String message, ClassificationContext context);

    record ClassificationContext(
            List<Candidate> collections,
            List<Candidate> instruments,
            List<Candidate> scales,
            List<Candidate> concepts) {

        public static ClassificationContext empty() {
            return new ClassificationContext(List.of(), List.of(), List.of(), List.of());
        }
    }

    record Candidate(String uri, String label) {
    }
}
