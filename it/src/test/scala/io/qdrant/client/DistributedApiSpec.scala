package io.qdrant.client

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{Network => DockerNetwork}
import munit.FunSuite
import sttp.client4.*
import sttp.client4.okhttp.OkHttpSyncBackend
import io.qdrant.client.api.*
import io.qdrant.client.core.Authorization
import io.qdrant.client.model.*

class DistributedApiSpec extends FunSuite:

  val backend: SyncBackend = OkHttpSyncBackend()

  val network = DockerNetwork.newNetwork()

  // Node 1 - bootstrap node
  val node1 = GenericContainer(
    dockerImage = "qdrant/qdrant:v1.17.0",
    exposedPorts = Seq(6333),
    command = Seq("./qdrant", "--uri", "http://node1:6335"),
    env = Map(
      "QDRANT__CLUSTER__ENABLED" -> "true",
      "QDRANT__SERVICE__API_KEY" -> QdrantSuite.ApiKey,
    ),
    waitStrategy = Wait.forHttp("/readyz").forPort(6333).forStatusCode(200),
  )
  node1.underlyingUnsafeContainer.withNetwork(network).withNetworkAliases("node1")

  // Node 2 - joins the cluster via bootstrap
  val node2 = GenericContainer(
    dockerImage = "qdrant/qdrant:v1.17.0",
    exposedPorts = Seq(6333),
    command = Seq("./qdrant", "--bootstrap", "http://node1:6335", "--uri", "http://node2:6335"),
    env = Map(
      "QDRANT__CLUSTER__ENABLED" -> "true",
      "QDRANT__SERVICE__API_KEY" -> QdrantSuite.ApiKey,
    ),
    waitStrategy = Wait.forHttp("/readyz").forPort(6333).forStatusCode(200),
  )
  node2.underlyingUnsafeContainer.withNetwork(network).withNetworkAliases("node2")

  override def beforeAll(): Unit =
    super.beforeAll()
    node1.start()
    node2.start()

  override def afterAll(): Unit =
    node2.stop()
    node1.stop()
    network.close()
    super.afterAll()

  def baseUrl(container: GenericContainer): String =
    val host = container.host
    val port = container.mappedPort(6333)
    s"http://$host:$port"

  def distributedApi(c: GenericContainer): DistributedApi[Authorization.ApiKey] =
    DistributedApi.withApiKeyAuth(baseUrl(c), QdrantSuite.ApiKey)

  test("clusterStatus returns enabled for clustered setup") {
    val dApi = distributedApi(node1)
    val response = backend.send(dApi.clusterStatus)
    assert(response.body.isRight, s"clusterStatus failed: ${response.body}")
    val result = response.body.toOption.get.result.get
    result match {
      case ClusterStatus.ClusterStatusOneOf1Value(v) =>
        assertEquals(v.status, ClusterStatusOneOf1Enums.Status.enabled)
        assert(v.peers.size >= 2, s"Expected at least 2 peers, got ${v.peers.size}")
      case other =>
        fail(s"Expected ClusterStatusOneOf1Value(enabled) but got: $other")
    }
  }

  test("clusterTelemetry returns telemetry data") {
    val dApi = distributedApi(node1)
    val response = backend.send(dApi.clusterTelemetry())
    assert(response.body.isRight, s"clusterTelemetry failed: ${response.body}")
  }
