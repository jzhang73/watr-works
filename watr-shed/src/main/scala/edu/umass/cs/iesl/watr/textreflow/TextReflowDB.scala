package edu.umass.cs.iesl.watr
package textreflow

import doobie.imports._
import scalaz.syntax.applicative._

import geometry._
import spindex._
import segment._

import com.sksamuel.scrimage._
import shapeless._
import databasics._

import scalaz.std.list._
import scalaz.std.option._
import scalaz.syntax.traverse._

import textreflow.data._
import watrmarks.{StandardLabels => LB}
import watrmarks._
import utils.EnrichNumerics._

class TextReflowDB(
  val tables: TextReflowDBTables
) extends DoobiePredef {

  lazy val xa = tables.xa

  def addSegmentation(ds: DocumentSegmentation): Unit = {

    val mpageIndex = ds.mpageIndex
    val docId = mpageIndex.getDocumentID()

    def insertPagesInfo(docPk: Int) = for {
      pageId <- mpageIndex.getPages.toList
    } yield for {
      // _         <- putStrLn(s"inserting page rec for page ${pageId}")
      pagePrKey <- insertPageGeometry(docPk, mpageIndex.getPageGeometry(pageId), ds.pageImages.page(pageId))
    } yield ()

    val query = for {
      // _         <- putStrLn("Starting ...")
      docPrKey  <- getOrInsertDocumentID(docId)
      _         <- insertPagesInfo(docPrKey).sequenceU
      // _         <- putStrLn("insert multi pages...")
      _         <- insertMultiPageIndex(docId, mpageIndex)
    } yield docPrKey

    val docPrKey = query.transact(xa).unsafePerformSync


  }

  def insertMultiPageIndex(docId: String@@DocumentID, mpageIndex: MultiPageIndex): ConnectionIO[List[Unit]] = {
    // To start, just create entries for all Component/Zones labelled VisualLine

    val query: ConnectionIO[List[Unit]] = (for {
      vlineCC      <- mpageIndex.getDocumentVisualLines.flatten.toList
      maybeReflow   = mpageIndex.getTextReflowForComponent(vlineCC.id)
      vlineZoneId   = mpageIndex.getZoneForComponent(vlineCC.id).get
    } yield for {
      // _            <- putStrLn(s"Inserting zone for line ${vlineCC.id}")
      zpk          <- insertZone(docId, vlineZoneId)
      treflowPk    <- maybeReflow.toList.map(r => insertTextReflow(zpk, r)).sequence

      // insert zone -> targetregion (w/ordering)
      // _            <- putStrLn(s"   inserting targetregion")
      targetRegPk  <- insertTargetRegion(vlineCC.targetRegion)
      // _            <- putStrLn(s"   linking zone -> targetregion ")
      _            <- linkZoneToTargetRegion(zpk, targetRegPk)

      // insert zone -> label (VisualLine is the only important label right now)
      // _            <- putStrLn(s"   linking zone -> label ")
      _            <- linkZoneToLabel(zpk, LB.VisualLine)

      // insert targetregion images for VisualLines
      // _            <- putStrLn(s"   creating VisualLines images")
      // _            <- getOrCreateTargetRegionImage(vlineCC.targetRegion)
    } yield ()).sequenceU

    query
  }

  def selectTargetRegions(docId: String@@DocumentID, pageId: Int@@PageID): List[TargetRegion] = {
    val query = sql"""
     select
          tr.targetregion, d.stable_id, pg.pagenum, tr.bleft, tr.btop, tr.bwidth, tr.bheight
     from
          targetregion   as tr join
          page           as pg on (pg.page=tr.page) join
          document       as d  on (pg.document=d.document)
     where
        d.stable_id=${docId} AND
        pg.pagenum=${pageId}
    """.query[TargetRegion]
      .list

    query.transact(xa).unsafePerformSync
  }

  def selectZones(docId: String@@DocumentID, pageId: Int@@PageID, label: Label): List[Zone] = {
    val query = sql"""
     select
        zn.zone, tr.targetregion, d.stable_id, pg.pagenum, tr.bleft, tr.btop, tr.bwidth, tr.bheight
     from
        zone                      as zn
        join zone_to_label        as z2l   on (zn.zone=z2l.zone)
        join label                as lb    on (z2l.label=lb.label)
        join zone_to_targetregion as z2tr  on (zn.zone=z2tr.zone)
        join targetregion         as tr    on (z2tr.targetregion=tr.targetregion)
        join page                 as pg    on (pg.page=tr.page)
        join document             as d     on (pg.document=d.document)
     where
        d.stable_id=${docId}
        AND pg.pagenum=${pageId.unwrap}
        AND lb.key=${label.fqn}
    """.query[ZonedRegion]
      .list
      .map(_.foldLeft(List[Zone]())({ case (zones, zrec) =>
        import scala.::
        zones match {
          case Nil =>
            Zone(zrec.id, List(zrec.targetRegion), List()) :: Nil

          case h :: tail if h.id == zrec.id =>
            h.copy(regions = zrec.targetRegion +: h.regions) :: tail

          case h :: tail =>
            Zone(zrec.id, List(zrec.targetRegion), List()) :: zones
        }

      }))
      .map({zoneList =>
        zoneList
          .map(z => z.copy(regions = z.regions.reverse))
          .reverse
      })

    query.transact(xa).unsafePerformSync
  }


  def insertZone(docId: String@@DocumentID, zoneId: Int@@ZoneID): ConnectionIO[Int] = {
    sql"""
       insert into zone (zoneid, document)
       values (${zoneId}, (select document from document where stable_id=${docId}))
    """.update.withUniqueGeneratedKeys[Int]("zone")
  }

  def insertTextReflow(zonePk: Int, textReflow: TextReflow): ConnectionIO[Int] = {
    import TextReflowJsonCodecs._
    import play.api.libs.json, json._
    val js = textReflow.toJson()
    val jsStr = Json.stringify(js)
    val query = for {
      // _  <- putStrLn(s"   inserted textreflow ${}")
      pk <- sql"""
       insert into textreflow (reflow, zone)
       values (${jsStr}, ${zonePk})
      """.update.withUniqueGeneratedKeys[Int]("textreflow")
    } yield { pk }

    query
  }


  def getTextReflowForZone(zone: Zone): Option[TextReflow] = {
    selectTextReflowForZone(zone).
      transact(xa).unsafePerformSync
  }

  def getTextReflowsForTargetRegion(targetRegion: TargetRegion): List[(Zone, TextReflow)] = {
    val query = for {
      // _          <- putStrLn(s"getTextReflowsForTargetRegion: ${targetRegion}")
      // _          <- putStrLn(s"selectZonesForTargetRegion")
      zones      <- selectZonesForTargetRegion(targetRegion, LB.VisualLine)
      zids        = zones.map(z => z.id).mkString(", ")
      // _          <- putStrLn(s"zone (${zids}) count ${zones.length}; selecting reflows")
      reflows    <- zones.toList.map(selectTextReflowForZone(_)).sequence
    } yield {
      zones.zip(reflows)
    }

    query.
      transact(xa).unsafePerformSync
      .filter(_._2.isDefined)
      .map({case(z, r) => (z, r.get)})
  }

  def selectZonesForTargetRegion(targetRegion: TargetRegion, label: Label): ConnectionIO[List[Zone]] = {
    val trUri = targetRegion.uri

    val query = sql"""
     select
        zn.zone, tr.targetregion, d.stable_id, pg.pagenum, tr.bleft, tr.btop, tr.bwidth, tr.bheight
     from
        zone                      as zn
        join zone_to_label        as z2l   on (zn.zone=z2l.zone)
        join label                as lb    on (z2l.label=lb.label)
        join zone_to_targetregion as z2tr  on (zn.zone=z2tr.zone)
        join targetregion         as tr    on (z2tr.targetregion=tr.targetregion)
        join page                 as pg    on (pg.page=tr.page)
        join document             as d     on (pg.document=d.document)
     where
        tr.uri = ${trUri}
    """.query[ZonedRegion]
      .list
      .map(_.foldLeft(List[Zone]())({ case (zones, zrec) =>
        import scala.::
        zones match {
          case Nil =>
            Zone(zrec.id, List(zrec.targetRegion), List()) :: Nil

          case h :: tail if h.id == zrec.id =>
            h.copy(regions = zrec.targetRegion +: h.regions) :: tail

          case h :: tail =>
            Zone(zrec.id, List(zrec.targetRegion), List()) :: zones
        }

      }))
      .map({zoneList =>
        zoneList
          .map(z => z.copy(regions = z.regions.reverse))
          .reverse
      })

    query
  }

  def selectTextReflowForZone(zone: Zone): ConnectionIO[Option[TextReflow]] = {
    import TextReflowJsonCodecs._
    import play.api.libs.json, json._

    val query = sql"""
     select tr.reflow
     from   textreflow as tr join zone as z on (tr.zone=z.zone)
     where  z.zone=${zone.id}
    """.query[String]
      .option
      .map({maybeStr =>
        maybeStr.map({str =>
          jsonToTextReflow(Json.parse(str))
        })
      })

    query
  }

  def insertPageGeometry(docPrKey: Int, pageGeometry: PageGeometry, pageImage: Image): ConnectionIO[Int] = {
    println(s"adding page w/image dims ${pageImage.dimensions} ")

    val PageGeometry(pageId, LTBounds(l, t, w, h)) = pageGeometry
    val (bl, bt, bw, bh) = (dtoi(l), dtoi(t), dtoi(w), dtoi(h))

    sql"""
      insert into page (document, pagenum, pageimg, bleft, btop, bwidth, bheight)
      values (${docPrKey}, ${pageId}, ${pageImage.bytes}, $bl, $bt, $bw, $bh)
    """.update.withUniqueGeneratedKeys("page")

  }


  def insertDocumentID(docId: String@@DocumentID): ConnectionIO[Int] = {
    sql"""
       insert into document (stable_id)
       values (${docId})
    """.update.withUniqueGeneratedKeys[Int]("document")
  }

  def getDocuments(): List[String@@DocumentID] = {
    sql"select stable_id from document"
      .query[String@@DocumentID].list
      .transact(xa)
      .unsafePerformSync
  }

  def selectDocumentID(docId: String@@DocumentID): ConnectionIO[Int] = {
    sql"select document from document where stable_id=${docId}"
      .query[Int].unique
  }

  def getOrInsertDocumentID(docId: String@@DocumentID): ConnectionIO[Int] = {
    sql"""select document from document where stable_id=${docId}"""
      .query[Int].option
      .flatMap({
        case Some(pk) => FC.delay(pk)
        case None => insertDocumentID(docId)
      })
  }


  def selectPageGeometry(docId: String@@DocumentID, pageId: Int@@PageID): ConnectionIO[Option[PageGeometry]] = {
    val e = FC.delay(Option.empty[PageGeometry])
    val query = for {
      _ <- putStrLn(s"selectPageGeometry: selecting docid ${docId}")
      docPk    <- selectDocumentID(docId)
      _ <- putStrLn(s"selectPageGeometry: selected docid ${docPk}")
      mpagePk  <- selectPage(docPk, pageId)
      _ <- putStrLn(s"selectPageGeometry: selected page pk ${mpagePk}")
      pgeom    <- mpagePk.fold(e)(pagePk =>
        sql"""select bleft, btop, bwidth, bheight from page where page=${pagePk}"""
          .query[LTBounds].option
          .map(_.map(ltb => PageGeometry(pageId, ltb)))
      )
      _ <- putStrLn(s"selectPageGeometry: selected page geometry ${pgeom}")
    } yield {
      pgeom
    }
    query
  }

  def selectPageImage(docId: String@@DocumentID, pageId: Int@@PageID): ConnectionIO[Option[Image]] = {
    val e = FC.delay(Option.empty[Image])
    val query = for {
      docPk  <- selectDocumentID(docId)
      mpagePk <- selectPage(docPk, pageId)
      image <- mpagePk.fold(e)(pagePk =>
        sql"""select pageimg from page where page=${pagePk}"""
          .query[Array[Byte]].unique
          .map(bytes => Option(Image(bytes)))
      )
    } yield image

    query
  }

  def deleteTargetRegionImage(targetRegion: TargetRegion): ConnectionIO[Int] = {
    val trUri = targetRegion.uri
    sql"""
       delete from targetregion_image
       using
          targetregion as tr
       where
          tr.targetregion = targetregion_image.targetregion AND
          tr.uri = ${trUri}
    """.update.run
  }

  def selectTargetRegionImage(targetRegion: TargetRegion): ConnectionIO[Option[Image]] = {
    val trUri = targetRegion.uri
    val query = sql"""
       select img.image
       from targetregion_image as img join targetregion as tr
       on img.targetregion=tr.targetregion
       where tr.uri = ${trUri}
    """.query[Array[Byte]]
      .option
      .map(_.map(Image(_)))

    query
  }

  def ensureLabel(label: Label): ConnectionIO[Int] = for {
    maybePk <- sql"""select label from label where key=${label.fqn}""".query[Int].option
    pk      <- maybePk match {
      case Some(id)    => id.point[ConnectionIO]
      case None        => sql"""insert into label (key) values (${label.fqn})""".update.run
    }
  } yield pk


  def linkZoneToLabel(zonePk: Int, label: Label): ConnectionIO[Unit] = {
    for {
      labelPk <- ensureLabel(label)
      _       <- sql"""insert into zone_to_label (zone, label) values (${zonePk}, ${labelPk})""".update.run
    } yield ()
  }

  def linkZoneToTargetRegion(zonePk: Int, targetRegionPk: Int): ConnectionIO[Unit] = {
    sql"""
       insert into zone_to_targetregion (zone, targetregion)
       values (${zonePk}, ${targetRegionPk})
    """.update.run.map(_ => ())
  }


  def selectPage(docPk: Int, pageId: Int@@PageID): ConnectionIO[Option[Int]] = {
    sql"""select page from page where document=${docPk} and pagenum=${pageId.unwrap}"""
      .query[Int].option
  }

  type B4Int = Int :: Int :: Int :: Int :: HNil

  def ensureTargetRegion(targetRegion: TargetRegion): ConnectionIO[Int] = {
    val trUri = targetRegion.uri

    val query = for {
      maybePk <- sql"""select targetregion from targetregion where uri=${trUri}""".query[Int].option
      pk       <- maybePk match {
        case Some(id)    => id.point[ConnectionIO]
        case None        => insertTargetRegion(targetRegion)
      }
    } yield pk

    query
  }


  def insertTargetRegion(targetRegion: TargetRegion): ConnectionIO[Int] = {
    val TargetRegion(id, docId, pageId, LTBounds(l, t, w, h) ) = targetRegion
    val (bl, bt, bw, bh) = (dtoi(l), dtoi(t), dtoi(w), dtoi(h))
    println(s"inserting TargetRegion w/int rep:  ${targetRegion}")
    println(s"  ${bl}, $bt, $bw, $bh")
    println(s"  ${l}, $t, $w, $h")

    for {
      docPk <- selectDocumentID(docId)
      pagePk <- selectPage(docPk, pageId)
      trPk <- sql"""
       insert into targetregion (page, bleft, btop, bwidth, bheight, uri)
       values (${pagePk}, $bl, $bt, $bw, $bh, ${targetRegion.uri})
      """.update.withUniqueGeneratedKeys[Int]("targetregion")
    } yield trPk

  }

  def insertTargetRegionImage(targetRegion: TargetRegion, image: Image): ConnectionIO[Unit] = {
    val imagebytes = image.bytes
    for {

      trPk <- ensureTargetRegion(targetRegion)

      _ <- (sql"""
              insert into targetregion_image (image, targetregion)
              values (${imagebytes}, ${trPk})
              """.update.run)
    } yield ()
  }

  def getPageGeometry(docId: String@@DocumentID, pageId: Int@@PageID): Option[PageGeometry]= {
    selectPageGeometry(docId, pageId)
      .transact(xa)
      .unsafePerformSync
  }

  def getPageImageAndGeometry(docId: String@@DocumentID, pageId: Int@@PageID): Option[(Image, PageGeometry)] = {
    val query = for {
      pageImage    <- selectPageImage(docId, pageId)
      pageGeometry <- selectPageGeometry(docId, pageId)
    } yield (pageImage |@| pageGeometry)((_, _))

    query.transact(xa).unsafePerformSync
  }

  def overwriteTargetRegionImage(targetRegion: TargetRegion, image: Image): Unit = {
    val query = for {
      // _  <- putStrLn(s"overwriteTargetRegionImage for ${targetRegion}")
      _  <- deleteTargetRegionImage(targetRegion)
      _  <- insertTargetRegionImage(targetRegion, image)
    } yield ()

    query
      .transact(xa)
      .unsafePerformSync
  }
  def putTargetRegionImage(targetRegion: TargetRegion, image: Image): Unit = {
    val query = for {
      // _           <- putStrLn(s"putTargetRegionImage for ${targetRegion}")
      maybeTrClip <- selectTargetRegionImage(targetRegion)
      clipped     <- maybeTrClip match {
        // case Some(tr) => putStrLn(s"  image exists")
        case None     => insertTargetRegionImage(targetRegion, image)
      }
    } yield ()

    query
      .transact(xa)
      .unsafePerformSync
  }

  import extract.images.ExtractImages
  def getOrCreateTargetRegionImage(targetRegion: TargetRegion): ConnectionIO[Image] = {
    val TargetRegion(id, docId, pageId, bbox) = targetRegion

    val query = for {
      // _         <- putStrLn(s"getOrCreateTargetRegionImage for ${targetRegion}")
      maybeTrClip <- selectTargetRegionImage(targetRegion)
      clipped     <- maybeTrClip match {
        case Some(tr) => tr.point[ConnectionIO]
        case None     => for {
          pageImage <- selectPageImage(docId, pageId)
          pageGeometry <- selectPageGeometry(docId, pageId)
          maybeClippedImage = {
            (pageImage |@| pageGeometry).apply((pg, geom) =>
                ExtractImages.cropTo(pg, bbox, geom)
            )
          }
          clippedImage = maybeClippedImage.getOrElse { sys.error(s"getOrCreateTargetRegionImage: ${targetRegion}") }
          _  <- insertTargetRegionImage(targetRegion, clippedImage)
        } yield clippedImage
      }
    } yield clipped

    query
  }

  def serveImageWithURI(targetRegion: TargetRegion): Image = {
    getOrCreateTargetRegionImage(targetRegion)
      .transact(xa)
      .unsafePerformSync

  }
}