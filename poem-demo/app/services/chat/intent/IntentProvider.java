package services.chat.intent;

import java.util.List;
import java.util.Optional;

/**
 * Provides metadata and construction logic for a chat intent.
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
public interface IntentProvider {

    String name();

    String description();

    Optional<ChatIntent> create(List<String> collectionUris,
                                List<String> instrumentUris,
                                List<String> scaleUris,
                                List<String> conceptUris);
}
