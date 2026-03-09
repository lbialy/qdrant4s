package io.qdrant.client

import io.qdrant.client.model.*

class CollectionsApiSpec extends QdrantSuite:

  test("createCollection creates a collection with dense vector config") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      val response = backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))
      val result = response.body
      assert(result.isRight, s"createCollection failed: $result")
      assertEquals(result.toOption.get.result, Some(true))

      // cleanup
      backend.send(api.deleteCollection(name))
    }
  }

  test("getCollections lists collections including the created one") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.getCollections)
      val result = response.body
      assert(result.isRight, s"getCollections failed: $result")
      val collections = result.toOption.get.result.get.collections
      assert(collections.exists(_.name == name), s"collection $name not found in $collections")

      backend.send(api.deleteCollection(name))
    }
  }

  test("getCollection returns collection info with matching config") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.getCollection(name))
      val result = response.body
      assert(result.isRight, s"getCollection failed: $result")
      val info = result.toOption.get.result.get
      assertEquals(info.status, CollectionStatus.`green`)

      backend.send(api.deleteCollection(name))
    }
  }

  test("collectionExists returns true for existing collection") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.collectionExists(name))
      val result = response.body
      assert(result.isRight, s"collectionExists failed: $result")
      assertEquals(result.toOption.get.result.get.exists, true)

      backend.send(api.deleteCollection(name))
    }
  }

  test("updateCollection updates optimizer config") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val updateResponse = backend.send(api.updateCollection(
        name,
        updateCollection = Some(UpdateCollection(
          optimizersConfig = Some(OptimizersConfigDiff(
            indexingThreshold = Some(50000)
          ))
        ))
      ))
      val updateResult = updateResponse.body
      assert(updateResult.isRight, s"updateCollection failed: $updateResult")
      assertEquals(updateResult.toOption.get.result, Some(true))

      backend.send(api.deleteCollection(name))
    }
  }

  test("deleteCollection deletes collection and it no longer exists") {
    withContainers { container =>
      val api = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(api.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val deleteResponse = backend.send(api.deleteCollection(name))
      val deleteResult = deleteResponse.body
      assert(deleteResult.isRight, s"deleteCollection failed: $deleteResult")
      assertEquals(deleteResult.toOption.get.result, Some(true))

      val existsResponse = backend.send(api.collectionExists(name))
      assertEquals(existsResponse.body.toOption.get.result.get.exists, false)
    }
  }
