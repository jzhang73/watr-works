package edu.umass.cs.iesl.watr
package watrcolors
package server

import akka.actor.{ActorRef, Actor, ActorSystem}
import akka.util.ByteString
import akka.actor.ActorDSL._

import spray.routing.SimpleRoutingApp
import spray.http._
import HttpHeaders._
import HttpMethods._

import concurrent.duration._
import scala.concurrent.Future

import corpora._
import textreflow._
import geometry._
import display._

import autowire._
import upickle.{default => UPickle}
import UPickle._
import TypeTagPicklers._


class EmbeddedServer(
  reflowDB: TextReflowDB,
  corpus: Corpus,
  url: String,
  port: Int
) extends SimpleRoutingApp with WatrColorsApi with WatrShellApi {


  implicit val system = ActorSystem()
  import system.dispatcher
  val corsHeaders: List[ModeledHeader] =
    List(
      `Access-Control-Allow-Methods`(OPTIONS, GET, POST),
      `Access-Control-Allow-Origin`(AllOrigins),
      `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
      `Access-Control-Max-Age`(1728000)
    )


  /**
   * Actor meant to handle long polling, buffering messages or waiting actors
   */
  private val longPoll = actor(new Actor{
    var waitingActor: Option[ActorRef] = None
    var queuedMessages = List[RemoteCall]()

    /**
     * Flushes returns nothing to any waiting actor every so often,
     * to prevent the connection from living too long.
     */
    case object Clear

    system.scheduler.schedule(0.seconds, 10.seconds, self, Clear)

    def respond(a: ActorRef, s: String) = {
      val respEntity = HttpEntity(
        HttpData(s)
      )
      a ! HttpResponse(
        entity = respEntity,
        headers = corsHeaders
      )
    }
    def receive = (x: Any) => (x, waitingActor, queuedMessages) match {
      case (a: ActorRef, _, Nil) =>
        waitingActor = Some(a)

      case (a: ActorRef, None, msgs) =>
        println(s"""receive: Actor ! msgs waiting""")
        val wr = UPickle.write[List[RemoteCall]](queuedMessages)
        respond(a, wr)
        queuedMessages = Nil

      case (msg: RemoteCall, None, msgs) =>
        println(s"""receive: msg enqueue""")
        queuedMessages = msg :: msgs

      case (msg: RemoteCall, Some(a), Nil) =>
        println(s"""receive: waiting actor gets msg""")
        val wr = UPickle.write(List(msg))
        respond(a, wr)
        waitingActor = None

      case (Clear, waitingOpt, msgs) =>
        val wr = UPickle.write(msgs)
        waitingOpt.foreach(respond(_, wr))
        waitingActor = None
    }
  })

  def webjarResources = pathPrefix("webjars") {
    getFromResourceDirectory("META-INF/resources/webjars")
  }

  def assets = pathPrefix("assets") {
    getFromResourceDirectory("")
  }

  def httpResponse(resp: String) = {
    HttpEntity(MediaTypes.`text/html`, resp)
  }

  // import geometry._

  def producePageImage(path: List[String]): Array[Byte] = {
    println(s"producePageImage: ${path}")

    val uriPath = path.headOption.getOrElse { sys.error("producePageImage: no path specified") }

    reflowDB
      .serveImageWithURI(TargetRegion.fromUri(uriPath))
      .bytes
      // .map(_.bytes)
      // .getOrElse { sys.error("producePageImage: no images found")}
  }

  def pageImageServer = pathPrefix("img")(
    path(Segments)(pathSegments =>
      complete(
        HttpResponse(entity =
          HttpEntity(MediaTypes.`image/png`, HttpData(producePageImage(pathSegments)))
        )
      )
    )
  )

  def mainFrame = pathPrefix("") (
    extract(_.request.entity.data) ( requestData => ctx =>
      ctx.complete { httpResponse(html.ShellHtml().toString()) }
    )
  )

  // def apiRoute(
  //   prefix: String,
  //   server: ShellsideServer
  // ) = pathPrefix("api")(
  //   path(prefix / Segments) { segs =>
  //     extract(_.request.entity.data) { requestData => ctx =>
  //       ctx.complete(
  //         server
  //           .route({
  //             val reqStr = requestData.asString
  //             val argmap = UPickle.read[Map[String, String]](reqStr)
  //             autowire.Core.Request(segs, argmap)
  //           })
  //           .map({responseData =>
  //             HttpEntity(HttpData(ByteString(responseData)))
  //           })
  //       )
  //     }
  //   }
  // )

  def apiRoute(
    prefix: String,
    router: autowire.Core.Router[String]
  ) = pathPrefix("api")(
    path(prefix / Segments) { segs =>
      extract(_.request.entity.data) { requestData => ctx =>
        ctx.complete(
          router({
            val reqStr = requestData.asString
            val argmap = UPickle.read[Map[String, String]](reqStr)
            autowire.Core.Request(segs, argmap)
          }).map(responseData =>
            HttpEntity(HttpData(ByteString(responseData)))
          )
        )
      }
    }
  )

  // apiRoute("explorer", AutowireServer.route[CorpusExplorerApi](corpusExplorerServer)) ~

  // val shellServer = new ShellsideServer(this)
  // object ShellServer extends autowire.Server[String, UPickle.Reader, UPickle.Writer]

  def autowireRoute = apiRoute("autowire", ShellsideServer.route[WatrShellApi](this))

  startServer(url, port)(
    get( webjarResources
      ~  assets
      ~  pageImageServer
      ~  mainFrame
    ) ~
    post(
      path("notifications") (ctx =>
        longPoll ! ctx.responder)
      ~  autowireRoute
    )
  )

  def kill() = system.terminate()


  val labeler = new LabelingServer(reflowDB, corpus)

  val ClientSite  = new ShellsideClient(longPoll)
  val api = ClientSite[WatrColorsApi]

  def clear(): Unit = {
    api.clear().call()
  }

  def print(level: String, msg: String): Unit = {
    api.print(level, msg).call()
  }

  def echoTextReflows(textReflows: List[TextReflow]): Unit = {
    api.echoTextReflows(textReflows).call()
  }

  def echoLabeler(lwidget: LabelWidget): Unit = {
    import matryoshka._
    import matryoshka.data._
    import matryoshka.implicits._

    import LabelWidgetF._

    /// pre-create target region images w/embossings
    def visit(t: LabelWidgetF[Unit]): Unit = t match {
      case Target(tr, emboss) =>
        // pre-create the images w/labels embossed as color overlays and put them in database
        labeler.embossTargetRegion(tr, emboss)

      case _ => ()
    }

    // side-effecting...
    lwidget.cata(visit)

    api.echoLabeler(lwidget).call()
  }

  def helloColors(msg: String): Unit = {
    api.helloColors(msg).call()
  }


  // Handle incoming messages from WatrColors:
  def helloShell(msg: String): Unit = {
    println(s"helloShell: $msg")
  }

  def onDrawPath(artifactId: String,path: Seq[Point]): Unit = {
    println(s"onDrawPath: ")
  }

  def onSelectLTBounds(artifactId: String,bbox: LTBounds): Unit = {
    println(s"onSelectLTBounds: ")
  }







}
