package RMSD

import javax.vecmath.Point3d
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.Row
import org.dia.core.SciSparkContext
import org.openscience.cdk.exception.CDKException
import org.openscience.cdk.geometry.alignment.KabschAlignment
import org.openscience.cdk.interfaces.IAtom
import org.openscience.cdk.{Atom, AtomContainer}
//import org.dia.algorithms.mcs.MCSOps

//import org.dia.urlgenerators.RandomDatesGenerator
object Rmsd {

  case class crdschme(x: Double, y: Double, z: Double)


  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)
    val sc = new SciSparkContext(args(0), "MTA")
    val spark = new sql.SparkSession.Builder().getOrCreate
    import spark.implicits._

    val constant = new CONSTANTS
    constant.set_TOPOFILE(args(7))

    val topologyDataFrame = {

      val topologyArray = new ReadFiles(constant).getTopologyFileArray()
      val topologyDataFrame = spark.sparkContext.parallelize(topologyArray.toSeq).map(x => (x.split(",")(0), x.split(",")(1), x.split(",")(2), x.split(",")(3), x.split(",")(4))).toDF("index", "atomName", "molName", "molId", "atomSymbol")
      val topoWithoutWater = topologyDataFrame.filter($"molName".notEqual("WAT"))
      topoWithoutWater.repartition($"index").persist()

    }
    var indexArray = List[Row]()
    var symbolArr = List[Row]()

    //*** To check whether Residuewise or Atomwise *** res-> Residuewise  and atom-> Atom wise ***
    if (args(8) == "res") {
      val moleculeIds = (args(3).toInt to args(4).toInt).toList
      var atomFilter = args(5).toString
      var maskedDataFrame = topologyDataFrame.filter($"molId".isin(moleculeIds: _*)).filter($"atomName".isin(atomFilter))
      indexArray = maskedDataFrame.select("index").collect().toList
      symbolArr = maskedDataFrame.select("atomSymbol").collect().toList
    }
    else if (args(8) == "atom") {
      val atomIds = (args(3).toInt to args(4).toInt).toList
      //val atomFilter = args(5).toString
      val maskedDataFrame = topologyDataFrame.filter($"index".isin(atomIds: _*))
      indexArray = maskedDataFrame.select("index").collect().toList
      symbolArr = maskedDataFrame.select("atomSymbol").collect().toList
    }

    // mass Map set Masses of Atom Symbol
    val mass = Map('H' -> 1.00800000E+00, 'C' -> 1.20100000E+01, 'O' -> 1.60000000E+01, 'N' -> 1.40100000E+01, 'S' -> 3.1972100000000E+01)


    /* makeContainer takes frame coordinates and create AtomContainer
       by setting Atoms mass,Coordinates,Sysmbol
       @param cordArr
              It contains coordinates of one frame as Array
       @return AtomContainer

     */
    def makeContainer(cordArr: Array[Double]): AtomContainer = {

      var length = symbolArr.length
      var atomContainerObj = new AtomContainer()
      var atomArray = new Array[IAtom](length)

      for (i <- 0 to length - 1) {

        var atomObj = new Atom()
        var atomCoordinate = (indexArray(i)(0).toString.toInt) * 3
        var x = cordArr(atomCoordinate).toFloat
        var y = cordArr(atomCoordinate + 1).toFloat
        var z = cordArr(atomCoordinate + 2).toFloat

        var coordinate = new Point3d(x, y, z)
        atomObj.setPoint3d(coordinate)

        var massSymbol = symbolArr(i)(0).toString
        var masss = (mass(massSymbol(0)))
        atomObj.setExactMass(masss)
        atomObj.setSymbol(massSymbol.toString)
        atomArray(i) = atomObj
      }
      atomContainerObj.setAtoms(atomArray)
      atomContainerObj
    }


    /* findRMSD takes two AtomContainer and finds RMSD value between them and Returns RMSD value.
       RMSD calculation done using CDK Library's Kabsh Implementation.
       @param ref
              Referance frame AtomContainer
       @param next
              Next Frame AtomContainer
       @return
              RMSD value in double
     */
    def findRMSD(ref: AtomContainer, next: AtomContainer): Double = {
      val kabsch = new KabschAlignment(ref, next)
      try {

        kabsch.align()
        val refCentre = new Point3d(kabsch.getCenterOfMass)

        for (i <- 0 until ref.getAtomCount) {
          val atom = new Atom(ref.getAtom(i));
          atom.getPoint3d.setX(atom.getPoint3d.getX - refCentre.x)
          atom.getPoint3d.setY(atom.getPoint3d.getY - refCentre.y)
          atom.getPoint3d.setZ(atom.getPoint3d.getZ - refCentre.z)
        }
        kabsch.rotateAtomContainer(next)
      }

      catch {
        case x: CDKException => {
          println(x)
        }
      }
      kabsch.getRMSD
    }


    val noOfAtoms = 175105
    val noOfResidues = 13897
    var numberOfCoordinatesPerFile = 0
    var totalCRDFiles = 0
    var totalCoordinates = noOfAtoms * 3 * 10
    var startTime = System.currentTimeMillis()

    /*
       frameRDD contains coordinates of one frame as one element

     */
    val dfsfilesRDD = sc.netcdfDFSFiles(args(1), List("coordinates"), args(2).toInt)
    var arryRDD = dfsfilesRDD.map(x => (x.data.toStream.toList, x.metaData))
    var frameRDD = arryRDD.flatMap(crdArr => {
      var frameNumberSTR = crdArr._2.get("SOURCE").toString.split("/")
      var frameNumber = frameNumberSTR(frameNumberSTR.length - 1).split("\\.")(1).split("_")(0).toInt
      (0 to totalCoordinates - 1 by 175105 * 3).map(x => (crdArr._1.slice(x, x + 175105 * 3), ((x / (noOfAtoms * 3)) + 1) + frameNumber))
    }
    )

    /*  containerRDD has Atomcontainer as one element of frame by passing frameRDD element to makeContainer().
        refContainer is AtomContainer of referance Frame
     */

    var containerRDD = frameRDD.map(x => (makeContainer(x._1.toArray), x._2))
    var refContainer = containerRDD.take(1)(0)._1


    /*
        rmsdRDD contains RMSD of calculating frame as element
     */

    var rmsdRDD = containerRDD.map(x => (findRMSD(refContainer, x._1), x._2))
    rmsdRDD.coalesce(1).saveAsTextFile(args(6))

  }

}
