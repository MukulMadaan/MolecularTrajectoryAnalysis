package AverageStructure

import Masking.{CONSTANTS, Mask}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

class AverageStructure(args: Array[String], spark: SparkSession) extends Serializable {

  def getAverageStructure(): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    val inputArr = args(0).split("\\s+")
    val constant = new CONSTANTS()

    constant.set_CRDDIRECTORY(inputArr(0))
    constant.set_TOPOFILE(inputArr(1))
    constant.set_NumberOfCrdFiles(args(1).toInt)

    val masking = new Mask(constant, spark)
    val crdFile = masking.getAutoImagedDataFrame().withColumnRenamed("crdIndex", "index")
    println("CRD DATA SIZE " + crdFile.count())
    val topoFile = masking.getTopologyDataFrame().withColumn("Atom", lit("ATOM")).withColumn("Atom1", substring(col("atomName"), 1, 1))

    val time = System.nanoTime()

    val avgCoordinates = crdFile.groupBy("index").avg("x", "y", "z")

    println((System.nanoTime() - time) / 1e9d)

    val avgstruct = avgCoordinates.select(avgCoordinates("index"), bround(avgCoordinates("avg(x)"), 3).as("avg(x)"), bround(avgCoordinates("avg(y)"), 3).as("avg(y)"), bround(avgCoordinates("avg(z)"), 3).as("avg(z)")) //.select(bround(a("avg(y)"),3)).select(bround(a("avg(z)"),3)).show()
    val avgDF = avgstruct.join(topoFile.withColumn("index", topoFile("index").cast(sql.types.IntegerType)), Seq("index"), "right")
    val avgPDB = avgDF.withColumn("index", avgDF.col("index") + 1).select("Atom", "index", "atomName", "molName", "molId", "avg(x)", "avg(y)", "avg(z)", "Atom1")


    val output = avgPDB.sort("index")

    output.coalesce(1).rdd.map(x => format(x.toString()
      .replace("[", "")
      .replace("]", "")))
      .saveAsTextFile(inputArr(2))

    println((System.nanoTime() - time) / 1e9d)

    spark.sparkContext.parallelize(Array("Average Time " + ((System.nanoTime() - time) / 1e9d)
      .toString, "CRD Files taken " + constant.get_NumberOfCrdFiles()), 1)
      .saveAsTextFile(inputArr(3))


  }

  def format(str: String): String = {
    var colArray = str.split(",")
    var line = "%1$4s  %2$5d %3$-4s %4$3s  %5$4d    %6$8.3f%7$8.3f%8$8.3f          %9$2S".format(
      colArray(0).trim,
      colArray(1).trim.toInt,
      colArray(2).trim,
      colArray(3).trim,
      colArray(4).trim.toInt,
      colArray(5).trim.toFloat,
      colArray(6).trim.toFloat,
      colArray(7).trim.toFloat,
      colArray(8).trim
    )

    line
  }

}
