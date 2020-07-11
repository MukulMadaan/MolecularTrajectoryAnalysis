package Angle

import org.apache.spark.sql._

class Angle1 {
  /**
   * takes two tuple containing vectors from the center node
   *
   * @param vector1x
   * @param vector1y
   * @param vector1z
   * @param vector2x
   * @param vector2y
   * @param vector2z
   * @return
   */
  def calculateAngle(vector1x: Double, vector1y: Double, vector1z: Double, vector2x: Double, vector2y: Double, vector2z: Double) = {
    // calculating magnitude of vectors
    var vactA = Math.sqrt(vector1x * vector1x + vector1y * vector1y + vector1z * vector1z)
    var vactB = Math.sqrt(vector2x * vector2x + vector2y * vector2y + vector2z * vector2z)
    var vactAB = ((vector1x * vector2x) + (vector1y * vector2y) + (vector1z * vector2z));
    val Angle = (scala.math.acos(vactAB / (vactA * vactB)) * 180) / 3.14159
    Angle.toString
  }

  def calculateCoordinate(firstAtomCoordinates: Row, secondAtomCoordinates: Row, thirdAtomCoordinates: Row) = {
    var x1 = firstAtomCoordinates.get(2).toString.toDouble
    var x2 = secondAtomCoordinates.get(2).toString.toDouble
    var x3 = thirdAtomCoordinates.get(2).toString.toDouble
    var y1 = firstAtomCoordinates.get(3).toString.toDouble
    var y2 = secondAtomCoordinates.get(3).toString.toDouble
    var y3 = thirdAtomCoordinates.get(3).toString.toDouble
    var z1 = firstAtomCoordinates.get(4).toString.toDouble
    var z2 = secondAtomCoordinates.get(4).toString.toDouble
    var z3 = thirdAtomCoordinates.get(4).toString.toDouble
    calculateAngle(x1 - x2, y1 - y2, z1 - z2, x3 - x2, y3 - y2, z3 - z2);
  }

  def calculateMoleculeAngle(moleculeDataSet: Array[Row], inputArr: String): String = {

    var first = inputArr.split(",")(0).toInt
    var second = inputArr.split(",")(1).toInt
    var third = inputArr.split(",")(2).toInt
    var angleOfMolecules = ""
    var no_of_frame = moleculeDataSet.size
    if (second > first && second > third) {
      for (i <- 0 until no_of_frame by 3) {
        angleOfMolecules += calculateCoordinate(moleculeDataSet(i), moleculeDataSet(i + 2), moleculeDataSet(i + 1)) + "\n"
      }
    } else if (second < first && second < third) {
      for (i <- 0 until no_of_frame by 3) {
        angleOfMolecules += calculateCoordinate(moleculeDataSet(i + 1), moleculeDataSet(i), moleculeDataSet(i + 2)) + "\n"
      }
    } else {
      for (i <- 0 until no_of_frame by 3) {
        angleOfMolecules += calculateCoordinate(moleculeDataSet(i), moleculeDataSet(i + 1), moleculeDataSet(i + 2)) + "\n"
      }
    }
    return angleOfMolecules
  }

  def calculateAtomAngle(maskedAtomData: Array[Row], inputArr: String): String = {
    var first = inputArr.split(",")(0).toInt
    var second = inputArr.split(",")(1).toInt
    var third = inputArr.split(",")(2).toInt
    var angleOfAtoms = "";
    var no_of_frames = maskedAtomData.size
    if (second > first && second > third) {
      for (i <- 0 until no_of_frames by 3) {
        angleOfAtoms += calculateCoordinate(maskedAtomData(i), maskedAtomData(i + 2), maskedAtomData(i + 1)) + "\n"
      }
    } else if (second < first && second < third) {
      for (i <- 0 until no_of_frames by 3) {
        angleOfAtoms += calculateCoordinate(maskedAtomData(i + 1), maskedAtomData(i), maskedAtomData(i + 2)) + "\n"
      }
    } else {
      for (i <- 0 until no_of_frames by 3)
        angleOfAtoms += calculateCoordinate(maskedAtomData(i), maskedAtomData(i + 1), maskedAtomData(i + 2)) + "\n"
    }
    return angleOfAtoms
  }
}
