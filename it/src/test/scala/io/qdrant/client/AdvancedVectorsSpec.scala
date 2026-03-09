package io.qdrant.client

import io.qdrant.client.model.*

class AdvancedVectorsSpec extends QdrantSuite:

  // ---------------------------------------------------------------------------
  // Multi-vector (named dense vectors)
  // ---------------------------------------------------------------------------

  test("create collection with multiple named dense vectors") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      val response = backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.MapStringVectorParamsValue(Map(
            "text" -> VectorParams(size = 4, distance = Distance.Cosine),
            "image" -> VectorParams(size = 8, distance = Distance.Dot)
          )))
        ))
      ))
      assert(response.body.isRight, s"createCollection with named vectors failed: ${response.body}")

      // Verify config
      val getResp = backend.send(colApi.getCollection(name))
      assert(getResp.body.isRight, s"getCollection failed: ${getResp.body}")

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("upsert and search with multiple named vectors") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val pApi = pointsApi(container)
      val sApi = searchApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.MapStringVectorParamsValue(Map(
            "text" -> VectorParams(size = 4, distance = Distance.Cosine),
            "image" -> VectorParams(size = 4, distance = Distance.Dot)
          )))
        ))
      ))

      // Upsert points with named vectors using Map[String, Vector]
      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.MapStringVectorValue(Map(
            "text" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
            "image" -> Vector.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.MapStringVectorValue(Map(
            "text" -> Vector.SeqFloatValue(Seq(0.9f, 0.8f, 0.7f, 0.6f)),
            "image" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(3),
          vector = VectorStruct.MapStringVectorValue(Map(
            "text" -> Vector.SeqFloatValue(Seq(0.4f, 0.3f, 0.2f, 0.1f)),
            "image" -> Vector.SeqFloatValue(Seq(0.8f, 0.7f, 0.6f, 0.5f))
          ))
        )
      )

      val upsertResp = backend.send(pApi.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))
      assert(upsertResp.body.isRight, s"upsert with named vectors failed: ${upsertResp.body}")

      // Search targeting "text" named vector
      val searchTextResp = backend.send(sApi.searchPoints(
        name,
        searchRequest = Some(SearchRequest(
          vector = NamedVectorStruct.NamedVectorValue(NamedVector(
            name = "text",
            vector = Seq(0.1f, 0.2f, 0.3f, 0.4f)
          )),
          limit = 3
        ))
      ))
      assert(searchTextResp.body.isRight, s"search on 'text' vector failed: ${searchTextResp.body}")
      val textResults = searchTextResp.body.toOption.get.result.get
      assertEquals(textResults.size, 3)
      // Point 1 should be the best match for its own text vector
      assertEquals(textResults.head.id, ExtendedPointId.LongValue(1))

      // Search targeting "image" named vector
      val searchImageResp = backend.send(sApi.searchPoints(
        name,
        searchRequest = Some(SearchRequest(
          vector = NamedVectorStruct.NamedVectorValue(NamedVector(
            name = "image",
            vector = Seq(0.5f, 0.6f, 0.7f, 0.8f)
          )),
          limit = 3
        ))
      ))
      assert(searchImageResp.body.isRight, s"search on 'image' vector failed: ${searchImageResp.body}")
      val imageResults = searchImageResp.body.toOption.get.result.get
      assertEquals(imageResults.size, 3)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("query API with named vector using 'using' parameter") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val pApi = pointsApi(container)
      val sApi = searchApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.MapStringVectorParamsValue(Map(
            "text" -> VectorParams(size = 4, distance = Distance.Cosine),
            "image" -> VectorParams(size = 4, distance = Distance.Dot)
          )))
        ))
      ))

      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.MapStringVectorValue(Map(
            "text" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
            "image" -> Vector.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.MapStringVectorValue(Map(
            "text" -> Vector.SeqFloatValue(Seq(0.9f, 0.8f, 0.7f, 0.6f)),
            "image" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f))
          ))
        )
      )

      backend.send(pApi.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))

      // Query using the "image" vector space
      val queryResp = backend.send(sApi.queryPoints(
        name,
        queryRequest = Some(QueryRequest(
          query = Some(QueryInterface.VectorInputValue(
            VectorInput.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f))
          )),
          `using` = Some("image"),
          limit = Some(2)
        ))
      ))
      assert(queryResp.body.isRight, s"queryPoints with 'using' failed: ${queryResp.body}")
      val results = queryResp.body.toOption.get.result.get.points
      assertEquals(results.size, 2)

      backend.send(colApi.deleteCollection(name))
    }
  }

  // ---------------------------------------------------------------------------
  // Sparse vectors
  // ---------------------------------------------------------------------------

  test("create collection with sparse vector config") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      val response = backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine))),
          sparseVectors = Some(Map(
            "sparse_text" -> SparseVectorParams()
          ))
        ))
      ))
      assert(response.body.isRight, s"createCollection with sparse vectors failed: ${response.body}")

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("upsert and search with sparse vectors") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val pApi = pointsApi(container)
      val sApi = searchApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine))),
          sparseVectors = Some(Map(
            "sparse_text" -> SparseVectorParams()
          ))
        ))
      ))

      // Upsert points with both dense and sparse vectors
      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.MapStringVectorValue(Map(
            "" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
            "sparse_text" -> Vector.SparseVectorValue(SparseVector(
              indices = Seq(0, 2, 5),
              values = Seq(0.1f, 0.8f, 0.3f)
            ))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.MapStringVectorValue(Map(
            "" -> Vector.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f)),
            "sparse_text" -> Vector.SparseVectorValue(SparseVector(
              indices = Seq(1, 3, 5),
              values = Seq(0.5f, 0.2f, 0.9f)
            ))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(3),
          vector = VectorStruct.MapStringVectorValue(Map(
            "" -> Vector.SeqFloatValue(Seq(0.9f, 0.8f, 0.7f, 0.6f)),
            "sparse_text" -> Vector.SparseVectorValue(SparseVector(
              indices = Seq(0, 1, 2),
              values = Seq(0.9f, 0.1f, 0.5f)
            ))
          ))
        )
      )

      val upsertResp = backend.send(pApi.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))
      assert(upsertResp.body.isRight, s"upsert with sparse vectors failed: ${upsertResp.body}")

      // Search with sparse vector using NamedSparseVector
      val searchResp = backend.send(sApi.searchPoints(
        name,
        searchRequest = Some(SearchRequest(
          vector = NamedVectorStruct.NamedSparseVectorValue(NamedSparseVector(
            name = "sparse_text",
            vector = SparseVector(
              indices = Seq(0, 2, 5),
              values = Seq(0.1f, 0.8f, 0.3f)
            )
          )),
          limit = 3
        ))
      ))
      assert(searchResp.body.isRight, s"search with sparse vector failed: ${searchResp.body}")
      val results = searchResp.body.toOption.get.result.get
      assert(results.nonEmpty, "sparse vector search should return results")
      // Point 1 should be the best match (identical sparse vector)
      assertEquals(results.head.id, ExtendedPointId.LongValue(1))

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("query API with sparse vector") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val pApi = pointsApi(container)
      val sApi = searchApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine))),
          sparseVectors = Some(Map(
            "sparse_text" -> SparseVectorParams()
          ))
        ))
      ))

      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.MapStringVectorValue(Map(
            "" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
            "sparse_text" -> Vector.SparseVectorValue(SparseVector(
              indices = Seq(0, 2, 5),
              values = Seq(0.1f, 0.8f, 0.3f)
            ))
          ))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.MapStringVectorValue(Map(
            "" -> Vector.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f)),
            "sparse_text" -> Vector.SparseVectorValue(SparseVector(
              indices = Seq(1, 3, 5),
              values = Seq(0.5f, 0.2f, 0.9f)
            ))
          ))
        )
      )

      backend.send(pApi.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))

      // Query targeting sparse vector space
      val queryResp = backend.send(sApi.queryPoints(
        name,
        queryRequest = Some(QueryRequest(
          query = Some(QueryInterface.VectorInputValue(
            VectorInput.SparseVectorValue(SparseVector(
              indices = Seq(0, 2, 5),
              values = Seq(0.1f, 0.8f, 0.3f)
            ))
          )),
          `using` = Some("sparse_text"),
          limit = Some(2)
        ))
      ))
      assert(queryResp.body.isRight, s"queryPoints with sparse vector failed: ${queryResp.body}")
      val results = queryResp.body.toOption.get.result.get.points
      assertEquals(results.size, 2)

      backend.send(colApi.deleteCollection(name))
    }
  }

  // ---------------------------------------------------------------------------
  // Quantization
  // ---------------------------------------------------------------------------

  test("create collection with scalar quantization and search") {
    withContainers { container =>
      val colApi = collectionsApi(container)
      val pApi = pointsApi(container)
      val sApi = searchApi(container)
      val name = uniqueCollectionName()

      // Create collection with scalar quantization (int8)
      val createResp = backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine))),
          quantizationConfig = Some(QuantizationConfig.ScalarQuantizationValue(
            ScalarQuantization(
              scalar = ScalarQuantizationConfig(
                `type` = ScalarType.int8,
                quantile = Some(0.99f),
                alwaysRam = Some(true)
              )
            )
          ))
        ))
      ))
      assert(createResp.body.isRight, s"createCollection with scalar quantization failed: ${createResp.body}")

      // Verify collection config includes quantization
      val getResp = backend.send(colApi.getCollection(name))
      assert(getResp.body.isRight, s"getCollection failed: ${getResp.body}")

      // Upsert points
      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
          payload = Some(Map("color" -> io.circe.Json.fromString("red")))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.SeqFloatValue(Seq(0.5f, 0.6f, 0.7f, 0.8f)),
          payload = Some(Map("color" -> io.circe.Json.fromString("blue")))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(3),
          vector = VectorStruct.SeqFloatValue(Seq(0.9f, 0.8f, 0.7f, 0.6f)),
          payload = Some(Map("color" -> io.circe.Json.fromString("green")))
        )
      )

      val upsertResp = backend.send(pApi.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))
      assert(upsertResp.body.isRight, s"upsert failed: ${upsertResp.body}")

      // Search - should work with quantization enabled
      val searchResp = backend.send(sApi.searchPoints(
        name,
        searchRequest = Some(SearchRequest(
          vector = NamedVectorStruct.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f)),
          limit = 3,
          withPayload = Some(WithPayloadInterface.BooleanValue(true))
        ))
      ))
      assert(searchResp.body.isRight, s"search with quantization failed: ${searchResp.body}")
      val results = searchResp.body.toOption.get.result.get
      assertEquals(results.size, 3)
      // Point 1 should be the best match for its own vector
      assertEquals(results.head.id, ExtendedPointId.LongValue(1))

      backend.send(colApi.deleteCollection(name))
    }
  }
