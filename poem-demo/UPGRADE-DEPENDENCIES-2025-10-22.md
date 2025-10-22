Dependency upgrade: jena-arq and openai-java
===========================================

Date: 2025-10-22

Summary
-------
This branch updates two dependencies in `poem-demo/build.sbt`:

- `org.apache.jena:jena-arq` 5.5.0 -> 5.6.0
- `com.openai:openai-java` 4.3.0 -> 4.6.1

Verification
------------
- Ran `sbt compile` in the `poem-demo` module after the bumps; compilation succeeded.

Notes / recommendations
-----------------------
- Review the release notes for Jena 5.6.0 and OpenAI Java 4.6.1 for any API changes.
- Run the project's test suite (`sbt test`) and exercise runtime features that use Jena and the OpenAI client.

Files changed
-------------
- `poem-demo/build.sbt` (dependency version bumps)

If anything else should be included in this PR (additional tests, changelog location, or a rollback plan), tell me and I'll update the branch.
