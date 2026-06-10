# JVM Traffic Recorder Agent

This project is a Java 11 JVM agent that records runtime interactions from Spring Boot and generic JVM services. It is designed for Kafka-heavy systems where captured traffic can become useful test fixtures for Copilot or another AI coding assistant.

The agent currently targets:

- Kafka consumers and producers
- Spring Kafka listener execution
- Spring Boot startup lifecycle
- HTTP server requests through embedded Tomcat/Spring MVC paths
- HTTP clients through `RestTemplate`, `WebClient`, and Java `HttpClient`
- Redis through Jedis and Lettuce
- MongoDB Java driver internal command paths
- YAML and in-memory recording sinks

## Build

```bash
gradle :agent-javaagent:shadowJar
```

This creates the javaagent jar at:

```text
agent-javaagent/build/libs/agent-javaagent-0.1.0-SNAPSHOT.jar
```

This repository does not currently include a Gradle wrapper. Add one with `gradle wrapper` in an environment where Gradle is installed.

## Run With A Spring Boot Service

```bash
java \
  -javaagent:/path/to/agent-javaagent-0.1.0-SNAPSHOT.jar=config=/path/to/agent.yaml \
  -jar your-service.jar
```

Sample config:

```text
sample-config/agent.yaml
```

## Output

By default, recordings are written as one YAML file per trace:

```text
recordings/rec-abc123.yaml
```

The YAML is intentionally readable by humans and AI coding tools:

```yaml
version: 1
service: order-service
traceId: rec-abc123
rootType: KAFKA_CONSUME
interactions:
  - type: KAFKA_CONSUME
    operation: poll
    metadata:
      topic: orders.created
      key: customer-123
  - type: MONGO_COMMAND
    operation: FindOperation
  - type: REDIS_COMMAND
    operation: GET
  - type: KAFKA_PUBLISH
    operation: send
    metadata:
      topic: orders.validated
```

## Storage Extension Point

Add new sinks by implementing:

```java
com.autogen.agent.api.RecordingSink
```

Initial sinks:

- `YamlFileRecordingSink`
- `InMemoryRecordingSink`

Good next sinks:

- JSONL file sink
- S3 sink
- Kafka sink
- HTTP collector sink

## Current Notes

This is a first implementation scaffold with real agent hooks. The next hardening pass should add integration tests against specific library versions for Spring Boot 2.x/3.x, Spring Kafka, Kafka clients, Jedis, Lettuce, and MongoDB driver versions used by your services.
