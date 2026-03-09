api_tag := trim(`cat api-version`)
api_docs_version := replace_regex(api_tag, '\.[0-9]+$', '.x')
generator_jar := "vendor/oapigen/modules/openapi-generator-cli/target/openapi-generator-cli.jar"

default:
    @just --list

select-version tag:
    cd qdrant && git fetch --tags && git checkout {{tag}}
    @echo {{tag}} > api-version
    @echo "Switched qdrant submodule to {{tag}}"

build-generator:
    cd vendor/oapigen && mvn package -pl modules/openapi-generator-cli -am -DskipTests -q
    @echo "Generator built at {{generator_jar}}"

regenerate:
    @test -f {{generator_jar}} || (echo "Generator jar not found. Run 'just build-generator' first." && exit 1)
    java -jar {{generator_jar}} generate \
        -i qdrant/docs/redoc/{{api_docs_version}}/openapi.json \
        -g scala-sttp4-jsoniter \
        -o generated \
        --additional-properties=mainPackage=io.qdrant.client
