# qdrant4s

Scala 3 client for [Qdrant](https://github.com/qdrant/qdrant) vector search engine, generated from the official OpenAPI spec.

Built on [sttp client4](https://sttp.softwaremill.com/en/stable/) — requests are pure data descriptions, so you can interpret them with any sttp backend: synchronous or asynchronous (Future, ZIO, cats-effect, Monix). Use whatever HTTP stack fits your application.

Targets **Qdrant v1.17.x**. Published for **JVM** and **Scala Native**.

## Installation

Scala CLI:

```scala
//> using scala 3.3.7
//> using dep "ma.chinespirit::qdrant4s:$VERSION$"
```

sbt:

```sbt
// build.sbt
libraryDependencies += "ma.chinespirit" %% "qdrant4s" % "<version>"

// or for Scala Native
libraryDependencies += "ma.chinespirit" %%% "qdrant4s" % "<version>"
```

You'll also need an sttp backend. Pick one that fits your stack:

```sbt
// Synchronous (JVM) — uses JDK's built-in HttpClient, no extra deps needed
// DefaultSyncBackend() is available from sttp core

// cats-effect
"com.softwaremill.sttp.client4" %% "cats" % "4.0.7"

// ZIO
"com.softwaremill.sttp.client4" %% "zio" % "4.0.7"

// Pekko / Akka Streams
"com.softwaremill.sttp.client4" %% "pekko-http-backend" % "4.0.7"

// ... and many more — see sttp docs
```

## Quick start

```scala
//> using scala 3.3.7
//> using dep "ma.chinespirit::qdrant4s:$VERSION$"

import sttp.client4.*
import io.qdrant.client.api.*
import io.qdrant.client.model.*

val backend = DefaultSyncBackend()

// Create API instances pointing to your Qdrant instance
val collections = CollectionsApi.withApiKeyAuth("http://localhost:6333", "your-api-key")
val points      = PointsApi.withApiKeyAuth("http://localhost:6333", "your-api-key")
val search      = SearchApi.withApiKeyAuth("http://localhost:6333", "your-api-key")

// Create a collection
val createResponse = backend.send(collections.createCollection(
  "my_collection",
  createCollection = Some(CreateCollection(
    vectors = Some(VectorsConfig.VectorParamsValue(
      VectorParams(size = 4, distance = Distance.Cosine)
    ))
  ))
))

// Upsert points
val upsertResponse = backend.send(points.upsertPoints(
  "my_collection",
  wait = Some(true),
  pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(
    points = Seq(
      PointStruct(
        id = ExtendedPointId.LongValue(1),
        vector = VectorStruct.SeqFloatValue(Seq(0.05f, 0.61f, 0.76f, 0.74f)),
        payload = Some(Map("city" -> io.circe.Json.fromString("Berlin")))
      ),
      PointStruct(
        id = ExtendedPointId.LongValue(2),
        vector = VectorStruct.SeqFloatValue(Seq(0.19f, 0.81f, 0.75f, 0.11f)),
        payload = Some(Map("city" -> io.circe.Json.fromString("London")))
      ),
    )
  )))
))

// Search for nearest neighbors
val searchResponse = backend.send(search.searchPoints(
  "my_collection",
  searchRequest = Some(SearchRequest(
    vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
    limit = 3,
    withPayload = Some(WithPayloadInterface.BooleanValue(true))
  ))
))

searchResponse.body match {
  case Right(result) => println(result.result)
  case Left(error)   => println(s"Error: $error")
}
```

## API coverage

The client covers the full Qdrant REST API:

| API | Description |
|-----|-------------|
| `CollectionsApi` | Create, update, delete, and inspect collections |
| `PointsApi` | CRUD operations on points (vectors + payloads) |
| `SearchApi` | Search, recommend, discover, query, and matrix similarity |
| `IndexesApi` | Manage payload field indexes |
| `AliasesApi` | Collection alias management |
| `SnapshotsApi` | Create and manage snapshots |
| `DistributedApi` | Cluster status and operations |
| `ServiceApi` | Health checks, telemetry, metrics |

## How it's generated

The client is generated from the [Qdrant OpenAPI spec](https://github.com/qdrant/qdrant) using a vendored fork of [openapi-generator](https://github.com/OpenAPITools/openapi-generator) with the `scala-sttp4-jsoniter` generator. The fork adds proper support for `anyOf`/`oneOf` schemas as sealed traits.

## License

[MIT](LICENSE.md)
