package services.chat.intent;

/**
 * Represents a structured, parameterised intent that can be executed against the POEM knowledge graph.
 */
public interface ChatIntent {

    /**
     * Machine-readable intent identifier.
     */
    String name();

    /**
     * User-friendly description of the intent.
     */
    String description();

    /**
     * Builds the SPARQL query that fulfils this intent.
     */
    String toSparql();
}
