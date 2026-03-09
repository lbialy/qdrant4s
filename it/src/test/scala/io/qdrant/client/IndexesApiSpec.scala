package io.qdrant.client

import io.qdrant.client.model.*

class IndexesApiSpec extends QdrantSuite:

  test("createFieldIndex creates a payload index on a collection") {
    withContainers { container =>
      val api = indexesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.createFieldIndex(
        name,
        wait = Some(true),
        createFieldIndex = Some(CreateFieldIndex(
          fieldName = "uuid_field",
          fieldSchema = Some(PayloadFieldSchema.PayloadSchemaTypeValue(PayloadSchemaType.uuid))
        ))
      ))
      val result = response.body
      assert(result.isRight, s"createFieldIndex failed: $result")
      assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("deleteFieldIndex deletes a field index") {
    withContainers { container =>
      val api = indexesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      // Create index first
      backend.send(api.createFieldIndex(
        name,
        wait = Some(true),
        createFieldIndex = Some(CreateFieldIndex(
          fieldName = "uuid_field",
          fieldSchema = Some(PayloadFieldSchema.PayloadSchemaTypeValue(PayloadSchemaType.uuid))
        ))
      ))

      // Delete it
      val response = backend.send(api.deleteFieldIndex(
        name,
        fieldName = "uuid_field",
        wait = Some(true)
      ))
      val result = response.body
      assert(result.isRight, s"deleteFieldIndex failed: $result")
      assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

      backend.send(colApi.deleteCollection(name))
    }
  }
