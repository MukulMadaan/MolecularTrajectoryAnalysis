package Angle

//package Angle

import org.apache.spark.sql._

class AngleModule {
  /**
   * calculateAngle():  Calculates Angle
   *
   * @param vector1x x component of first Vector
   * @param vector1y y component of first Vector
   * @param vector1z z component of first Vector
   * @param vector2x x component of second Vector
   * @param vector2y y component of second Vector
   * @param vector2z z component of second Vector
   * @return angle between the Vectors
   */
  def calculateAngle(vector1x: Double, vector1y: Double, vector1z: Double, vector2x: Double, vector2y: Double, vector2z: Double) = {
    // calculating magnitude of vectors
    var vactA = Math.sqrt(vector1x * vector1x + vector1y * vector1y + vector1z * vector1z)
    var vactB = Math.sqrt(vector2x * vector2x + vector2y * vector2y + vector2z * vector2z)
    var vactAB = ((vector1x * vector2x) + (vector1y * vector2y) + (vector1z * vector2z));
    val Angle = (scala.math.acos(vactAB / (vactA * vactB)) * 180) / 3.14159
    Angle.toString
  }

  /**
   * calculateCoordinate():- calculate co-ordinates
   *
   * @param firstAtomCoordinates  atom information of the First Atom
   * @param secondAtomCoordinates atom information of the Second Atom
   * @param thirdAtomCoordinates  atom information of the Third Atom
   * @return X,Y,Z components of two vectors
   */

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

  /**
   * calculateMoleculeAngle():- calculates angle for molecules
   *
   * @param moleculeDataSet masked data of Molecules
   * @param inputArr        List of user input
   * @return calculated angle
   */

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

  /**
   * calculateAtomAngle():- calculates angle among atoms
   *
   * @param maskedAtomData masked data of Atoms
   * @param inputArr       List of user input
   * @return calculated angle
   */

  def calculateAtomAngle(maskedAtomData: Array[Row], inputArr: String): String = {
    var first = inputArr.split(",")(0).toInt
    var second = inputArr.split(",")(1).toInt
    var third = inputArr.split(",")(2).toInt
    var angleOfAtoms = ""
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