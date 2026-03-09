package io.qdrant.client

import sttp.client4.*

class ServiceApiSpec extends QdrantSuite:

  // healthz, readyz, livez, and metrics return plain text, not JSON.
  // The generated client uses asJson[String] which expects a JSON-quoted string.
  // This is a known generator limitation. We test these via raw HTTP to verify
  // the container is reachable, then test the JSON-based endpoints properly.

  test("healthz endpoint is reachable") {
    withContainers { container =>
      val response = backend.send(basicRequest.get(uri"${baseUrl(container)}/healthz"))
      assertEquals(response.code.code, 200)
      assert(response.body.isRight)
    }
  }

  test("readyz endpoint is reachable") {
    withContainers { container =>
      val response = backend.send(basicRequest.get(uri"${baseUrl(container)}/readyz"))
      assertEquals(response.code.code, 200)
      assert(response.body.isRight)
    }
  }

  test("livez endpoint is reachable") {
    withContainers { container =>
      val response = backend.send(basicRequest.get(uri"${baseUrl(container)}/livez"))
      assertEquals(response.code.code, 200)
      assert(response.body.isRight)
    }
  }

  test("root returns version info") {
    withContainers { container =>
      val api = serviceApi(container)
      val response = backend.send(api.root)
      val result = response.body
      assert(result.isRight, s"root failed: $result")
      val versionInfo = result.toOption.get
      assert(versionInfo.version.nonEmpty, "version should be present")
    }
  }

  test("telemetry returns data") {
    withContainers { container =>
      val api = serviceApi(container)
      val response = backend.send(api.telemetry())
      val result = response.body
      assert(result.isRight, s"telemetry failed: $result")
    }
  }

  test("metrics endpoint is reachable") {
    withContainers { container =>
      val response = backend.send(
        basicRequest
          .get(uri"${baseUrl(container)}/metrics")
          .header("api-key", QdrantSuite.ApiKey)
      )
      assertEquals(response.code.code, 200)
      val body = response.body.toOption.get
      assert(body.contains("app_info"), "metrics should contain app_info")
    }
  }
