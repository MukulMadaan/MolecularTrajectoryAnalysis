package RMSD

import java.io.File

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable.ArrayBuffer

class Mask(constant: CONSTANTS) {

  Logger.getLogger("org").setLevel(Level.ERROR)
  var coordinateArray = new ArrayBuffer[Float]()
  val spark = SparkSession.builder.appName("MaskModules").master("local").getOrCreate()

  import spark.implicits._

  def getListOfFiles(dir: String): List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  def getTopologyWithWaterDataFrame(): DataFrame = {
    val topologyArray = new ReadFiles(constant).getTopologyFileArray()
    val topologyDataFrame = spark.sparkContext.parallelize(topologyArray.toSeq).map(x => (x.split(",")(0), x.split(",")(1), x.split(",")(2), x.split(",")(3))).toDF("index", "atomName", "molName", "molId")
    topologyDataFrame.repartition($"index")
  }

}