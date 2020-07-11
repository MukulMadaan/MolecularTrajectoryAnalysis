package Dihedral

import org.apache.log4j._
import org.apache.spark.sql._

class DihedralDriver(inputArgs: Array[String], spark: SparkSession) {

  def getDihedralAngle(): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    var objConstant = new Masking.CONSTANTS()

    val args = inputArgs(0).split("\\s+")
    objConstant.set_CRDDIRECTORY(args(0))
    objConstant.set_TOPOFILE(args(1))
    objConstant.set_NumberOfCrdFiles(inputArgs(1).toInt)

    var objFunc = new Functions(objConstant, spark)

    val (mainMaskedDS: Array[Row], count: Int) = objFunc.getMaskedDS(args(2))

    objFunc.phiAngleCalculation(mainMaskedDS, count, args(2))

    objFunc.psiAngleCalculation(mainMaskedDS, count, args(2))

    objFunc.omegaAngleCalculation(mainMaskedDS, count, args(2))

  }
}
