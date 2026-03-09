package io.qdrant.client

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.munit.TestContainersForAll
import org.testcontainers.containers.wait.strategy.Wait
import munit.FunSuite
import sttp.client4.*
import sttp.client4.okhttp.OkHttpSyncBackend
import io.qdrant.client.api.*
import io.qdrant.client.core.Authorization

import java.util.UUID

trait QdrantSuite extends FunSuite with TestContainersForAll:

  type Containers = GenericContainer

  override def startContainers(): GenericContainer =
    val container = GenericContainer(
      dockerImage = "qdrant/qdrant:v1.17.0",
      exposedPorts = Seq(6333),
      waitStrategy = Wait.forHttp("/readyz").forPort(6333).forStatusCode(200),
      env = Map(
        "QDRANT__SERVICE__API_KEY" -> QdrantSuite.ApiKey,
      ),
    )
    container.start()
    container

  val backend: SyncBackend = OkHttpSyncBackend()

  def baseUrl(container: GenericContainer): String =
    val host = container.host
    val port = container.mappedPort(6333)
    s"http://$host:$port"

  def serviceApi(c: GenericContainer): ServiceApi[Authorization.ApiKey] =
    ServiceApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def collectionsApi(c: GenericContainer): CollectionsApi[Authorization.ApiKey] =
    CollectionsApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def pointsApi(c: GenericContainer): PointsApi[Authorization.ApiKey] =
    PointsApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def searchApi(c: GenericContainer): SearchApi[Authorization.ApiKey] =
    SearchApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def aliasesApi(c: GenericContainer): AliasesApi[Authorization.ApiKey] =
    AliasesApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def indexesApi(c: GenericContainer): IndexesApi[Authorization.ApiKey] =
    IndexesApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def snapshotsApi(c: GenericContainer): SnapshotsApi[Authorization.ApiKey] =
    SnapshotsApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def distributedApi(c: GenericContainer): DistributedApi[Authorization.ApiKey] =
    DistributedApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  def uniqueCollectionName(): String =
    s"test_${UUID.randomUUID().toString.replace("-", "").take(12)}"

end QdrantSuite

object QdrantSuite:
  val ApiKey = "test-api-key-for-integration-tests"
