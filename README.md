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
- YAML, in-memory, Kafka, and MongoDB recording sinks
- Async handoff from application threads to a bounded writer executor

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

Supported storage types:

```yaml
storage:
  type: yaml
  outputDir: ./recordings
```

```yaml
storage:
  type: memory
```

```yaml
storage:
  type: kafka
  kafka:
    bootstrapServers: localhost:9092
    topic: traffic-recordings
    clientId: traffic-recorder-agent
    compressionType: lz4
    acks: "1"
```

```yaml
storage:
  type: mongo
  mongo:
    connectionString: mongodb://localhost:27017
    database: traffic_recorder
    ttlDays: 30
```

The Kafka sink writes one JSON message per updated trace, keyed by `traceId`.
The Mongo sink upserts one document per trace using `_id = traceId`. By default, Mongo uses the resolved service name as the collection name. Set `storage.mongo.collection` only when you want to override that default. Mongo recordings also get a TTL index on `recordedAt`; the default retention is 30 days.

```yaml
storage:
  type: mongo
  mongo:
    connectionString: mongodb://localhost:27017
    database: traffic_recorder
    collection: custom_recorded_traces
    ttlDays: 30
```

## Spring Boot Configuration

If the `-javaagent` argument does not include `config=/path/to/agent.yaml`, the agent looks for `application.yml` or `application.yaml` in the current directory, `config/`, and `src/main/resources/`.

Use an agent-specific section to keep the application config clean:

```yaml
spring:
  application:
    name: order-service

jvmTraceToTest:
  enabled: true
  storage:
    type: mongo
    mongo:
      connectionString: mongodb://localhost:27017
      database: traffic_recorder
      ttlDays: 30
```

Supported section names are `jvmTraceToTest`, `jvm-trace-to-test`, `traceToTest`, `trafficRecorderAgent`, and `trafficRecorder`. If `serviceName` is not specified in the agent section, the agent uses `spring.application.name`.

## Async Recording

The agent defaults to async recording so application, Kafka listener, and HTTP request threads do not perform sink IO directly.

```yaml
async:
  enabled: true
  workerThreads: 1
  queueSize: 10000
  shutdownDrainMillis: 5000
```

If the bounded queue is full, the agent drops new recording interactions and logs a counter. This is intentional: preserving application latency is more important than recording every event during overload.

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
- `KafkaRecordingSink`
- `MongoRecordingSink`

Good next sinks:

- JSONL file sink
- S3 sink
- HTTP collector sink

## Current Notes

This is a first implementation scaffold with real agent hooks. The next hardening pass should add integration tests against specific library versions for Spring Boot 2.x/3.x, Spring Kafka, Kafka clients, Jedis, Lettuce, and MongoDB driver versions used by your services.
