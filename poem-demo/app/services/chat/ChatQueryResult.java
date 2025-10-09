package services.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple container for SPARQL query results.
 */
public final class ChatQueryResult {

    private final List<String> variables;
    private final List<Map<String, String>> rows;

    public ChatQueryResult(List<String> variables, List<Map<String, String>> rows) {
        this.variables = List.copyOf(Objects.requireNonNull(variables, "variables must not be null"));
        this.rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
    }

    public List<String> getVariables() {
        return variables;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int getRowCount() {
        return rows.size();
    }

    public static ChatQueryResult empty() {
        return new ChatQueryResult(Collections.emptyList(), Collections.emptyList());
    }
}
