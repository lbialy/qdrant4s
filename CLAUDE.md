# qdrant4s

Scala client for [Qdrant](https://github.com/qdrant/qdrant) vector search engine, generated from the OpenAPI spec.

## Project structure

- `qdrant/` - Git submodule pointing to https://github.com/qdrant/qdrant at tag `v1.17.0`
- `generated/` - Auto-generated client code. **DO NOT modify generated code under any circumstances.**

## How the client was generated

1. Added qdrant as a git submodule:
   ```
   git submodule add https://github.com/qdrant/qdrant.git qdrant
   cd qdrant && git checkout v1.17.0
   ```

2. Used the pre-rendered OpenAPI spec shipped with qdrant at `qdrant/docs/redoc/v1.17.x/openapi.json`
   (no need to run `tools/generate_openapi_models.sh` which requires Rust cargo + Docker + ytt).

3. Generated the client with openapi-generator-cli:
   ```
   openapi-generator-cli generate \
     -i qdrant/docs/redoc/v1.17.x/openapi.json \
     -g scala-sttp4-jsoniter \
     -o generated \
     --additional-properties=mainPackage=io.qdrant.client
   ```

## Notes on generator options

- `separateErrorChannel` must stay `true` (the default). Setting it to `false` breaks binary
  file download endpoints (snapshot downloads) because the `fnHandleDownload` lambda that
  correctly maps `asJson[File]` → `asFile(...)` is only applied in the `separateErrorChannel=true`
  template path. This is a bug in the openapi-generator template.
