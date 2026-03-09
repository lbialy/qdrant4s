//> using scala 3.3.7
//> using jvm temurin:11
//> using dep "com.kubuszok::scala-cli-md-spec:0.2.0"

import com.kubuszok.scalaclimdspec.*
import scala.collection.immutable.ListMap

val qdrant4sVersion = sys.env.getOrElse("QDRANT4S_VERSION", "0.1.0-SNAPSHOT")

def replaceVersion(content: Snippet.Content): Snippet.Content = content match
  case Snippet.Content.Single(text) =>
    Snippet.Content.Single(text.replace("$VERSION$", qdrant4sVersion))
  case Snippet.Content.Multiple(files) =>
    Snippet.Content.Multiple(files.map { case (name, single) =>
      name -> replaceVersion(single).asInstanceOf[Snippet.Content.Single]
    })

@main def run(args: String*): Unit = testSnippets(args.toArray) { cfg =>
  new Runner.Default(cfg) {
    extension (snippet: Snippet)
      override def adjusted: Snippet =
        Snippet(snippet.locations, replaceVersion(snippet.content))
  }
}
