package Masking

import java.io.File

import AutoImaging.AutoImage
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.functions.{floor, lit}
import org.apache.spark.sql.types.{LongType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}
import ucar.nc2._

import scala.collection.mutable.ArrayBuffer

class Mask(constant: CONSTANTS, spark: SparkSession) {

  Logger.getLogger("org").setLevel(Level.ERROR)

  //  val spark = SparkSession.builder.master("local[*]").appName("MaskModules").getOrCreate()

  import spark.implicits._


  /**
   * To get list of CRD files in given directory
   *
   * @param dir
   * @return
   */
  def getListOfFiles(dir: String): List[File] = {
    val files = FileSystem.get(spark.sparkContext.hadoopConfiguration).listStatus(new Path(dir))
    var filesArr = files.map(x => new File("file:///bigdata/bigdata/home" + x.getPath.toString.substring(22))).toList
    filesArr
  }


  /**
   * This is used to get coordinate file DataFrame
   *
   * @return DataFrame
   */
  def getCoordinateDataFrame(): DataFrame = {
    var coordinateArray = new ArrayBuffer[Float]()
    var filepath: String = ""
    val noOfAtoms = constant.get_NonWaterAtoms()
    val noOfAtomPerFrame = constant.get_TotalAtomCount()
    var numberOfCoordinatesPerFile = 0
    var totalCRDFiles = 0
    var totalCoordinates = 0
    val filesList = getListOfFiles(constant.get_crdDirectory())
    totalCRDFiles = constant.get_NumberOfCrdFiles()
    for (i <- 0 until totalCRDFiles) {
      filepath = filesList(i).toString()
      val coordinates = NetcdfFile.open(filepath).getVariables.get(2).read()
      val frameTime = NetcdfFile.open(filepath).getVariables.get(0).read()
      numberOfCoordinatesPerFile = (noOfAtoms * 3) - 1 // coordinates.getSize().toInt - 1
      for (j <- 0 until 10) {
        var coordinatesTempArray = (((j) * (noOfAtomPerFrame * 3) to (((j) * (noOfAtomPerFrame * 3) + numberOfCoordinatesPerFile))).map(x => coordinates.getFloat(x))).toArray[Float]
        coordinateArray = coordinateArray ++ coordinatesTempArray
      }
    }

    // all CRD files coordinates collected

    totalCoordinates = noOfAtoms * 3 * 10 * totalCRDFiles
    val crdRDD = (0 to totalCoordinates - 1 by 3).map(x => coordinateArray.slice(x, x + 3)).map(x => (x(0), x(1), x(2))).toDF("value[0]", "value[1]", "value[2]")


    val coordinateDataFrame = spark.sqlContext.createDataFrame(
      crdRDD.rdd.zipWithIndex.map {
        case (row, index) => Row.fromSeq(row.toSeq :+ index)
      },
      StructType(crdRDD.schema.fields :+ StructField("crdIndex", LongType, false)))
      .withColumn("frame_no", lit(floor(($"crdIndex" / noOfAtoms))))
      .withColumnRenamed("value[0]", "x")
      .withColumnRenamed("value[1]", "y")
      .withColumnRenamed("value[2]", "z")
    coordinateDataFrame.withColumn("crdIndex", lit(floor(($"crdIndex" % noOfAtoms))))
      .repartition($"crdIndex").persist()
  }

  def getCoordinatesWithWaterDataFrame(): DataFrame = {
    var coordinateArray = new ArrayBuffer[Float]()
    var filepath: String = ""
    val noOfAtoms = constant.get_TotalAtomCount()
    var numberOfCoordinatesPerFile = 0
    var totalCRDFiles = 0
    var totalCoordinates = 0
    val filesList = getListOfFiles(constant.get_crdDirectory())
    totalCRDFiles = constant.get_NumberOfCrdFiles()

    for (i <- 0 until totalCRDFiles) {
      filepath = filesList(i).toString()
      val coordinates = NetcdfFile.open(filepath).getVariables.get(2).read()
      numberOfCoordinatesPerFile = coordinates.getSize().toInt - 1
      var coordinatesTempArray = ((0 to numberOfCoordinatesPerFile).map(x => coordinates.getFloat(x))).toArray[Float]
      coordinateArray = coordinateArray ++ coordinatesTempArray
    }
    totalCoordinates = numberOfCoordinatesPerFile * 1 //totalCRDFiles
    val crdRDD = (0 to totalCoordinates - 1 by 3).map(x => coordinateArray.slice(x, x + 3)).map(x => (x(0), x(1), x(2))).toDF("value[0]", "value[1]", "value[2]")


    val coordinateDataFrame = spark.sqlContext.createDataFrame(
      crdRDD.rdd.zipWithIndex.map {
        case (row, index) => Row.fromSeq(row.toSeq :+ index)
      },
      StructType(crdRDD.schema.fields :+ StructField("crdIndex", LongType, false)))
      .withColumn("frame_no", lit(floor(($"crdIndex" / noOfAtoms))))
      .withColumnRenamed("value[0]", "x")
      .withColumnRenamed("value[1]", "y")
      .withColumnRenamed("value[2]", "z")
    coordinateDataFrame.withColumn("crdIndex", lit(floor(($"crdIndex" % noOfAtoms)))).repartition($"crdIndex")
  }

  /**
   * This is used to get topology DataFrame
   *
   * @return DataFrame
   */
  def getTopologyDataFrame(): DataFrame = {
    val topologyArray = new ReadFiles(constant).getTopologyFileArray()
    val topologyDataFrame = spark.sparkContext.parallelize(topologyArray.toSeq)
      .map(x => (x.split(",")(0), x.split(",")(1), x.split(",")(2), x.split(",")(3)))
      .toDF("index", "atomName", "molName", "molId")
    val topoWithoutWater = topologyDataFrame.filter($"molName".notEqual("WAT"))
    topoWithoutWater.repartition($"index").persist()
  }

  def getTopologyWithWaterDataFrame(): DataFrame = {
    val topologyArray = new ReadFiles(constant).getTopologyFileArray()
    val topologyDataFrame = spark.sparkContext.parallelize(topologyArray.toSeq).map(x => (x.split(",")(0), x.split(",")(1), x.split(",")(2), x.split(",")(3))).toDF("index", "atomName", "molName", "molId")
    topologyDataFrame.repartition($"index")
  }


  /**
   * This is used to get protein Data Bank DataFrame
   *
   * @return DataFrame
   */
  def getProteinDataBank(): DataFrame = {
    var topologyDataFrame: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val proteinDataBank = topologyDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used for masking atoms by moleculeId
   *
   * @param input
   * @return DataFrame
   */
  def getMaskedAtomMoleaculeIdDataFrame(input: String): DataFrame = {
    val topologyDataFrame: DataFrame = getTopologyDataFrame()
    val crdDataFrame = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val moleaculeList = input.substring(0, input.indexOf('@')).split(",").toList
    val atomList = input.substring(input.indexOf('@') + 1).split(",").toList
    val maskedDataFrame = topologyDataFrame.filter($"molId".isin(moleaculeList: _*)).filter($"atomName".isin(atomList: _*))
    val proteinDataBank = maskedDataFrame.join(crdDataFrame, Seq("index"), "inner")
    proteinDataBank
  }


  /**
   * This is used to perform masking by atom name
   *
   * @param input
   * @return DataFrame
   */
  def getMaskedAtomDataFrame(input: String): DataFrame = {
    val topologyDataFrame: DataFrame = getTopologyDataFrame()
    val crdDataFrame = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val atomList = input.split(",").toList
    val maskedDataFrame = topologyDataFrame.filter($"atomName".isin(atomList: _*))
    var proteinDataBank = maskedDataFrame.join(crdDataFrame, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used to perform masking by Molecule name
   *
   * @param input
   * @return DataFrame
   */
  def getMaskedMoleculeDataFrame(input: String): DataFrame = {
    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val moleaculeList = input.split(",").toList
    val maskedDataFrame = topoDF.filter($"molName".isin(moleaculeList: _*))
    var proteinDataBank = maskedDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used to perform masking by AtomId
   *
   * @param input
   * @return
   */
  def getMaskedByAtomId(input: String): DataFrame = {
    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val atomIdList = input.split(",").toList
    val maskedDataFrame = topoDF.filter($"index".isin(atomIdList: _*))
    var proteinDataBank = maskedDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used to perform masking by MoleculeId
   *
   * @param input
   * @return
   */
  def getMaskedByMoleculeId(input: String): DataFrame = {

    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val moleculeIdList = input.split(",").toList
    val maskedDataFrame = topoDF.filter($"molId".isin(moleculeIdList: _*))
    var proteinDataBank = maskedDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used for masking molecules in range
   *
   * @param input
   * @return dataFrame
   */
  def getMaskedByMoleculeRange(input: String): DataFrame = {
    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val moleculeList = input.split("-")
    val start = moleculeList(0).toInt
    val end = moleculeList(1).toInt
    val moleculeIds = (start to end).toList
    val maskedDataFrame = topoDF.filter($"molId".isin(moleculeIds: _*))
    var proteinDataBank = maskedDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used for masking atoms in range
   *
   * @param input
   * @return dataFrame
   */
  def getMaskedByAtomRange(input: String): DataFrame = {
    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val atomList = input.split("-")
    val start = atomList(0).toInt
    val end = atomList(1).toInt
    val atomIds = (start to end).toList
    val maskedDataFrame = topoDF.filter($"index".isin(atomIds: _*))
    var proteinDataBank = maskedDataFrame.join(crdDF, Seq("index"), "inner")
    proteinDataBank
  }

  /**
   * This is used for masking frames in range
   *
   * @param input
   * @return dataFrame
   */
  def getMaskedByFrameRange(input: String): DataFrame = {
    var topoDF: DataFrame = getTopologyDataFrame()
    val crdDF = getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val frameList = input.split("-")
    val start = frameList(0).toInt
    val end = frameList(1).toInt
    val frameIds = (start to end).toList
    val maskedDataFrame = crdDF.filter($"frame_no".isin(frameIds: _*))
    var proteinDataBank = topoDF.join(maskedDataFrame, Seq("index"), "inner")
    proteinDataBank
  }

  def maskMoleculeByIds(moleculesId: String, PDB: DataFrame): Dataset[Row] = {
    PDB.createTempView("PDBMoleculeWise")
    spark.sql("select int(frame_no), int(molId),double(x),double(y),double(z) " +
      "from PDBMoleculeWise where molId in (" + moleculesId + ") and atomName = 'CA'")
      .orderBy("frame_no")
  }

  def maskAtomByIds(atomsId: String, PDB: DataFrame): Dataset[Row] = {
    PDB.createTempView("PDBAtomWise")
    spark.sql("select int(frame_no),int(index),double(x),double(y),double(z) from PDBAtomWise" +
      " where index in (" + atomsId + ")").orderBy("frame_no")
  }

  def getAutoImagedDataFrame(): DataFrame = {
    var coordinateArray = new ArrayBuffer[Float]()
    println("CRD PATH " + constant.get_crdDirectory())
    val autoImage = new AutoImage(constant.get_crdDirectory(), constant.get_topoFile(), "", "", spark)
    var filepath: String = ""
    val noOfAtoms = constant.get_NonWaterAtoms()
    val noOfAtomPerFrame = constant.get_TotalAtomCount()
    var numberOfCoordinatesPerFile = 0
    var totalCRDFiles = 0
    var totalCoordinates = 0
    val filesList = getListOfFiles(constant.get_crdDirectory())
    println("FIRST FILE IN LIST " + filesList(0).getName)
    totalCRDFiles = constant.get_NumberOfCrdFiles()

    for (i <- 0 until totalCRDFiles) {
      filepath = filesList(i).toString()
      val vs = NetcdfFile.open(filepath).getVariables
      val coordinates = vs.get(2).read()
      val boxDimensions = vs.get(5).read()
      numberOfCoordinatesPerFile = (noOfAtoms * 3) - 1 // coordinates.getSize().toInt - 1
      var coordinatesTempArray = autoImage.autoImageCoordinates("nonWater", coordinates, boxDimensions, autoImage.anchorResidueCenter)
      coordinateArray = coordinateArray ++ coordinatesTempArray

    }
    // all CRD files coordinated collected
    totalCoordinates = noOfAtoms * 3 * 10 * totalCRDFiles
    val crdRDD = (0 to totalCoordinates - 1 by 3).map(x => coordinateArray.slice(x, x + 3)).map(x => (x(0), x(1), x(2))).toDF("value[0]", "value[1]", "value[2]")


    val coordinateDataFrame = spark.sqlContext.createDataFrame(
      crdRDD.rdd.zipWithIndex.map {
        case (row, index) => Row.fromSeq(row.toSeq :+ index)
      },
      StructType(crdRDD.schema.fields :+ StructField("crdIndex", LongType, false)))
      .withColumn("frame_no", lit(floor(($"crdIndex" / noOfAtoms))))
      .withColumnRenamed("value[0]", "x")
      .withColumnRenamed("value[1]", "y")
      .withColumnRenamed("value[2]", "z")
    coordinateDataFrame.withColumn("crdIndex", lit(floor(($"crdIndex" % noOfAtoms))))
      .repartition($"crdIndex")
  }

}