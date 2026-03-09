package io.qdrant.client

import io.qdrant.client.model.*

import java.io.File

class SnapshotsApiSpec extends QdrantSuite:

  // Helper: create a collection, run fn, then clean up
  private def withCollection(container: Containers)(fn: String => Unit): Unit =
    val colApi = collectionsApi(container)
    val name = uniqueCollectionName()

    val createResp = backend.send(colApi.createCollection(
      name,
      createCollection = Some(CreateCollection(
        vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
      ))
    ))
    assert(createResp.body.isRight, s"collection creation failed: ${createResp.body}")

    try fn(name)
    finally backend.send(colApi.deleteCollection(name))

  // ---------------------------------------------------------------------------
  // Collection Snapshots
  // ---------------------------------------------------------------------------

  test("createSnapshot - create collection snapshot") {
    withContainers { container =>
      val api = snapshotsApi(container)
      withCollection(container) { name =>
        val response = backend.send(api.createSnapshot(name, wait = Some(true)))
        val result = response.body
        assert(result.isRight, s"createSnapshot failed: $result")
        val snapshot = result.toOption.get.result.get
        assert(snapshot.name.nonEmpty, "snapshot name should not be empty")
        assert(snapshot.size > 0, s"snapshot size should be positive, got ${snapshot.size}")
      }
    }
  }

  test("listSnapshots - list snapshots for a collection") {
    withContainers { container =>
      val api = snapshotsApi(container)
      withCollection(container) { name =>
        // Create a snapshot first
        val createResp = backend.send(api.createSnapshot(name, wait = Some(true)))
        assert(createResp.body.isRight, s"createSnapshot setup failed: ${createResp.body}")

        val response = backend.send(api.listSnapshots(name))
        val result = response.body
        assert(result.isRight, s"listSnapshots failed: $result")
        val snapshots = result.toOption.get.result.get
        assert(snapshots.nonEmpty, "should have at least one snapshot")
        assert(snapshots.head.name.nonEmpty, "snapshot name should not be empty")
      }
    }
  }

  test("getSnapshot - download snapshot file") {
    withContainers { container =>
      val api = snapshotsApi(container)
      withCollection(container) { name =>
        // Create a snapshot first
        val createResp = backend.send(api.createSnapshot(name, wait = Some(true)))
        assert(createResp.body.isRight, s"createSnapshot setup failed: ${createResp.body}")
        val snapshotName = createResp.body.toOption.get.result.get.name

        val response = backend.send(api.getSnapshot(name, snapshotName))
        val result = response.body
        assert(result.isRight, s"getSnapshot failed: $result")
        val file = result.toOption.get
        assert(file.exists(), "downloaded snapshot file should exist")
        assert(file.length() > 0, "downloaded snapshot file should not be empty")
        // Clean up temp file
        file.delete()
      }
    }
  }

  test("deleteSnapshot - delete a snapshot") {
    withContainers { container =>
      val api = snapshotsApi(container)
      withCollection(container) { name =>
        // Create a snapshot first
        val createResp = backend.send(api.createSnapshot(name, wait = Some(true)))
        assert(createResp.body.isRight, s"createSnapshot setup failed: ${createResp.body}")
        val snapshotName = createResp.body.toOption.get.result.get.name

        val response = backend.send(api.deleteSnapshot(name, snapshotName, wait = Some(true)))
        val result = response.body
        assert(result.isRight, s"deleteSnapshot failed: $result")
        assert(result.toOption.get.result.get == true, "deleteSnapshot should return true")

        // Verify snapshot is gone
        val listResp = backend.send(api.listSnapshots(name))
        val snapshots = listResp.body.toOption.get.result.get
        assert(!snapshots.exists(_.name == snapshotName), "deleted snapshot should not appear in list")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Full (storage-level) Snapshots
  // ---------------------------------------------------------------------------

  test("createFullSnapshot - full storage snapshot") {
    withContainers { container =>
      val api = snapshotsApi(container)
      val response = backend.send(api.createFullSnapshot(wait = Some(true)))
      val result = response.body
      assert(result.isRight, s"createFullSnapshot failed: $result")
      val snapshot = result.toOption.get.result.get
      assert(snapshot.name.nonEmpty, "full snapshot name should not be empty")
      assert(snapshot.size > 0, s"full snapshot size should be positive, got ${snapshot.size}")
    }
  }

  test("listFullSnapshots - list full snapshots") {
    withContainers { container =>
      val api = snapshotsApi(container)
      // Create a full snapshot first
      val createResp = backend.send(api.createFullSnapshot(wait = Some(true)))
      assert(createResp.body.isRight, s"createFullSnapshot setup failed: ${createResp.body}")

      val response = backend.send(api.listFullSnapshots)
      val result = response.body
      assert(result.isRight, s"listFullSnapshots failed: $result")
      val snapshots = result.toOption.get.result.get
      assert(snapshots.nonEmpty, "should have at least one full snapshot")
    }
  }

  test("deleteFullSnapshot - delete full snapshot") {
    withContainers { container =>
      val api = snapshotsApi(container)
      // Create a full snapshot first
      val createResp = backend.send(api.createFullSnapshot(wait = Some(true)))
      assert(createResp.body.isRight, s"createFullSnapshot setup failed: ${createResp.body}")
      val snapshotName = createResp.body.toOption.get.result.get.name

      val response = backend.send(api.deleteFullSnapshot(snapshotName, wait = Some(true)))
      val result = response.body
      assert(result.isRight, s"deleteFullSnapshot failed: $result")
      assert(result.toOption.get.result.get == true, "deleteFullSnapshot should return true")

      // Verify snapshot is gone
      val listResp = backend.send(api.listFullSnapshots)
      val snapshots = listResp.body.toOption.get.result.get
      assert(!snapshots.exists(_.name == snapshotName), "deleted full snapshot should not appear in list")
    }
  }
