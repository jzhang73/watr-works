package edu.umass.cs.iesl.watr
package extract

import ammonite.{ops => fs}
import edu.umass.cs.iesl.watr.extract.fonts.SplineFont.Dir
import fs._
import java.io.InputStream
import java.net.URI
import textboxing.{TextBoxing => TB}
import spindex._
import EnrichGeometricFigures._
import utils.EnrichNumerics._
import java.io.{File => JFile}
import predsynth._
import scala.util.{Try, Failure, Success}
import segment.DocumentSegmenter

// TODO: 'overwrite' should be changed to 'preserve-annots'|'overwrite'

case class AppConfig(
  runRoot: Option[JFile] = None,
  corpusRoot: Option[JFile] = None,
  inputFileList: Option[JFile] = None,
  inputEntryDescriptor: Option[String] = None,
  action: Option[String] = None,
  dbPath: Option[JFile] = None,
  preserveAnnotations: Boolean = false,
  priorPredsynthFile: Option[JFile] = None,
  priorDocsegFile: Option[JFile] = None,
  textAlignPredsynth: Boolean = false,
  force: Boolean = false,
  extractFonts: Boolean = false,
  numToRun: Int = 0,
  numToSkip: Int = 0,
  exec: Option[(AppConfig) => Unit] = None
)

object Works extends App {

  // utils.VisualTracer.visualTraceLevel = utils.VisualTraceLevel.Off
  // utils.VisualTracer.visualTraceLevel = utils.VisualTraceLevel.Print
  // utils.VisualTracer.clearFilters()
  // utils.VisualTracer.addFilter("GutterDetection")
  // utils.VisualTracer.addFilter("LabelAbstract")

  def die(t: Throwable): Unit = {
    val message = s"""error: ${t}: ${t.getCause}: ${t.getMessage} """
    println(s"ERROR: ${message}")
    t.printStackTrace()
  }

  def corpusRootOrDie(ac: AppConfig): Path = ac.corpusRoot
    .map({croot =>

      val fullPath = if (croot.isAbsolute()) Path(croot) else pwd/RelPath(croot)

      val corpusSentinel =  fullPath / ".corpus-root"
      val validPath = exists(fullPath)
      val validSentinel = exists(fullPath/".corpus-root")

      if (!validPath) {
        sys.error(s"invalid corpus root specified ${fullPath}")
      } else if (!validSentinel) {
        sys.error(s"no .corpus-root sentinal file found in ${fullPath};\n run bin/works init (or create if manually you know what you're doing)")
      } else {
        fullPath
      }

    }).getOrElse(sys.error("no corpus root specified"))


  def setAction(conf: AppConfig, action: (AppConfig) => Unit): AppConfig = {
    conf.copy(exec=Option(action))
  }

  val parser = new scopt.OptionParser[AppConfig]("works") {
    head("Watr Works command line app", "0.1")

    note("Run text extraction and analysis on PDFs")

    help("usage")

    opt[JFile]('c', "corpus") action { (v, conf) =>
      conf.copy(corpusRoot = Option(v))
    } text ("root path of the corpus")

    opt[Int]('n', "number") action { (v, conf) =>
      conf.copy(numToRun = v) } text("process n corpus entries")

    opt[Int]('k', "skip") action { (v, conf) =>
      conf.copy(numToSkip = v) } text("skip first k entries")

    opt[Unit]('x', "overwrite") action { (v, conf) =>
      conf.copy(force = true) } text("force overwrite of existing files")

    opt[Unit]("extract-fonts") action { (v, conf) =>
      conf.copy(extractFonts = true) } text("try to extract font defs from pdf")

    opt[JFile]('i', "inputs") action { (v, conf) =>
      conf.copy(inputFileList = Option(v))
    } text("process files listed in specified file. Specify '--' to read from stdin")

    opt[JFile]('d', "db") action { (v, conf) =>
      conf.copy(dbPath = Option(v))
    } text("h2 database path")

    opt[Unit]("text-align") action { (v, conf) =>
      conf.copy(textAlignPredsynth = true)
    } text("use text heuristics to align predsynth db to watrworks output")

    opt[JFile]("merge-predsynth") action { (v, conf) =>
      conf.copy(priorPredsynthFile = Option(v))
    } text("location of predsynth db export (as json)")

    opt[JFile]("merge-docseg") action { (v, conf) =>
      conf.copy(priorDocsegFile = Option(v))
    } text("location of prior docseg: annotations will carry forward into current text extraction")

    opt[Unit]("preserve-annotations") action { (v, conf) =>
      conf.copy(preserveAnnotations = true)
    } text("location of prior docseg: annotations will carry forward into current text extraction")

    cmd("init") action { (_, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        initCorpus(ac)
      })
    } text ("init (or re-init) a corpus directory structure") // children()

    cmd("docseg") action { (v, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        segmentDocument(ac)
      })
    } text ("run document segmentation")

    cmd("build-fontdb") action { (v, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        buildFontDB(ac)
      })
    } text ("")

    cmd("show-fontdb") action { (v, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        showFontDB(ac)
      })
    } text ("")

    cmd("extract-fonts") action { (v, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        extractFonts(ac)
      })
    } text ("")


    cmd("images") action { (v, conf) =>
      setAction(conf, {(ac: AppConfig) =>
        extractImages(ac)
      })
    } text ("extract pdf pages as images")

  }


  parser.parse(args, AppConfig()).foreach{ config =>
    config.exec.foreach { _.apply(config) }
  }

  def getProcessList(conf: AppConfig): Seq[CorpusEntry] = {
    val corpus = Corpus(corpusRootOrDie(conf))

    println(s"starting Works in corpus ${corpus}")

    val toProcess = conf.inputFileList
      .map({ inputs =>
        // User specifed input file on command line:
        val inputLines = if (inputs.toString == "--") {
          io.Source.stdin.getLines.toList
        } else {
          val inputFiles =  RelPath(inputs)
          val lines = read(pwd/inputFiles)
          lines.split("\n").toList
        }


        inputLines
          .map(_.trim).filterNot(_.isEmpty())
          .filter({ inputLine =>
            if (!corpus.hasEntry(inputLine)) {
              println(s"warning: no corpus entry with id ${inputLine}, skipping")
              false
            } else true
          })
          .map(corpus.entry(_).get)

      }).getOrElse({
        conf.inputEntryDescriptor
          .map(e => Seq(corpus.entry(e).getOrElse{sys.error(s"unknown corpus entry${e}")}))
          .getOrElse(corpus.entries())
      })

    val skipped = if (conf.numToSkip > 0) toProcess.drop(conf.numToSkip) else toProcess
    val taken = if (conf.numToRun > 0) skipped.take(conf.numToRun) else skipped

    taken
  }


  // Decide if the specified output artifact exists, or if the --force option is specified
  def processOrSkipOrForce(conf: AppConfig, entry: CorpusEntry, artifactOutputName: String): Try[Option[String]] = {
    Try {
      if (entry.hasArtifact(artifactOutputName)) {
        if (conf.force) {
          entry.deleteArtifact(artifactOutputName)
          Some(artifactOutputName)
        } else None
      } else Some(artifactOutputName)
    }
  }


  def runProcessor(conf: AppConfig, artifactOutputName: String, process: (CorpusEntry, String) => Unit): Unit = {
    var i = 0
    getProcessList(conf).foreach { entry =>
      println(s"${i}. extracting ${entry} ${artifactOutputName}")

      processOrSkipOrForce(conf, entry, artifactOutputName) match {
        case Success(Some(_)) => process(entry, artifactOutputName)
        case Success(None)    => println(s"skipping existing ${entry}, use -x to force reprocessing")
        case Failure(t)       => sys.error(s"error: ${t.getClass} ${t.getMessage}")
      }

      i += 1
    }
  }

  def processCorpusArtifact(entry: CorpusEntry, outputName: String, processor: (InputStream, String) => String): Unit = {
    (for {
      pdfArtifact <- entry.getPdfArtifact
      pdfIns <- pdfArtifact.asInputStream.toOption
    } yield {
      try {
        val output = processor(pdfIns, pdfArtifact.artifactPath.toString)
        entry.putArtifact(outputName, output)
      } catch {
        case t: Throwable =>
          println(s"could not extract ${outputName}  for ${pdfArtifact}: ${t.getMessage}\n")

          println(t.toString())
          t.printStackTrace()
          println(t.getCause.toString())
          t.getCause.printStackTrace()
          s"""{ "error": "exception thrown ${t}: ${t.getCause}: ${t.getMessage}" }"""
      } finally {
        pdfIns.close()
      }})
  }

  def processCorpusEntryList(conf: AppConfig, processor: (CorpusEntry) => Unit): Unit = {
    var i = 0
    getProcessList(conf).foreach { entry =>
      println(s"${i}. processing ${entry} ")
      processor(entry)
      i += 1
    }
  }

  def processCorpus(conf: AppConfig, artifactOutputName: String, processor: (InputStream, String) => String): Unit = {
    runProcessor(conf, artifactOutputName, process)

    def process(entry: CorpusEntry, outputName: String): Unit = {
      val pdfArtifact = entry.getPdfArtifact()
      processCorpusArtifact(entry, outputName, processor)
    }
  }



  ///////////////////////
  // Initialize/normalize corpus entries

  def normalizeCorpusEntry(corpus: Corpus, pdf: Path): Unit = {
    val artifactPath = corpus.corpusRoot / s"${pdf.name}.d"
    if (pdf.isFile && !(exists! artifactPath)) {
      println(s" creating artifact dir ${pdf}")
      mkdir! artifactPath
    }
    if (pdf.isFile) {
      val dest = artifactPath / pdf.name

      if (exists(dest)) {
        println(s"corpus already contains file ${dest}, skipping...")
      } else {
        println(s" stashing ${pdf}")
        mv.into(pdf, artifactPath)
      }
    }
  }

  def initCorpus(conf: AppConfig): Unit = {
      conf.corpusRoot
      .map({croot =>
        val fullPath = pwd/RelPath(croot)
        val validPath = exists(fullPath)

        if (!validPath) {
          sys.error(s"init: invalid corpus root specified ${fullPath}")
        } else {
          Corpus(fullPath).touchSentinel()
        }
      }).getOrElse(sys.error("no corpus root specified"))


    normalizeCorpusEntries(conf)
  }

  def normalizeCorpusEntries(conf: AppConfig): Unit = {
    val corpus = Corpus(corpusRootOrDie(conf))

    println(s"normalizing corpus at ${corpus.corpusRoot}")

    ls(corpus.corpusRoot)
    .filter(p=> p.isFile && (p.ext=="pdf" || p.ext=="ps"))
    .foreach { pdf =>
      normalizeCorpusEntry(corpus, pdf)
    }
  }


  /////////// Specific Commands


  import extract.fonts.SplineFont

  def loadOrExtractFonts(conf: AppConfig, corpusEntry: CorpusEntry): Seq[SplineFont.Dir] = {
    import extract.fonts.SplineFonts
    import ammonite.{ops => fs}
    import fs._
    import fs.ImplicitWd._

    if (!corpusEntry.hasArtifact("font.props", "fonts")) {
      for {
        pdf <- corpusEntry.getPdfArtifact
        pdfPath <- pdf.asPath
      } {
        try{

        val res = %%("bin/extract-fonts", "-f="+pdfPath.toString())
        println(s"ran extract-fonts exit=${res.exitCode}")
        } catch {
          case t: Throwable => println(s"Error extracting fonts, skipping")
        }
      }
    }

    if (corpusEntry.hasArtifact("fonts")) {
      val fontDirs = for {
        fontDir <- corpusEntry.getArtifact("fonts").toSeq
        pdir <- fontDir.asPath.toOption.toSeq
        sfdirs = fs.ls(pdir).filter(_.ext=="sfdir")
        sfdir <- sfdirs
      } yield {
        // println(s"loading fonts from ${sfdir}")
        SplineFonts.loadSfdir(sfdir)
      }

      fontDirs
    } else {
      println(s"no extracted fonts found")
      Seq()
    }
  }



  def loadPredsynthUberJson(conf: AppConfig): Option[Map[String, Paper]] = {
    for {
      pfile <- conf.priorPredsynthFile
      dict <- PredsynthLoad.loadPapers(pwd / RelPath(pfile))
    } yield dict
  }



  def runPageSegmentation(documentURI: URI, pdfins: InputStream, fontDirs: Seq[Dir]): Try[DocumentSegmenter] =  {
    Try {
      val segmenter = DocumentSegmenter.createSegmenter(documentURI, pdfins, fontDirs)
      segmenter.runPageSegmentation()
      segmenter
    }
  }

  def writePredsynthJson(predsynthPaper: Paper, corpusEntry: CorpusEntry): Unit = {
    new predsynth.PredsynthJsonFormats {
      import play.api.libs.json, json._
      val pjson = Json.toJson(predsynthPaper)
      val jsOut = Json.prettyPrint(pjson)
      corpusEntry.putArtifact("predsynth.json", jsOut)
    }
  }


  def textAlignPredsynthDB(segmenter: DocumentSegmenter, entry: Option[Paper]): Try[Unit] = {
    Try { for {
      predSynthPaper <- entry
    } {
      segmenter.alignPredSynthPaper(predSynthPaper)
    }}
  }

  def segmentDocument(conf: AppConfig): Option[segment.DocumentSegmenter] = {
    val artifactOutputName = "docseg.json"

    var rsegmenter: Option[segment.DocumentSegmenter] = None

    val predsynthPapers: Map[String, Paper] =
      loadPredsynthUberJson(conf)
        .getOrElse(Map())

    processCorpusEntryList(conf, {corpusEntry =>
      try {
        val processResults = for {
          output          <- processOrSkipOrForce(conf, corpusEntry, artifactOutputName)
          predsynthOutput <- processOrSkipOrForce(conf, corpusEntry, "predsynth.json")
          _               <- output
          // fontDirs      = loadOrExtractFonts(conf, corpusEntry)
          pdf             <- corpusEntry.getPdfArtifact
          pdfins          <- pdf.asInputStream
          _                = pdf.asPath
          segmenter       <- runPageSegmentation(corpusEntry.getURI, pdfins, Seq())
        } {
          // TODO pass the URI of the pdf into the extractor, don't pass the InputStream and close it here.
          if (pdfins!=null) pdfins.close()

          rsegmenter = Some(segmenter)

          val entryFilename = corpusEntry.entryDescriptorRoot
          val paper = predsynthPapers.get(entryFilename)
          if (conf.preserveAnnotations) {
            // load the prior docseg
            println("loading/converting prior annots")
          }

          textAlignPredsynthDB(segmenter, paper)

          paper.foreach{ p => writePredsynthJson(p, corpusEntry) }
          val output = formats.DocumentIO.richTextSerializeDocument(segmenter.zoneIndexer)
          corpusEntry.putArtifact(artifactOutputName, output)
        }
      } catch {
        case t: Throwable => die(t)
      }
    })

    rsegmenter

  }

  def extractImages(conf: AppConfig): Unit = {
    import ammonite.{ops => fs}
    import fs._
    import fs.ImplicitWd._


    processCorpusEntryList(conf, {corpusEntry =>

      for {
        pdf <- corpusEntry.getPdfArtifact
        pdfPath <- pdf.asPath
      } {
        val pageImagePath = corpusEntry.artifactsRoot / "page-images"
        if (! exists(pageImagePath)) {
          mkdir(pageImagePath)
        }
        val pageImageFilespec = pageImagePath / "page-%d.png"

        val res = %%("mudraw", "-r", "128", "-o", pageImageFilespec, pdfPath)

      }

    })
  }

  def buildFontDB(conf: AppConfig): Unit = {
    import ammonite.{ops => fs}
    import fs._
    // import fs.ImplicitWd._
    import extract.fonts._

    val dbfile = conf.dbPath.getOrElse {
      sys.error("please specify database path")
    }

    val dbpath = pwd / RelPath(dbfile)

    val db = new FontDatabaseApi(dbpath)

    try {
      db.createDBDir()

      processCorpusEntryList(conf, {corpusEntry =>

        if (corpusEntry.hasArtifact("fonts")) {
          for {
            fontDir <- corpusEntry.getArtifact("fonts")
            pdir <- fontDir.asPath
            sfdirs = fs.ls(pdir).filter(_.ext=="sfdir")
            sfdir <- sfdirs
          } {
            println(s"adding fonts from ${sfdir}")
            val sfs = SplineFonts.loadSfdir(sfdir)

            db.addFontDir(sfs)
          }
        }
      })
    } finally {
      db.shutdown()
    }
  }


  def extractFonts(conf: AppConfig): Unit = {
    import utils.IdGenerator

    processCorpusEntryList(conf, {corpusEntry =>

      val charExtractor = new PdfTextExtractor(Set(), IdGenerator[RegionID]())

      for {
        pdfArtifact <- corpusEntry.getPdfArtifact
        pdfIns <- pdfArtifact.asInputStream.toOption
      } yield {

        val fobjs = FontExtractor.extractFontObjects(pdfIns)

        val fontObjsFile = "fontobjs.txt"

        processOrSkipOrForce(conf, corpusEntry, fontObjsFile) match {
          case Success(Some(_)) => corpusEntry.putArtifact(fontObjsFile, fobjs)
          case Success(None)    =>
          case Failure(t)       => die(t)
        }
      }
    })
  }


  def showFontDB(conf: AppConfig): Unit = {
    import extract.fonts._

    val dbfile = conf.dbPath.getOrElse {
      sys.error("please specify database path")
    }

    val dbpath = pwd / RelPath(dbfile)

    val db = new FontDatabaseApi(dbpath)

    try {
      // db.showFontTrees()
      db.showHashedGlyphs()

    } finally {
      db.shutdown()
    }
  }

  // def extractCharacters(conf: AppConfig): Unit = {}

}
