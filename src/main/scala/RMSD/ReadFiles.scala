package RMSD

import scala.language.implicitConversions
//import scala.util.parsing.json._

import scala.collection.mutable.ArrayBuffer
import scala.io.Source


class ReadFiles(constants: CONSTANTS) {

  var moleculePointer = 0
  var startTime1 = System.currentTimeMillis()

  def getTopologyFileArray(): ArrayBuffer[String] = {

    val fsource = Source.fromFile(constants.get_topoFile())
    val topoFileArray = fsource.getLines().toArray
    fsource.close()
    var atomArray = ArrayBuffer[String]()

    var atomStartIndex = topoFileArray.indexOf(constants.get_atomStartDelim()) + constants.get_skipLine()
    var atomEndIndex = topoFileArray.indexOf(constants.get_atomEndDelim())
    while (atomStartIndex < atomEndIndex) {
      var line = topoFileArray(atomStartIndex)
      //Split the line and add each element to atomArray
      atomArray ++= topoFileArray(atomStartIndex).split("(?<=\\G.{" + constants.get_atomSize() + "})").toList
      atomStartIndex += 1
    }

    var residueArray = ArrayBuffer[String]()

    var molStartIndex = topoFileArray.indexOf(constants.get_moleculeStartDelim()) + constants.get_skipLine()
    var molEndIndex = topoFileArray.indexOf(constants.get_moleculeEndDelim())
    while (molStartIndex < molEndIndex) {
      //Split line and add each element to residueArray
      residueArray ++= topoFileArray(molStartIndex).split("\\s+").toList
      molStartIndex += 1
    }

    residueArray = residueArray.filter(!_.trim.equals("FLAG"))

    var residuePointerArray = ArrayBuffer[String]()

    var resPointerStartIndex = topoFileArray.indexOf(constants.get_moleculeChainStartDelim()) + constants.get_skipLine()
    var resPointerEndIndex = topoFileArray.indexOf(constants.get_moleculeChainEndDelim())
    while (resPointerStartIndex < resPointerEndIndex) {
      residuePointerArray ++= topoFileArray(resPointerStartIndex).split("(?<=\\G.{" + constants.get_moleculeChainSize() + "})").toList
      resPointerStartIndex += 1
    }

    residuePointerArray = residuePointerArray :+ String.valueOf(Integer.MAX_VALUE)

    var terPointerArray = ArrayBuffer[String]()
    var terPointerStartIndex = topoFileArray.indexOf(constants.get_terStartDelim()) + constants.get_skipLine()
    var terPointerEndIndex = topoFileArray.indexOf(constants.get_terEndDelim())
    while (terPointerStartIndex < terPointerEndIndex) {
      terPointerArray ++= topoFileArray(terPointerStartIndex).split("(?<=\\G.{" + constants.get_terSize() + "})").toList
      terPointerStartIndex += 1
    }


    (System.currentTimeMillis() - startTime1) / 1000


    var residuePointer = 1
    var terPointer = 0
    var frameNumber = 1
    var count = 0
    var numberOfAtoms = 175105


    var topoArray = new ArrayBuffer[String]()

    for (frame <- 1 until 2) {

      var fileName = constants.get_outputFile().concat("_" + frame + ".pdb")

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

        }


        if (i + 1 < Integer.parseInt(residuePointerArray(residuePointer).trim)) {


          var (atomID: Int, moleculeID: Int) = getID(i)

          var atomName = atomArray(i).trim.substring(0, 1);

          count += 1
          frameNumber = count % numberOfAtoms


          var sb = new StringBuilder()
          sb.append(i + ",")
          sb.append(atomArray(i).trim + ",")
          sb.append(residueArray(moleculePointer).trim + ",")
          sb.append(moleculeID + ",")
          sb.append(atomArray(i).charAt(0) + ",")

          topoArray += sb.toString()

        } else if (i + 1 == Integer.parseInt(residuePointerArray(residuePointer).trim)) {
          moleculePointer += 1
          residuePointer += 1


          var (atomID: Int, moleculeID: Int) = getID(i)

          val atomName = atomArray(i).trim.substring(0, 1)


          count += 1
          frameNumber = count % numberOfAtoms

          var sb = new StringBuilder()
          sb.append(i + ",")
          sb.append(atomArray(i).trim + ",")
          sb.append(residueArray(moleculePointer).trim + ",")
          sb.append(moleculeID + ",")
          sb.append(atomArray(i).charAt(0) + ",")

          topoArray += sb.toString()


        }
      }
    }

    topoArray
  }

  private def getID(i: Int) = {
    var atomID = (i + 1) % 100000
    var moleculeID = (moleculePointer + 1) % 10000
    (atomID, moleculeID)
  }

}