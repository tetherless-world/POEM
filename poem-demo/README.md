# POEM demo graph storage

`POEMModel` stores the combined application data and ontologies in a persistent
Jena TDB2 database at `./tdb2`, relative to the app's working directory. Override
the location with the JVM property `-Dpoem.tdb2.directory=/path/to/store`.
Use a persistent volume for this directory when running in Docker.

The first access imports the data and ontology files. On reopening the store,
a SHA-256 fingerprint of the source file names and contents determines whether
to reuse the graph or replace it. Existing stores without a fingerprint are
reimported once. Changes during a running process require `POEMModel.refresh()`
or a restart. The replacement and fingerprint are committed atomically; a failed
import preserves the previous graph and is retried on the next initialization.

## Source directories and deployment

Run `sbt run` from this directory for development. Data is found in `./data`
or, if absent, `./dist/data`; ontologies are found in `./ontology` or, if absent,
`../ontology`. Explicit JVM properties override discovery:

```text
-Dpoem.data.directory=/absolute/path/to/data
-Dpoem.ontology.directory=/absolute/path/to/ontology
```

An explicit path must exist; it never silently falls back to another location.
Missing source directories, missing/empty required data files, and missing/empty
ontology inputs cause initialization to fail instead of storing an incomplete graph.

`sbt dist` packages the repository's sibling `../ontology` files into the
distribution's `ontology/` directory. The build rejects absent or empty ontology
inputs. The demo Dockerfile copies the root ontology directory into the build
stage before packaging, then copies the complete distribution into the runtime
image. The GitHub image workflow uses the repository root as its build context,
so the same package layout works locally and in CI. Required ontology files must
be included in the checkout before building the image.

The runtime working directory is `/poem-demo`, containing both `data/` and
`ontology/`. Keep source files unchanged while an import is running. A persistent
TDB2 volume will automatically receive changed sources on the next startup.

## Transactions

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
