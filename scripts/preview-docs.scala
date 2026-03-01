//> using scala "3.8.2"
//> using dep "com.lihaoyi::cask:0.11.3"
//> using dep "com.lihaoyi::os-lib:0.11.8"
//> using mainClass "DocServer"

/** Generates both doc sites and serves them locally for preview.
  *
  * Usage (from project root):
  *   scala-cli run scripts/preview-docs.scala
  *   scala-cli run scripts/preview-docs.scala -- --port 9000
  */

object DocServer extends cask.MainRoutes:
  private var docsDir: String = ""
  private var portNum: Int    = 8080

  override def port      = portNum
  override def host      = "localhost"
  override def debugMode = false

  @cask.staticFiles("/")
  def staticFiles() = docsDir

  initialize()

  override def main(args: Array[String]): Unit =
    portNum = args.zipWithIndex
      .find(_._1 == "--port")
      .flatMap { case (_, i) => args.lift(i + 1).flatMap(_.toIntOption) }
      .getOrElse(8080)

    val projectRoot = os.pwd

    println("Generating core documentation...")
    os.proc("mill", "duck4s[3.8.2].docJar")
      .call(cwd = projectRoot, stdout = os.Inherit, stderr = os.Inherit)

    println("Generating cats-effect documentation...")
    os.proc("mill", "duck4s-cats-effect[3.8.2].docJar")
      .call(cwd = projectRoot, stdout = os.Inherit, stderr = os.Inherit)

    val coreDocsDir = projectRoot / "out" / "duck4s" / "3.8.2" / "docJar.dest" / "docs"
    val catsDocsDir =
      projectRoot / "out" / "duck4s-cats-effect" / "3.8.2" / "docJar.dest" / "docs"

    val previewDir = os.temp.dir(prefix = "duck4s-preview")
    println(s"Merging docs into $previewDir...")

    // Copy core docs into preview root
    os.list(coreDocsDir).foreach(p =>
      os.copy(p, previewDir / p.last, replaceExisting = true, mergeFolders = true)
    )

    // Copy cats-effect docs into cats-effect-api/ subdirectory
    val catsEffectOut = previewDir / "cats-effect-api"
    os.makeDir.all(catsEffectOut)
    os.list(catsDocsDir).foreach(p =>
      os.copy(p, catsEffectOut / p.last, replaceExisting = true, mergeFolders = true)
    )

    docsDir = previewDir.toString

    println(s"Docs ready. Open http://localhost:$portNum/index.html in your browser.")
    println("Press Ctrl+C to stop.\n")

    super.main(args)
