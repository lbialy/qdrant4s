package io.qdrant.client

import io.qdrant.client.model.*

class PointsApiSpec extends QdrantSuite:

  // Helper: create a collection with 3 test points, runs fn, then cleans up
  private def withTestPoints(container: Containers)(fn: String => Unit): Unit =
    val api = pointsApi(container)
    val colApi = collectionsApi(container)
    val name = uniqueCollectionName()

    backend.send(colApi.createCollection(
      name,
      createCollection = Some(CreateCollection(
        vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
      ))
    ))

    val points = Seq(
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
      PointStruct(
        id = ExtendedPointId.LongValue(3),
        vector = VectorStruct.SeqFloatValue(Seq(0.36f, 0.55f, 0.47f, 0.94f)),
        payload = Some(Map("city" -> io.circe.Json.fromString("Moscow")))
      )
    )

    val upsertResp = backend.send(api.upsertPoints(
      name,
      wait = Some(true),
      pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
    ))
    assert(upsertResp.body.isRight, s"upsert setup failed: ${upsertResp.body}")

    try fn(name)
    finally backend.send(colApi.deleteCollection(name))

  // ---------------------------------------------------------------------------
  // Upsert & Retrieve
  // ---------------------------------------------------------------------------

  test("upsertPoints - insert points with vectors and payload (list-based)") {
    withContainers { container =>
      val api = pointsApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val points = Seq(
        PointStruct(
          id = ExtendedPointId.LongValue(1),
          vector = VectorStruct.SeqFloatValue(Seq(0.05f, 0.61f, 0.76f, 0.74f)),
          payload = Some(Map("city" -> io.circe.Json.fromString("Berlin")))
        ),
        PointStruct(
          id = ExtendedPointId.LongValue(2),
          vector = VectorStruct.SeqFloatValue(Seq(0.19f, 0.81f, 0.75f, 0.11f)),
          payload = Some(Map("city" -> io.circe.Json.fromString("London")))
        )
      )

      val response = backend.send(api.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))
      val result = response.body
      assert(result.isRight, s"upsertPoints failed: $result")
      assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

      // verify count
      val countResp = backend.send(api.countPoints(name, countRequest = Some(CountRequest(exact = Some(true)))))
      assertEquals(countResp.body.toOption.get.result.get.count, 2)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("upsertPoints - batch upsert") {
    withContainers { container =>
      val api = pointsApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val batch = Batch(
        ids = Seq(ExtendedPointId.LongValue(1), ExtendedPointId.LongValue(2)),
        vectors = BatchVectorStruct.SeqSeqFloatValue(Seq(
          Seq(0.05f, 0.61f, 0.76f, 0.74f),
          Seq(0.19f, 0.81f, 0.75f, 0.11f)
        ))
      )

      val response = backend.send(api.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsBatchValue(PointsBatch(batch = batch)))
      ))
      val result = response.body
      assert(result.isRight, s"upsertPoints (batch) failed: $result")
      assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

      val countResp = backend.send(api.countPoints(name, countRequest = Some(CountRequest(exact = Some(true)))))
      assertEquals(countResp.body.toOption.get.result.get.count, 2)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("getPoint - retrieve single point by ID") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val result = response.body
        assert(result.isRight, s"getPoint failed: $result")
        val record = result.toOption.get.result.get
        assertEquals(record.id, ExtendedPointId.LongValue(1))
      }
    }
  }

  test("getPoints - retrieve multiple points by IDs") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.getPoints(
          name,
          pointRequest = Some(PointRequest(
            ids = Seq(ExtendedPointId.LongValue(1), ExtendedPointId.LongValue(3)),
            withPayload = Some(WithPayloadInterface.BooleanValue(true))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"getPoints failed: $result")
        val records = result.toOption.get.result.get
        assertEquals(records.size, 2)
      }
    }
  }

  test("scrollPoints - scroll through all points (empty collection)") {
    withContainers { container =>
      val api = pointsApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.scrollPoints(
        name,
        scrollRequest = Some(ScrollRequest())
      ))
      val result = response.body
      assert(result.isRight, s"scrollPoints failed: $result")
      val scroll = result.toOption.get.result.get
      assertEquals(scroll.points.size, 0)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("countPoints - count points (no filter, empty collection)") {
    withContainers { container =>
      val api = pointsApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.countPoints(
        name,
        countRequest = Some(CountRequest(exact = Some(true)))
      ))
      val result = response.body
      assert(result.isRight, s"countPoints failed: $result")
      assertEquals(result.toOption.get.result.get.count, 0)

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("countPoints - count points with filter") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.countPoints(
          name,
          countRequest = Some(CountRequest(
            exact = Some(true),
            filter = Some(Filter(
              must = Some(FilterMust.SeqConditionValue(Seq(
                Condition.FieldConditionValue(FieldCondition(
                  key = "city",
                  `match` = Some(ModelMatch.MatchValueValue(MatchValue(
                    value = ValueVariants.StringValue("Berlin")
                  )))
                ))
              )))
            ))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"countPoints with filter failed: $result")
        assertEquals(result.toOption.get.result.get.count, 1)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Payload Operations
  // ---------------------------------------------------------------------------

  test("setPayload - set payload fields") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.setPayload(
          name,
          wait = Some(true),
          setPayload = Some(SetPayload(
            payload = Map("color" -> io.circe.Json.fromString("red")),
            points = Some(Seq(ExtendedPointId.LongValue(1)))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"setPayload failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify payload was set
        val pointResp = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val payload = pointResp.body.toOption.get.result.get.payload.get
        assert(payload("color") == io.circe.Json.fromString("red"), s"expected red, got ${payload("color")}")
        // original payload should still be there
        assert(payload("city") == io.circe.Json.fromString("Berlin"), s"expected Berlin, got ${payload("city")}")
      }
    }
  }

  test("overwritePayload - overwrite payload entirely") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.overwritePayload(
          name,
          wait = Some(true),
          setPayload = Some(SetPayload(
            payload = Map("color" -> io.circe.Json.fromString("blue")),
            points = Some(Seq(ExtendedPointId.LongValue(1)))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"overwritePayload failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify payload was overwritten (city should be gone)
        val pointResp = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val payload = pointResp.body.toOption.get.result.get.payload.get
        assert(payload("color") == io.circe.Json.fromString("blue"), s"expected blue, got ${payload("color")}")
        assert(!payload.contains("city"), s"city should have been removed by overwrite, got $payload")
      }
    }
  }

  test("deletePayload - delete specific payload keys") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.deletePayload(
          name,
          wait = Some(true),
          deletePayload = Some(DeletePayload(
            keys = Seq("city"),
            points = Some(Seq(ExtendedPointId.LongValue(1)))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"deletePayload failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify city key was deleted
        val pointResp = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val payload = pointResp.body.toOption.get.result.get.payload
        assert(payload.isEmpty || !payload.get.contains("city"), s"city should be deleted, got $payload")
      }
    }
  }

  test("clearPayload - clear all payload from a point") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.clearPayload(
          name,
          wait = Some(true),
          pointsSelector = Some(PointsSelector.PointIdsListValue(
            PointIdsList(points = Seq(ExtendedPointId.LongValue(1)))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"clearPayload failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify payload is cleared
        val pointResp = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val payload = pointResp.body.toOption.get.result.get.payload
        assert(payload.isEmpty || payload.get.isEmpty, s"payload should be empty, got $payload")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Vector Operations
  // ---------------------------------------------------------------------------

  test("updateVectors - update vectors on existing points") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val newVector = Seq(0.99f, 0.01f, 0.02f, 0.03f)
        val response = backend.send(api.updateVectors(
          name,
          wait = Some(true),
          updateVectors = Some(UpdateVectors(
            points = Seq(PointVectors(
              id = ExtendedPointId.LongValue(1),
              vector = VectorStruct.SeqFloatValue(newVector)
            ))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"updateVectors failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)
      }
    }
  }

  test("deleteVectors - delete named vectors from points") {
    withContainers { container =>
      val api = pointsApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()

      // Create collection with named vectors
      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.MapStringVectorParamsValue(Map(
            "dense" -> VectorParams(size = 4, distance = Distance.Cosine)
          )))
        ))
      ))

      // Insert a point with named vector
      val points = Seq(PointStruct(
        id = ExtendedPointId.LongValue(1),
        vector = VectorStruct.MapStringVectorValue(Map(
          "dense" -> Vector.SeqFloatValue(Seq(0.1f, 0.2f, 0.3f, 0.4f))
        ))
      ))
      backend.send(api.upsertPoints(
        name,
        wait = Some(true),
        pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
      ))

      val response = backend.send(api.deleteVectors(
        name,
        wait = Some(true),
        deleteVectors = Some(DeleteVectors(
          points = Some(Seq(ExtendedPointId.LongValue(1))),
          vector = Set("dense")
        ))
      ))
      val result = response.body
      assert(result.isRight, s"deleteVectors failed: $result")
      assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

      backend.send(colApi.deleteCollection(name))
    }
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  test("deletePoints - delete by ID") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.deletePoints(
          name,
          wait = Some(true),
          pointsSelector = Some(PointsSelector.PointIdsListValue(
            PointIdsList(points = Seq(ExtendedPointId.LongValue(1)))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"deletePoints failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify count decreased
        val countResp = backend.send(api.countPoints(name, countRequest = Some(CountRequest(exact = Some(true)))))
        assertEquals(countResp.body.toOption.get.result.get.count, 2)
      }
    }
  }

  test("deletePoints - delete by filter") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.deletePoints(
          name,
          wait = Some(true),
          pointsSelector = Some(PointsSelector.FilterSelectorValue(
            FilterSelector(filter = Filter(
              must = Some(FilterMust.SeqConditionValue(Seq(
                Condition.FieldConditionValue(FieldCondition(
                  key = "city",
                  `match` = Some(ModelMatch.MatchValueValue(MatchValue(
                    value = ValueVariants.StringValue("Berlin")
                  )))
                ))
              )))
            ))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"deletePoints by filter failed: $result")
        assertEquals(result.toOption.get.result.get.status, UpdateStatus.completed)

        // verify only Berlin point was deleted (2 remaining)
        val countResp = backend.send(api.countPoints(name, countRequest = Some(CountRequest(exact = Some(true)))))
        assertEquals(countResp.body.toOption.get.result.get.count, 2)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Batch
  // ---------------------------------------------------------------------------

  test("batchUpdate - execute multiple operations in a single call") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.batchUpdate(
          name,
          wait = Some(true),
          updateOperations = Some(UpdateOperations(
            operations = Seq(
              // Set payload on point 1
              UpdateOperation.SetPayloadOperationValue(SetPayloadOperation(
                setPayload = SetPayload(
                  payload = Map("batch_key" -> io.circe.Json.fromString("batch_val")),
                  points = Some(Seq(ExtendedPointId.LongValue(1)))
                )
              )),
              // Delete point 3
              UpdateOperation.DeleteOperationValue(DeleteOperation(
                delete = PointsSelector.PointIdsListValue(
                  PointIdsList(points = Seq(ExtendedPointId.LongValue(3)))
                )
              ))
            )
          ))
        ))
        val result = response.body
        assert(result.isRight, s"batchUpdate failed: $result")

        // verify point 3 was deleted
        val countResp = backend.send(api.countPoints(name, countRequest = Some(CountRequest(exact = Some(true)))))
        assertEquals(countResp.body.toOption.get.result.get.count, 2)

        // verify payload was set on point 1
        val pointResp = backend.send(api.getPoint(name, ExtendedPointId.LongValue(1)))
        val payload = pointResp.body.toOption.get.result.get.payload.get
        assert(payload("batch_key") == io.circe.Json.fromString("batch_val"), s"expected batch_val, got ${payload("batch_key")}")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Facet
  // ---------------------------------------------------------------------------

  test("facet - facet counts on a keyword payload field") {
    withContainers { container =>
      val api = pointsApi(container)
      withTestPoints(container) { name =>
        val idxApi = indexesApi(container)
        backend.send(idxApi.createFieldIndex(
          name,
          wait = Some(true),
          createFieldIndex = Some(CreateFieldIndex(
            fieldName = "city",
            fieldSchema = Some(PayloadFieldSchema.PayloadSchemaTypeValue(PayloadSchemaType.keyword))
          ))
        ))

        val response = backend.send(api.facet(
          name,
          facetRequest = Some(FacetRequest(key = "city", exact = Some(true)))
        ))
        val result = response.body
        assert(result.isRight, s"facet failed: $result")
        val hits = result.toOption.get.result.get.hits
        assertEquals(hits.size, 3) // Berlin, London, Moscow
        assert(hits.forall(_.count == 1), s"each city should have count 1, got $hits")
      }
    }
  }
