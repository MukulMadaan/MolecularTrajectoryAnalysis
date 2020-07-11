package Distance

import Masking.{CONSTANTS, Mask}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}

class DistanceDriver(args: Array[String], spark: SparkSession) {

  Logger.getLogger("org").setLevel(Level.ERROR)

  def getDistance(): Unit = {

    val inputArr = args(0).split("\\s+")
    val constant = new CONSTANTS()

    constant.set_CRDDIRECTORY(inputArr(0))
    constant.set_TOPOFILE(inputArr(1))
    constant.set_NumberOfCrdFiles(args(1).toInt)

    val masking = new Mask(constant, spark)

    var topoDF: DataFrame = masking.getTopologyDataFrame()
    val crdDF = masking.getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")

    var proteinDataBank = topoDF.join(crdDF, Seq("index"), "left")
    var choice = inputArr(2)
    var a_m_list = inputArr(3)
    val d = new Distance(spark)

    if (choice == "m") {
      var maskedMoleculesDataFrame: Dataset[Row] = masking.maskMoleculeByIds(a_m_list, proteinDataBank)
      maskedMoleculesDataFrame.show()
      spark.sql("select frame_no, ")
      var maskedMoleculesData: Array[Row] = maskedMoleculesDataFrame.collect()
      d.calculateDistanceBetweenMolecules(maskedMoleculesData).rdd.saveAsTextFile(inputArr(4))
      d.calculateDistanceBetweenMolecules(maskedMoleculesData)
        .repartition(1)
        .write.mode("overwrite")
        .format("csv")
        .save(inputArr(4) + "/a")

    } else {
      var maskedAtomsDataFrame = masking.maskAtomByIds(a_m_list, proteinDataBank)
      maskedAtomsDataFrame.show()
      var maskedAtomsData: Array[Row] = maskedAtomsDataFrame.collect()
      d.calculateDistanceBetweenAtoms(maskedAtomsData).rdd.saveAsTextFile(inputArr(4))
      d.calculateDistanceBetweenAtoms(maskedAtomsData)
        .repartition(1)
        .write.mode("overwrite")
        .format("csv")
        .save(inputArr(4) + "/a")
    }
  }
}