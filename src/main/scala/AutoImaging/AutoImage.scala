package AutoImaging

import java.io.File

import Masking.CONSTANTS
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.SparkSession
import ucar.nc2.NetcdfFile

import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.control.Breaks.{break, breakable}

/**
 * Provide functions to Auto Image molecules in each frame
 *
 * Center each anchor molecule at box center
 * Image remaining molecules inside the box
 */
class AutoImage(crdPath: String, topologyPath: String, pdbOutput: String, timeOutput: String, spark: SparkSession) extends Serializable {

  var constants = new CONSTANTS()
  var crdFilesList = List[File]()

  val timeTopo1 = System.currentTimeMillis()
  val parmtop = Source.fromFile(topologyPath)
  var itr = parmtop.getLines()
  crdFilesList = getListOfFiles(crdPath)

  var startTime1 = System.currentTimeMillis()

  val fsource = Source.fromFile(topologyPath)
  val total = fsource.getLines().toArray

  var atomArray = ArrayBuffer[String]()
  var residueArray = ArrayBuffer[String]()
  var residuePointerArray = ArrayBuffer[String]()
  var terPointerArray = ArrayBuffer[String]()

  var moleculePointer = 0
  var residuePointer = 1
  var terPointer = 0
  var frameNumber = 1
  var count = 0
  var numberOfAtoms = atomArray.length


  var fCount = 0
  val ouputDirPath = pdbOutput

  var atomStartIndex = total.indexOf("%FLAG ATOM_NAME                                                                 ") + 2
  var atomEndIndex = total.indexOf("%FLAG CHARGE                                                                    ")
  while (atomStartIndex < atomEndIndex) {
    var line = total(atomStartIndex)
    //Split the line and add each element to atomArray
    atomArray ++= total(atomStartIndex).split("(?<=\\G.{" + 4 + "})").toList
    atomStartIndex += 1
  }


  var molStartIndex = total.indexOf("%FLAG RESIDUE_LABEL                                                             ") + 2
  var molEndIndex = total.indexOf("%FLAG RESIDUE_POINTER                                                           ")
  while (molStartIndex < molEndIndex) {
    //Split line and add each element to residueArray
    residueArray ++= total(molStartIndex).split("\\s+").toList
    molStartIndex += 1
  }

  residueArray = residueArray.filter(!_.trim.equals("FLAG"))


  var resPointerStartIndex = total.indexOf("%FLAG RESIDUE_POINTER                                                           ") + 2
  var resPointerEndIndex = total.indexOf("%FLAG BOND_FORCE_CONSTANT                                                       ")
  while (resPointerStartIndex < resPointerEndIndex) {
    residuePointerArray ++= total(resPointerStartIndex).split("(?<=\\G.{" + 8 + "})").toList
    resPointerStartIndex += 1
  }

  residuePointerArray = residuePointerArray :+ String.valueOf(Integer.MAX_VALUE)

  var terPointerStartIndex = total.indexOf("%FLAG ATOMS_PER_MOLECULE                                                        ") + 2
  var terPointerEndIndex = total.indexOf("%FLAG BOX_DIMENSIONS                                                            ")
  while (terPointerStartIndex < terPointerEndIndex) {
    terPointerArray ++= total(terPointerStartIndex).split("(?<=\\G.{" + 8 + "})").toList
    terPointerStartIndex += 1
  }

  val timeTopo2 = System.currentTimeMillis()
  val topoTime = timeTopo2 - timeTopo1
  var cordsReadTime: Long = 0
  var formatTime: Long = 0
  var writeTime: Long = 0
  val fileNamesList = crdFilesList.sorted
  val anchorCRD = NetcdfFile.open(fileNamesList(0).toString).getVariables.get(constants.get_coordinateIndex()).read()
  println("Anchor File " + fileNamesList(0).toString)
  val anchorResidueCenter = centerAnchorMolecule(terPointerArray, 1, anchorCRD)
  numberOfAtoms = atomArray.length


  def pdbAutoImaging(): Unit = {

    for (ele <- crdFilesList) {
      fCount += 1
      val crdFile = ele.toString
      val ncdf = NetcdfFile.open(crdFile)
      val vs = ncdf.getVariables
      var crdtime1 = System.currentTimeMillis()
      var coords = vs.get(constants.get_coordinateIndex()).read()
      var boxDimension = vs.get(5).read()
      var autoImagedCoords = autoImageCoordinates("water", coords, boxDimension, anchorResidueCenter)
      var crdtime2 = System.currentTimeMillis()
      var timformat1 = System.currentTimeMillis()
      println("AUTO IMAGED CORDS SIZE " + autoImagedCoords.size)
      var pdbArray = generatePDB(autoImagedCoords, fCount)
      println("FINAL SIZE " + pdbArray.length)
      var tim2format2 = System.currentTimeMillis()
      spark.sparkContext.parallelize(pdbArray, 10).saveAsTextFile(ouputDirPath + ele.getName)

      moleculePointer = 0
      residuePointer = 0
      terPointer = 0
      frameNumber = 1
      count = 0
      numberOfAtoms = atomArray.length

      var endtime = System.currentTimeMillis()

      cordsReadTime += crdtime2 - crdtime1
      formatTime += tim2format2 - timformat1
      writeTime += endtime - tim2format2
    }
    spark.sparkContext.parallelize(Array("CRD read time " + (cordsReadTime / 1000).toString, "TOPO read time " + (topoTime / 1000).toString, "STRING format time " + (formatTime / 1000).toString, "FILE write time " + (writeTime / 1000).toString), 1).saveAsTextFile(timeOutput)
  }


  def getListOfFiles(dir: String): List[File] = {
    val files = FileSystem.get(spark.sparkContext.hadoopConfiguration).listStatus(new Path(dir))
    var filesArr = files.map(x => new File("file:///bigdata/bigdata/home" + x.getPath.toString.substring(22))).toList
    filesArr
  }

  def autoImageCoordinates(crdFlag: String, coordinates: ucar.ma2.Array, boxDims: ucar.ma2.Array, anchorResidueCenter: Array[Float]): ArrayBuffer[Float] = {
    val autoCrds = new ArrayBuffer[Float]()
    val totalCrdSize = coordinates.getSize.toInt
    for (frame <- 1 until 11) {
      var startIndex = (frame - 1) * numberOfAtoms * 3
      val boxCenter = findBoxCenter(frame, boxDims)
      val residueCenter = centerAnchorMolecule(terPointerArray, frame, coordinates)

      var deltaX = math.round((boxCenter(0)) - residueCenter(0))
      var deltaY = math.round((boxCenter(1)) - residueCenter(1))
      var deltaZ = math.round((boxCenter(2)) - residueCenter(2))

      var anchorDeltaX = math.floor(residueCenter(0) - anchorResidueCenter(0))
      var anchorDeltaY = math.floor(residueCenter(1) - anchorResidueCenter(1))
      var anchorDeltaZ = math.floor(residueCenter(2) - anchorResidueCenter(2))

      var endIndex = 0
      if (crdFlag == "water") {
        endIndex = startIndex + totalCrdSize / 10
      } else {
        var residueCounter = 0
        breakable {
          for (i <- 0 to terPointerArray.length) {
            if (terPointerArray(i).trim.toInt == 3) {
              break
            } else {
              //println(terArray(i))
              residueCounter = residueCounter + terPointerArray(i).trim.toInt
            }
          }
        }
        endIndex = startIndex + (residueCounter * 3)
      }

      for (i <- startIndex until endIndex by 3) {
        autoCrds += coordinates.getFloat(i) + (deltaX + anchorDeltaX).toFloat
        autoCrds += coordinates.getFloat(i + 1) + (deltaY + anchorDeltaY).toFloat
        autoCrds += coordinates.getFloat(i + 2) + (deltaZ + anchorDeltaZ).toFloat
      }
    }
    autoCrds
  }

  def generatePDB(coordinate: ArrayBuffer[Float], fCount: Int): ArrayBuffer[String] = {

    var pdbArray = new ArrayBuffer[String]

    for (frame <- 1 until 11) {

      moleculePointer = 0
      residuePointer = 1
      terPointer = 0
      frameNumber = 1
      count = 0
      numberOfAtoms = atomArray.length

      var globalSum = Integer.parseInt(terPointerArray(terPointer).trim)

      for (i <- 0 until atomArray.length) {

        if (i == globalSum) {

          terPointer += 1
          globalSum += Integer.parseInt(terPointerArray(terPointer).trim)

          var (atomID: Int, moleculeID: Int) = getID(i)

          val lineTer = "%1$-4s  %2$5d %3$-4s %4$3s  %5$4d ".format("TER", atomID, "", residueArray(moleculePointer).trim, moleculeID)

          pdbArray += lineTer
        }


        if (i + 1 < Integer.parseInt(residuePointerArray(residuePointer).trim)) {

          var startIndex = getCoordinatesIndex(frame, i + 1, numberOfAtoms)

          var (atomID: Int, moleculeID: Int) = getID(i)

          var atomName = atomArray(i).trim.substring(0, 1);

          var line: String = formatLine(i, startIndex, atomID, moleculeID, atomName, coordinate)
          pdbArray += line

          count += 1
          frameNumber = count % numberOfAtoms

        } else if (i + 1 == Integer.parseInt(residuePointerArray(residuePointer).trim)) {
          moleculePointer += 1
          residuePointer += 1
          var startIndex = getCoordinatesIndex(frame, i + 1, numberOfAtoms)

          var (atomID: Int, moleculeID: Int) = getID(i)

          val atomName = atomArray(i).trim.substring(0, 1)

          var line: String = formatLine(i, startIndex, atomID, moleculeID, atomName, coordinate)

          count += 1
          frameNumber = count % numberOfAtoms
          pdbArray += line
          //out.println(line)
        }
      }

      val lineTer = "%1$-4s  %2$5d %3$-4s %4$3s  %5$4d ".format(
        "TER", (atomArray.length % 100000) + 1, "", residueArray(moleculePointer).trim, (residueArray.length) % 10000)

      pdbArray += lineTer

      pdbArray += "END"

    }

    pdbArray
  }


  private def getID(i: Int) = {
    var atomID = (i + 1) % 100000
    var moleculeID = (moleculePointer + 1) % 10000
    (atomID, moleculeID)
  }

  /** findBoxCenter() finds the center of the box
   *
   * @param frame Frame number to read box dimensions from CRD file
   * @return Box center coordinates
   */
  def findBoxCenter(frame: Int, boxDims: ucar.ma2.Array): Array[Float] = {
    //val boxDims = vs.get(5).read()
    var start = (frame - 1) * 3
    var boxDimension = new Array[Float](3)
    boxDimension(0) = boxDims.getFloat(start) / 2
    boxDimension(1) = boxDims.getFloat(start + 1) / 2
    boxDimension(2) = boxDims.getFloat(start + 2) / 2

    boxDimension
  }

  /** centerAnchorMolecule() does the centering of the anchor molecule
   *
   * @param terArray Get the centroid of residue
   * @param frame    Frame number
   */
  def centerAnchorMolecule(terArray: ArrayBuffer[String], frame: Int, cords: ucar.ma2.Array): Array[Float] = {
    var residueCounter = 0
    breakable {
      for (i <- 0 to terArray.length) {
        if (terArray(i).trim.toInt == 1 || terArray(i).trim.toInt == 3) {
          break
        } else {
          residueCounter = residueCounter + terArray(i).trim.toInt
        }
      }
    }
    residueCounter = residueCounter * 3
    var center = new Array[Float](3)

    var index = (frame - 1) * numberOfAtoms

    for (i <- index to (index + residueCounter) by 3) {
      center(0) += cords.getFloat(i)
      center(1) += cords.getFloat(i + 1)
      center(2) += cords.getFloat(i + 2)
    }

    center(0) = center(0) / (residueCounter / 3)
    center(1) = center(1) / (residueCounter / 3)
    center(2) = center(2) / (residueCounter / 3)

    center
  }

  /** Images the molecules which goes out of the box dimensions after the centering
   *
   * @param i          Atom index
   * @param startIndex Frame start index
   * @param moleculeID Molecule ID
   * @param atomName   Atom name
   */
  def formatLine(i: Int, startIndex: Int, atomID: Int, moleculeID: Int, atomName: String, coordinates: ArrayBuffer[Float]) = {

    var line = "%1$4s  %2$5d %3$-4s %4$3s  %5$4d    %6$8.3f%7$8.3f%8$8.3f%9$6.2f%10$6.2f          %11$2S".format(
      "ATOM",
      atomID,
      atomArray(i).trim,
      residueArray(moleculePointer).trim,
      moleculeID,
      coordinates(startIndex),
      coordinates(startIndex + 1),
      coordinates(startIndex + 2),
      1.00,
      0.00,
      atomName
    );
    line
  }

  def getCoordinatesIndex(frameNumber: Int, atomNumber: Int, numberOfAtoms: Int): Int = {
    ((frameNumber - 1) * numberOfAtoms * 3) + (atomNumber - 1) * 3
  }
}
