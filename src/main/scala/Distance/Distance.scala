package Distance

import org.apache.spark.sql.{DataFrame, Row, SparkSession}

/**
 * Distance module calculates the distance between the center of mass of atoms in the given molecules, between atoms in each and every frame.
 * The center of mass or the centroid of a molecule is equivalent to the carbon-alpha atom in that molecule.
 */
class Distance(spark: SparkSession) {

  import spark.implicits._

  /**
   * This method calculates the distance between 2 3-d points
   *
   * @param point1 - Row containing ,frame_no, id and x,y,z coordinates of first molecule/atom
   * @param point2 - Row containing ,frame_no, id and x,y,z coordinates of second molecule/atom
   * @return Distance of the given two atoms/molecules in every frame
   */
  def calculateDistance(point1: Row, point2: Row): Double = {
    var (x1, y1, z1): (Double, Double, Double) =
      (point1.get(2).toString.toDouble, point1.get(3).toString.toDouble, point1.get(4).toString.toDouble)

    var (x2, y2, z2): (Double, Double, Double) =
      (point2.get(2).toString.toDouble, point2.get(3).toString.toDouble, point2.get(4).toString.toDouble)

    return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow((y2 - y1), 2) + Math.pow((z2 - z1), 2))
  }

  /**
   * This methods calculates the distance between selected molecules in all the frames
   *
   * @param moleculeData Data containing the coordinates of the selected molecules in each frame
   * @return Dataframe containing framewise distances between selected atoms or molecules
   */
  def calculateDistanceBetweenMolecules(moleculeData: Array[Row]): DataFrame = {
    val no_of_frames: Int = moleculeData.size
    var arrayOfTuples: Array[(Int, Double)] = new Array[(Int, Double)](no_of_frames / 2)
    for (i <- 0 until no_of_frames by 2) {
      var distance: Double = calculateDistance(moleculeData.apply(i), moleculeData.apply(i + 1))
      val t = (moleculeData.apply(i).get(0).toString.toInt, distance)
      arrayOfTuples(i / 2) = t
    }
    arrayOfTuples.toSeq.toDF("frame_no", "distance")
  }

  /**
   * This methods calculates the distance between selected atoms in all the frames
   *
   * @param atomData Data containing the coordinates of the selected molecules in each frame
   * @return Dataframe containing framewise distances between selected atoms or molecules
   */
  def calculateDistanceBetweenAtoms(atomData: Array[Row]): DataFrame = {
    val no_of_frames: Int = atomData.size
    var arrayOfTuples: Array[(Int, Double)] = new Array[(Int, Double)](no_of_frames / 2)
    for (i <- 0 until no_of_frames by 2) {
      var distance: Double = calculateDistance(atomData.apply(i), atomData.apply(i + 1))
      val t = (atomData.apply(i).get(0).toString.toInt, distance)
      arrayOfTuples(i / 2) = t
    }
    arrayOfTuples.toSeq.toDF("frame_no", "distance")
  }
}
