# POEM demo graph storage

`POEMModel` stores the combined application data and ontologies in a persistent
Jena TDB2 database at `./tdb2`, relative to the app's working directory. Override
the location with the JVM property `-Dpoem.tdb2.directory=/path/to/store`.
Use a persistent volume for this directory when running in Docker.

The first access imports the data and ontology files. Later starts reuse the
stored graph. Call `POEMModel.refresh()` to import source changes; the replacement
is committed atomically, and a failed import preserves the previous graph.

Model reads must run inside a transaction, including iteration over query results:

```java
try (var transaction = POEMModel.readTransaction()) {
    Model model = POEMModel.getModel();
    // Read model properties or execute and consume a SPARQL query here.
}
```

Nested read scopes share the current thread's transaction. `POEMModel.close()`
releases the database; it is also called on JVM shutdown. Close only after readers
have finished. A store directory belongs to one running JVM at a time.

Pass `POEMModel.getDataset()` to SPARQL query executions inside the read scope to
use TDB2's query engine.
