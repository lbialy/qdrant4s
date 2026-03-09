package io.qdrant.client

import io.qdrant.client.model.*

class AliasesApiSpec extends QdrantSuite:

  test("updateAliases creates an alias for a collection") {
    withContainers { container =>
      val api = aliasesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()
      val aliasName = s"alias_${name}"

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      val response = backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.CreateAliasOperationValue(
            CreateAliasOperation(createAlias = CreateAlias(collectionName = name, aliasName = aliasName))
          ))
        ))
      ))
      val result = response.body
      assert(result.isRight, s"updateAliases failed: $result")
      assertEquals(result.toOption.get.result, Some(true))

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("getCollectionAliases returns aliases for a collection") {
    withContainers { container =>
      val api = aliasesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()
      val aliasName = s"alias_${name}"

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.CreateAliasOperationValue(
            CreateAliasOperation(createAlias = CreateAlias(collectionName = name, aliasName = aliasName))
          ))
        ))
      ))

      val response = backend.send(api.getCollectionAliases(name))
      val result = response.body
      assert(result.isRight, s"getCollectionAliases failed: $result")
      val aliases = result.toOption.get.result.get.aliases
      assert(aliases.exists(_.aliasName == aliasName), s"alias $aliasName not found in $aliases")

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("getCollectionsAliases returns alias in global list") {
    withContainers { container =>
      val api = aliasesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()
      val aliasName = s"alias_${name}"

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.CreateAliasOperationValue(
            CreateAliasOperation(createAlias = CreateAlias(collectionName = name, aliasName = aliasName))
          ))
        ))
      ))

      val response = backend.send(api.getCollectionsAliases)
      val result = response.body
      assert(result.isRight, s"getCollectionsAliases failed: $result")
      val aliases = result.toOption.get.result.get.aliases
      assert(aliases.exists(_.aliasName == aliasName), s"alias $aliasName not found in $aliases")

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("updateAliases renames an alias") {
    withContainers { container =>
      val api = aliasesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()
      val aliasName = s"alias_${name}"
      val newAliasName = s"renamed_${name}"

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      // Create alias
      backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.CreateAliasOperationValue(
            CreateAliasOperation(createAlias = CreateAlias(collectionName = name, aliasName = aliasName))
          ))
        ))
      ))

      // Rename alias
      val response = backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.RenameAliasOperationValue(
            RenameAliasOperation(renameAlias = RenameAlias(oldAliasName = aliasName, newAliasName = newAliasName))
          ))
        ))
      ))
      val result = response.body
      assert(result.isRight, s"updateAliases (rename) failed: $result")

      // Verify renamed alias exists
      val aliasesResponse = backend.send(api.getCollectionAliases(name))
      val aliases = aliasesResponse.body.toOption.get.result.get.aliases
      assert(aliases.exists(_.aliasName == newAliasName), s"renamed alias $newAliasName not found in $aliases")
      assert(!aliases.exists(_.aliasName == aliasName), s"old alias $aliasName still present in $aliases")

      backend.send(colApi.deleteCollection(name))
    }
  }

  test("updateAliases deletes an alias") {
    withContainers { container =>
      val api = aliasesApi(container)
      val colApi = collectionsApi(container)
      val name = uniqueCollectionName()
      val aliasName = s"alias_${name}"

      backend.send(colApi.createCollection(
        name,
        createCollection = Some(CreateCollection(
          vectors = Some(VectorsConfig.VectorParamsValue(VectorParams(size = 4, distance = Distance.Cosine)))
        ))
      ))

      // Create alias
      backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.CreateAliasOperationValue(
            CreateAliasOperation(createAlias = CreateAlias(collectionName = name, aliasName = aliasName))
          ))
        ))
      ))

      // Delete alias
      val response = backend.send(api.updateAliases(
        changeAliasesOperation = Some(ChangeAliasesOperation(
          actions = Seq(AliasOperations.DeleteAliasOperationValue(
            DeleteAliasOperation(deleteAlias = DeleteAlias(aliasName = aliasName))
          ))
        ))
      ))
      val result = response.body
      assert(result.isRight, s"updateAliases (delete) failed: $result")

      // Verify alias is gone
      val aliasesResponse = backend.send(api.getCollectionAliases(name))
      val aliases = aliasesResponse.body.toOption.get.result.get.aliases
      assert(!aliases.exists(_.aliasName == aliasName), s"alias $aliasName still present after delete in $aliases")

      backend.send(colApi.deleteCollection(name))
    }
  }
