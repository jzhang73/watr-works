package edu.umass.cs.iesl.watr
package corpora

import geometry._
import watrmarks._
import textreflow._
import TextReflowF._

import watrmarks.{StandardLabels => LB}

trait DocumentZoningApi {
  val Rel = RelationModel

  def getDocuments(n: Int=Int.MaxValue, skip: Int=0, labelFilters: Seq[Label]=List()): Seq[String@@DocumentID]
  def getDocumentCount(labelFilters: Seq[Label]=List()): Int
  def addDocument(stableId: String@@DocumentID): Int@@DocumentID
  def getDocument(stableId: String@@DocumentID): Option[Int@@DocumentID]
  // def getDocumentDef(docId: Int@@DocumentID): Rel.Document

  def addPage(docId: Int@@DocumentID, pageNum: Int@@PageNum): Int@@PageID
  def getPage(docId: Int@@DocumentID, pageNum: Int@@PageNum): Option[Int@@PageID]
  def getPageIdentifier(pageId: Int@@PageID): RecordedPageID
  def getPageDef(pageId: Int@@PageID): Option[Rel.Page]
  def getPages(docId: Int@@DocumentID): Seq[Int@@PageID]
  def getPageGeometry(pageId: Int@@PageID): LTBounds
  def setPageGeometry(pageId: Int@@PageID, geom: LTBounds): Unit
  def setPageImage(pageId: Int@@PageID, bytes: Array[Byte]): Unit
  def getPageImage(pageId: Int@@PageID): Option[Array[Byte]]

  def addTargetRegion(pageId: Int@@PageID, bbox:LTBounds): Int@@RegionID
  def getTargetRegion(regionId: Int@@RegionID): TargetRegion

  def setTargetRegionImage(regionId: Int@@RegionID, bytes: Array[Byte]): Unit
  def getTargetRegionImage(regionId: Int@@RegionID): Option[Array[Byte]]
  def deleteTargetRegionImage(regionId: Int@@RegionID): Unit

  def getTargetRegions(pageId: Int@@PageID): Seq[Int@@RegionID]


  // Construct
  def createZone(regionId: Int@@RegionID, label: Int@@LabelID): Int@@ZoneID
  def addZoneRegion(zoneId: Int@@ZoneID, regionId: Int@@RegionID): Unit
  def removeZoneRegion(zoneId: Int@@ZoneID, regionId: Int@@RegionID): Option[Int@@ZoneID]

  // Destroy
  def deleteZone(zoneId: Int@@ZoneID): Unit

  // Locate
  def getZone(zoneId: Int@@ZoneID): Zone
  def getZoneLabelsForDocument(docId: Int@@DocumentID): Seq[Int@@LabelID]
  def getZonesForDocument(docId: Int@@DocumentID, label: Int@@LabelID): Seq[Int@@ZoneID]
  def getZoneForRegion(regionId: Int@@RegionID, label: Label): Option[Int@@ZoneID]

  def ensureLabel(label: Label): Int@@LabelID
  def getLabel(labelId: Int@@LabelID): Label

  // Get Text
  def getModelTextReflowForZone(zoneId: Int@@ZoneID): Option[Rel.TextReflow]
  def getTextReflowForZone(zoneId: Int@@ZoneID): Option[TextReflow]
  def setTextReflowForZone(zoneId: Int@@ZoneID, textReflow: TextReflow): Unit

  ///////////////////////
  /// Derived operations

  def getDocumentZones(docId: Int@@DocumentID, label: Label): Seq[Zone] =
    getZonesForDocument(docId, ensureLabel(label)).map(getZone(_))

  def getPageZones(stableId: String@@DocumentID, pageNum: Int@@PageNum, label: Label): Seq[Zone] = for {
    docId     <- getDocument(stableId).toSeq
    pageId    <- getPage(docId, pageNum).toSeq
    zone      <- getPageZones(pageId, label)
  } yield zone

  def getPageZones(pageId: Int@@PageID, label: Label): Seq[Zone] = (for {
    regionId  <- getTargetRegions(pageId)
    zoneId    <- getZoneForRegion(regionId, label)
  } yield { getZone(zoneId) })
    .foldLeft(Seq[Zone]()){ case (acc, e) =>
      if (acc.exists(_.id==e.id)) acc
      else acc :+ e
    }

  def getPageVisualLines(stableId: String@@DocumentID, pageNum: Int@@PageNum): Seq[Zone] =
    getPageZones(stableId, pageNum, LB.VisualLine)

  def getPageVisualLines(pageId: Int@@PageID): Seq[Zone] =
    getPageZones(pageId, LB.VisualLine)

  def getTextReflowForTargetRegion(regionId: Int@@RegionID): Option[TextReflow] = for {
    zoneId  <- getZoneForRegion(regionId, LB.VisualLine)
    reflow <- getTextReflowForZone(zoneId)
  } yield reflow

  def ensurePageRegion(pageRegion: PageRegion): Option[Int@@RegionID] = {
    val stableId = pageRegion.page.stable.stableId
    val pageNum =  pageRegion.page.stable.pageNum
    ensureTargetRegion(stableId, pageNum, pageRegion.bbox)
  }

  def ensureTargetRegion(stableId: String@@DocumentID, pageNum: Int@@PageNum, bbox:LTBounds): Option[Int@@RegionID] = {
    for {
      docId     <- getDocument(stableId)
      pageId    <- getPage(docId, pageNum)
    } yield {
      addTargetRegion(pageId, bbox)
    }
  }

  def labelRegions(label: Label, regions: Seq[PageRegion]): Option[Int@@ZoneID] = {
    val labelId = ensureLabel(label)

    for {
      pageRegion <- regions.headOption
      headRegion <- ensurePageRegion(pageRegion)
    } yield {
      val zoneId = createZone(headRegion, labelId)
      for {
        tailRegion <- regions.tail
        tailRegionId <- ensurePageRegion(tailRegion)
      }  {
        addZoneRegion(zoneId, tailRegionId)
      }
      zoneId
    }
  }

}