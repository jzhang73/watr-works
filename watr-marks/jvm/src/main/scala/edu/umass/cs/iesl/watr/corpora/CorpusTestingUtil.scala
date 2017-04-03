package edu.umass.cs.iesl.watr
package corpora

import textboxing.{TextBoxing => TB}, TB._
import TypeTags._

trait CorpusTestingUtil extends PlainTextCorpus {
  def createEmptyDocumentCorpus(): DocumentCorpus
  // def regionIdGen: utils.IdGenerator[RegionID]
  val regionIdGen = utils.IdGenerator[RegionID]()

  var freshDocstore: Option[DocumentCorpus] = None

  def docStore: DocumentCorpus = freshDocstore
    .getOrElse(sys.error("Uninitialized DocumentCorpus; Use CleanDocstore() class"))

  def initEmpty(): Unit = {
    try {
      regionIdGen.reset()
      freshDocstore = Some(createEmptyDocumentCorpus())
    } catch {
      case t: Throwable =>
        val message = s"""error: ${t}: ${t.getCause}: ${t.getMessage} """
        println(s"ERROR: ${message}")
        t.printStackTrace()
    }
  }

  trait CleanDocstore {
    initEmpty()
  }

  class FreshDocstore(pageCount: Int=0) {
    initEmpty()
    try {
      loadSampleDoc(pageCount)
    } catch {
      case t: Throwable =>
        val message = s"""error: ${t}: ${t.getCause}: ${t.getMessage} """
        println(s"ERROR: ${message}")
        t.printStackTrace()
    }
  }

  val stableId = DocumentID("stable-id#23")

  def loadSampleDoc(pageCount: Int): Unit = {
    val pages = MockPapers.genericTitle
      .take(pageCount)

    addDocument(stableId, pages)
  }

  def reportDocument(stableId: String@@DocumentID): TB.Box = {
    val docBoxes = for {
      docId <- docStore.getDocument(stableId).toSeq
    } yield {
      val pagesBox = for {
        pageId <- docStore.getPages(docId)
      } yield {
        val pageGeometry = docStore.getPageGeometry(pageId)

        val regionBoxes = for {
          regionId <- docStore.getTargetRegions(pageId)
        } yield {
          val targetRegion = docStore.getTargetRegion(regionId)
          // val imageBytes = getTargetRegionImage(regionId)

          "t: ".box + targetRegion.toString.box
        }


        (
          indent(2)("PageGeometry")
            % indent(4)(pageGeometry.toString.box)
            % indent(2)("TargetRegions + zones/regions")
            % indent(4)(vcat(regionBoxes))
            % indent(2)("Page Zones")
            // % indent(4)(vcat(pageZoneBoxes))
        )
      }

      val zoneBoxes = for {
        label <- docStore.getZoneLabelsForDocument(docId)
        zoneId <- docStore.getZonesForDocument(docId, label)
        textReflow <- docStore.getTextReflowForZone(zoneId)
      } yield {
        (textReflow.toText.box
          % docStore.getZone(zoneId).toString().box)
      }
      (s"Document ${docId} (${stableId}) report"
        % indent(4)(vcat(pagesBox))
        % indent(2)("Zones")
        % indent(4)(vcat(zoneBoxes))
      )
    }
    vcat(docBoxes)
  }


}