# Copilot Test Generation Prompt

Use this prompt with the generated YAML recordings and the service source code.

```text
You are helping me write high-value tests for this Java/Spring Boot service.

I am providing:
- The service source code.
- Runtime traffic recordings captured by a JVM agent.
- YAML files containing Kafka, HTTP, Redis, and Mongo interactions.

Please inspect the source code and the YAML recordings together. Generate tests that verify the behavior shown in the recordings.

Prioritize:
- Kafka listener tests for consumed records.
- Kafka producer assertions for emitted topics, keys, headers, and payloads.
- HTTP controller/client tests where applicable.
- Redis and Mongo dependency stubbing based on the recorded reads/writes.
- Assertions on important business fields, not only "method was called" checks.
- Redaction-safe fixtures.

For each test, explain which YAML trace it came from and which production path it exercises.
```
