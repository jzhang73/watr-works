package edu.umass.cs.iesl.watr
package spindex

import watrmarks._

import geometry._

import GeometryImplicits._

import scalaz.Tree
import scala.collection.mutable

import watrmarks.{StandardLabels => LB}

import TypeTags._

// TODO move and/or delete this
case class BioNode(
  component: Component,
  pins: mutable.Set[BioPin] =  mutable.Set()
)

sealed trait Component {
  def id: Int@@ComponentID

  def mpageIndex: MultiPageIndex
  def vtrace = mpageIndex.vtrace

  def roleLabel: Label

  lazy val pageId = mpageIndex.getPageForComponent(this)

  def getDocumentID() = mpageIndex.getDocumentID()

  def getPageGeometry(): PageGeometry = {
    mpageIndex.getPageGeometry(pageId)
  }

  def queryAtoms(): Seq[AtomicComponent] = {
    queryInside(LB.PageAtom).map(_.asInstanceOf[AtomicComponent])
  }

  def queryInside(role: Label): Seq[Component] = {
    mpageIndex.getChildren(this, role) match {
      case Some(children) => children
      case None =>
        val pinfo = mpageIndex.getPageIndex(mpageIndex.getPageForComponent(this))
        val bounds = this.targetRegion.bbox
        pinfo.componentIndex
          .queryForContained(bounds)
          .filter(_.roleLabel == role)
    }
  }
  def groupChildren(withLabel: Label, newLabel: Label)(
    groupf: (Component, Component, Int) => Boolean,
    onCreate: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent]

  def groupAtomsIf(
    groupf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onGrouped: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent]

  def splitAtomsIf(
    splitf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onSplit: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent]


  def targetRegions: Seq[TargetRegion]

  // TODO target region only makes sense for some connected components
  def targetRegion: TargetRegion = {
    targetRegions.headOption
      .map(tr => TargetRegion(RegionID(0), tr.docId, tr.pageId, bounds))
      .getOrElse {  sys.error("no target region found in Component}") }
  }

  def chars: String


  // TODO: this should really return a Seq[Option[Component]] to signify existance vs. empty children
  def getChildren(l: Label): Seq[Component] = {
    mpageIndex.getChildren(this, l).getOrElse {Seq()}
  }

  def hasChildren(l: Label): Boolean = {
    mpageIndex.getChildren(this, l).isDefined
  }

  def getDescendants(l: Label): Seq[Component] = {
    getChildren(l)
      .flatMap(ch => ch +: ch.getDescendants(l))
  }

  def setChildren(l: Label, children: Seq[Component]): Unit = {
    mpageIndex.setChildrenWithLabel(this, l, children.map(_.id))
  }

  def connectChildren(l: Label, sortf: Option[((Component)=>Double)]): Unit = {
    val sub = queryInside(l)
    val sorted = sortf
      .map(sub.sortBy(_))
      .getOrElse(sub)
      .map(_.id)

    mpageIndex.setChildrenWithLabel(this, l, sorted)
  }

  // TODO: This is redundant w/targetregion
  def bounds: LTBounds

  def orientation: Double = 0.0d // placeholder until this is implemented for real

  def addLabel(l: Label): Component = {
    // vtrace.trace("addLabel" withTrace link(showComponent(this), showLabel(l)))
    mpageIndex.addLabel(this, l)
  }

  def removeLabel(l: Label): Component = {
    // vtrace.trace("removeLabel())" withTrace link(showComponent(this), showLabel(l)))
    mpageIndex.removeLabel(this, l)
  }

  def getLabels(): Set[Label] = {
    mpageIndex.getLabels(this)
  }

  def getLabel(l: Label): Option[Label] = {
    getLabels().filter(_ == l).headOption
  }


  def addChild(label: Label, c: Component): Unit = {
    setChildren(label, getChildren(label) :+ c)
  }

  def toRoleTree(roles: Label*): Tree[Component] = {
    roles.map(getChildren(_))
      .filterNot(_.isEmpty)
      .headOption
      .map(c => Tree.Node(this,
        c.map(_.toRoleTree(roles:_*)).toStream
      ))
      .getOrElse (Tree.Leaf(this))
  }

}



case class RegionComponent(
  id: Int@@ComponentID,
  override val roleLabel: Label,
  var region: TargetRegion,
  override val mpageIndex: MultiPageIndex
) extends Component {

  def cloneAndNest(l: Label): RegionComponent = {
    val clone = cloneAs(l)
    this.setChildren(l, Seq(clone))
    clone
  }

  private def initCloneAs(l: Label): RegionComponent = {
    copy(
      id = mpageIndex.componentIdGen.nextId,
      roleLabel = l,
      region = region.copy(
        id = mpageIndex.regionIdGen.nextId
      )
    )
  }

  def cloneAs(l: Label): RegionComponent = {
    val atoms = queryAtoms()
    val clone = initCloneAs(l)
    mpageIndex.addComponent(clone)
    clone.setChildren(LB.PageAtom, atoms)
    clone
  }

  import utils.SlicingAndDicing._

  private def cbounds(cs: Seq[Component]) = {
    cs.map(_.bounds)
      .reduce(_ union _)
  }

  private def initRegionFromAtoms(atoms: Seq[AtomicComponent]): RegionComponent = {
    val initRegion = initCloneAs(roleLabel)
    val newRegion = initRegion.copy(
      region = initRegion.targetRegion.copy(
        bbox = cbounds(atoms)))

    newRegion.setChildren(LB.PageAtom, atoms)
    mpageIndex.addComponent(newRegion)
    newRegion
  }

  private def initRegion(newLabel: Label, children: Seq[Component]): RegionComponent = {
    val initRegion = initCloneAs(newLabel)
    val newRegion = initRegion.copy(
      region = initRegion.targetRegion.copy(
        bbox = cbounds(children)))

    newRegion.setChildren(newLabel, children)
    mpageIndex.addComponent(newRegion)
    newRegion
  }

  def groupChildren(withLabel: Label, newLabel: Label)(
    groupf: (Component, Component, Int) => Boolean,
    onCreate: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = {

    val oldChildren = getChildren(withLabel)
    // Reset children to Nil
    setChildren(withLabel, Seq())

    oldChildren
      .groupByPairsWithIndex(groupf)
      .zipWithIndex
      .map({case (childGrp, regionIndex) =>
        val newRegion = initRegion(newLabel, childGrp)
        addChild(newLabel, newRegion)
        onCreate(newRegion, regionIndex)
        newRegion
      })
  }

  def groupAtomsIf(
    groupf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onRegionCreate: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = {

    queryAtoms()
      .groupByPairsWithIndex(groupf)
      .zipWithIndex
      .map({case (atoms, regionIndex) =>
        val newRegion = initRegionFromAtoms(atoms)
        onRegionCreate(newRegion, regionIndex)
        newRegion
      })
  }

  def splitAtomsIf(
    splitf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onRegionCreate: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = {

    queryAtoms()
      .splitOnPairsWithIndex(splitf)
      .zipWithIndex
      .map({case (atoms, regionIndex) =>
        val newRegion = initRegionFromAtoms(atoms)
        onRegionCreate(newRegion, regionIndex)
        newRegion
      })
  }

  def targetRegions: Seq[TargetRegion] = Seq(region)

  def bounds: LTBounds = region.bbox

  def chars: String = {
    queryAtoms().map(_.char).mkString
  }

  override def toString(): String = {
    val lls = getLabels.mkString(",")
    s"<${roleLabel.key}.${id} ${region}${lls}>"
  }
}


case class AtomicComponent(
  id: Int@@ComponentID,
  pageAtom: PageAtom,
  override val mpageIndex: MultiPageIndex
) extends Component {

  def splitAtomsIf(
    splitf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onSplit: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = { Seq() }

  def groupAtomsIf(
    groupf: (AtomicComponent, AtomicComponent, Int) => Boolean,
    onGrouped: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = {
    val newRegion = mpageIndex.createRegionComponent(pageAtom.targetRegion, LB.NullLabel) // FIXME <- nulllabel????
    onGrouped(newRegion, 0)
    Seq(newRegion)
  }

  def groupChildren(withLabel: Label, newLabel: Label)(
    groupf: (Component, Component, Int) => Boolean,
    onCreate: (RegionComponent, Int) => Unit = ((_, _) => ())
  ): Seq[RegionComponent] = { Seq() }



  def roleLabel: Label = LB.PageAtom

  def targetRegions: Seq[TargetRegion] = Seq(pageAtom.targetRegion)

  def char = pageAtom match {
    case rg: CharAtom => rg.char.toString
    case rg: ImgAtom => ""
  }

  val bounds = pageAtom.targetRegion.bbox

  def chars: String = char

  override def toString(): String = {
    val lls = getLabels.mkString(",")
    s"<`${chars}`${id} ${pageAtom.targetRegion}$lls>"
  }
}
