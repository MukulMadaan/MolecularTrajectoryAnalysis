package Angle

import Masking.{CONSTANTS, Mask}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}

class AngleDriver(args: Array[String], spark: SparkSession) {

  Logger.getLogger("org").setLevel(Level.ERROR)

  def getAngle(): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    val inputArr = args(0).split("\\s+")
    val constant = new CONSTANTS()

    constant.set_CRDDIRECTORY(inputArr(0))
    constant.set_TOPOFILE(inputArr(1))
    constant.set_NumberOfCrdFiles(args(1).toInt)

    val masking = new Mask(constant, spark)

    var topoDF: DataFrame = masking.getTopologyDataFrame()
    val crdDF = masking.getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")

    var proteinDataBank = topoDF.join(crdDF, Seq("index"), "left")
    var choice = inputArr(3)
    var angle = new Angle1()

    if (choice == "m") {
      val moleculeDataSet = masking.maskMoleculeByIds(inputArr(4), proteinDataBank).collect()
      var angleOfMolecules = angle.calculateMoleculeAngle(moleculeDataSet, inputArr(4))
      spark.sparkContext.parallelize(angleOfMolecules.split(",")).repartition(1).saveAsTextFile(inputArr(2))
    } else {
      var maskedAtomData = masking.maskAtomByIds(inputArr(4), proteinDataBank).collect()
      var angleOfAtoms = angle.calculateAtomAngle(maskedAtomData, inputArr(4))
      spark.sparkContext.parallelize(angleOfAtoms.split(",")).repartition(1).saveAsTextFile(inputArr(2))
    }
  }
}