package io.qdrant.client

import io.qdrant.client.model.*

class SearchApiSpec extends QdrantSuite:

  // Helper: create a collection with 5 test points with city payload, runs fn, then cleans up
  private def withTestPoints(container: Containers)(fn: String => Unit): Unit =
    val pApi = pointsApi(container)
    val colApi = collectionsApi(container)
    val name = uniqueCollectionName()

    backend.send(colApi.createCollection(
      name,
      createCollection = Some(CreateCollection(
        vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
      ))
    ))

    // Index on "city" so we can group by it
    val idxApi = indexesApi(container)
    backend.send(idxApi.createFieldIndex(
      name,
      wait = Some(true),
      createFieldIndex = Some(CreateFieldIndex(
        fieldName = "city",
        fieldSchema = Some(PayloadFieldSchema.PayloadSchemaTypeValue(PayloadSchemaType.keyword))
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
      ),
      PointStruct(
        id = ExtendedPointId.LongValue(4),
        vector = VectorStruct.SeqFloatValue(Seq(0.18f, 0.01f, 0.85f, 0.80f)),
        payload = Some(Map("city" -> io.circe.Json.fromString("London")))
      ),
      PointStruct(
        id = ExtendedPointId.LongValue(5),
        vector = VectorStruct.SeqFloatValue(Seq(0.24f, 0.18f, 0.22f, 0.44f)),
        payload = Some(Map("city" -> io.circe.Json.fromString("Berlin")))
      )
    )

    val upsertResp = backend.send(pApi.upsertPoints(
      name,
      wait = Some(true),
      pointInsertOperations = Some(PointInsertOperations.PointsListValue(PointsList(points = points)))
    ))
    assert(upsertResp.body.isRight, s"upsert setup failed: ${upsertResp.body}")

    try fn(name)
    finally backend.send(colApi.deleteCollection(name))

  // ---------------------------------------------------------------------------
  // Basic Search
  // ---------------------------------------------------------------------------

  test("searchPoints - nearest neighbor search with a query vector") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchPoints(
          name,
          searchRequest = Some(SearchRequest(
            vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
            limit = 3
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchPoints failed: $result")
        val points = result.toOption.get.result.get
        assertEquals(points.size, 3)
        // scores should be in descending order
        assert(points(0).score >= points(1).score && points(1).score >= points(2).score,
          s"scores not in descending order: ${points.map(_.score)}")
      }
    }
  }

  test("searchPoints - search with payload filter") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchPoints(
          name,
          searchRequest = Some(SearchRequest(
            vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
            limit = 10,
            filter = Some(Filter(
              must = Some(FilterMust.SeqConditionValue(Seq(
                Condition.FieldConditionValue(FieldCondition(
                  key = "city",
                  `match` = Some(ModelMatch.MatchValueValue(MatchValue(
                    value = ValueVariants.StringValue("London")
                  )))
                ))
              )))
            ))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchPoints with filter failed: $result")
        val points = result.toOption.get.result.get
        // Only London points (ids 2 and 4)
        assertEquals(points.size, 2)
      }
    }
  }

  test("searchPoints - search with with_payload and with_vector options") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchPoints(
          name,
          searchRequest = Some(SearchRequest(
            vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
            limit = 1,
            withPayload = Some(WithPayloadInterface.BooleanValue(true)),
            withVector = Some(WithVector.BooleanValue(true))
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchPoints with payload/vector failed: $result")
        val points = result.toOption.get.result.get
        assertEquals(points.size, 1)
        assert(points.head.payload.isDefined, "payload should be present")
        assert(points.head.vector.isDefined, "vector should be present")
      }
    }
  }

  test("searchBatchPoints - batch search (multiple queries at once)") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchBatchPoints(
          name,
          searchRequestBatch = Some(SearchRequestBatch(
            searches = Seq(
              SearchRequest(
                vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
                limit = 2
              ),
              SearchRequest(
                vector = NamedVectorStruct.SeqFloatValue(Seq(0.05f, 0.61f, 0.76f, 0.74f)),
                limit = 3
              )
            )
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchBatchPoints failed: $result")
        val batches = result.toOption.get.result.get
        assertEquals(batches.size, 2)
        assertEquals(batches(0).size, 2)
        assertEquals(batches(1).size, 3)
      }
    }
  }

  test("searchPointGroups - grouped search by payload field") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchPointGroups(
          name,
          searchGroupsRequest = Some(SearchGroupsRequest(
            vector = NamedVectorStruct.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f)),
            groupBy = "city",
            groupSize = 1,
            limit = 3
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchPointGroups failed: $result")
        val groups = result.toOption.get.result.get.groups
        // 3 distinct cities: Berlin, London, Moscow
        assertEquals(groups.size, 3)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Query API (universal)
  // ---------------------------------------------------------------------------

  test("queryPoints - nearest neighbor query") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.queryPoints(
          name,
          queryRequest = Some(QueryRequest(
            query = Some(QueryInterface.VectorInputValue(
              VectorInput.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f))
            )),
            limit = Some(3)
          ))
        ))
        val result = response.body
        assert(result.isRight, s"queryPoints failed: $result")
        val points = result.toOption.get.result.get.points
        assertEquals(points.size, 3)
      }
    }
  }

  test("queryPoints - query with filter") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.queryPoints(
          name,
          queryRequest = Some(QueryRequest(
            query = Some(QueryInterface.VectorInputValue(
              VectorInput.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f))
            )),
            limit = Some(10),
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
        assert(result.isRight, s"queryPoints with filter failed: $result")
        val points = result.toOption.get.result.get.points
        // Only Berlin points (ids 1 and 5)
        assertEquals(points.size, 2)
      }
    }
  }

  test("queryBatchPoints - batch query") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.queryBatchPoints(
          name,
          queryRequestBatch = Some(QueryRequestBatch(
            searches = Seq(
              QueryRequest(
                query = Some(QueryInterface.VectorInputValue(
                  VectorInput.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f))
                )),
                limit = Some(2)
              ),
              QueryRequest(
                query = Some(QueryInterface.VectorInputValue(
                  VectorInput.SeqFloatValue(Seq(0.05f, 0.61f, 0.76f, 0.74f))
                )),
                limit = Some(3)
              )
            )
          ))
        ))
        val result = response.body
        assert(result.isRight, s"queryBatchPoints failed: $result")
        val batches = result.toOption.get.result.get
        assertEquals(batches.size, 2)
        assertEquals(batches(0).points.size, 2)
        assertEquals(batches(1).points.size, 3)
      }
    }
  }

  test("queryPointsGroups - grouped query") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.queryPointsGroups(
          name,
          queryGroupsRequest = Some(QueryGroupsRequest(
            query = Some(QueryInterface.VectorInputValue(
              VectorInput.SeqFloatValue(Seq(0.2f, 0.1f, 0.9f, 0.7f))
            )),
            groupBy = "city",
            groupSize = Some(1),
            limit = Some(3)
          ))
        ))
        val result = response.body
        assert(result.isRight, s"queryPointsGroups failed: $result")
        val groups = result.toOption.get.result.get.groups
        assertEquals(groups.size, 3)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Recommendations
  // ---------------------------------------------------------------------------

  test("recommendPoints - recommend based on positive/negative point IDs") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.recommendPoints(
          name,
          recommendRequest = Some(RecommendRequest(
            positive = Some(Seq(
              RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))
            )),
            negative = Some(Seq(
              RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(3))
            )),
            limit = 3
          ))
        ))
        val result = response.body
        assert(result.isRight, s"recommendPoints failed: $result")
        val points = result.toOption.get.result.get
        assert(points.nonEmpty, "should return at least one recommendation")
        assert(points.size <= 3, s"should return at most 3 points, got ${points.size}")
      }
    }
  }

  test("recommendBatchPoints - batch recommendations") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.recommendBatchPoints(
          name,
          recommendRequestBatch = Some(RecommendRequestBatch(
            searches = Seq(
              RecommendRequest(
                positive = Some(Seq(
                  RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))
                )),
                limit = 2
              ),
              RecommendRequest(
                positive = Some(Seq(
                  RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(3))
                )),
                limit = 2
              )
            )
          ))
        ))
        val result = response.body
        assert(result.isRight, s"recommendBatchPoints failed: $result")
        val batches = result.toOption.get.result.get
        assertEquals(batches.size, 2)
      }
    }
  }

  test("recommendPointGroups - grouped recommendations") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.recommendPointGroups(
          name,
          recommendGroupsRequest = Some(RecommendGroupsRequest(
            positive = Some(Seq(
              RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))
            )),
            groupBy = "city",
            groupSize = 1,
            limit = 3
          ))
        ))
        val result = response.body
        assert(result.isRight, s"recommendPointGroups failed: $result")
        val groups = result.toOption.get.result.get.groups
        assert(groups.nonEmpty, "should return at least one group")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Discovery
  // ---------------------------------------------------------------------------

  test("discoverPoints - discovery search with context pairs") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.discoverPoints(
          name,
          discoverRequest = Some(DiscoverRequest(
            target = Some(RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))),
            context = Some(Seq(
              ContextExamplePair(
                positive = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(2)),
                negative = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(3))
              )
            )),
            limit = 3
          ))
        ))
        val result = response.body
        assert(result.isRight, s"discoverPoints failed: $result")
        val points = result.toOption.get.result.get
        assert(points.nonEmpty, "should return at least one discovery result")
      }
    }
  }

  test("discoverBatchPoints - batch discovery") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.discoverBatchPoints(
          name,
          discoverRequestBatch = Some(DiscoverRequestBatch(
            searches = Seq(
              DiscoverRequest(
                target = Some(RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))),
                context = Some(Seq(
                  ContextExamplePair(
                    positive = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(2)),
                    negative = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(3))
                  )
                )),
                limit = 2
              ),
              DiscoverRequest(
                target = Some(RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(3))),
                context = Some(Seq(
                  ContextExamplePair(
                    positive = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(4)),
                    negative = RecommendExample.ExtendedPointIdValue(ExtendedPointId.LongValue(1))
                  )
                )),
                limit = 2
              )
            )
          ))
        ))
        val result = response.body
        assert(result.isRight, s"discoverBatchPoints failed: $result")
        val batches = result.toOption.get.result.get
        assertEquals(batches.size, 2)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Matrix Search
  // ---------------------------------------------------------------------------

  test("searchMatrixPairs - pairwise similarity matrix") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchMatrixPairs(
          name,
          searchMatrixRequest = Some(SearchMatrixRequest(
            sample = Some(3),
            limit = Some(3)
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchMatrixPairs failed: $result")
        val matrix = result.toOption.get.result.get
        assert(matrix.pairs.nonEmpty, "should return at least one pair")
      }
    }
  }

  test("searchMatrixOffsets - offset-based similarity matrix") {
    withContainers { container =>
      val api = searchApi(container)
      withTestPoints(container) { name =>
        val response = backend.send(api.searchMatrixOffsets(
          name,
          searchMatrixRequest = Some(SearchMatrixRequest(
            sample = Some(3),
            limit = Some(3)
          ))
        ))
        val result = response.body
        assert(result.isRight, s"searchMatrixOffsets failed: $result")
        val matrix = result.toOption.get.result.get
        assert(matrix.ids.nonEmpty, "should return at least one id")
      }
    }
  }
