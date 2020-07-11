package Dihedral

import java.util.Calendar

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

import scala.collection.mutable.ArrayBuffer

class Functions(objConstant: Masking.CONSTANTS, spark: SparkSession) {

  var objMask = new Masking.Mask(objConstant, spark)

  Logger.getLogger("org").setLevel(Level.ERROR)

  val sc = spark.sparkContext

  def getMaskedDS(path: String): (Array[Row], Int) = {

    import spark.implicits._

    var cal = Calendar.getInstance
    var startTime = cal.getTimeInMillis
    var outputBuffer = new ArrayBuffer[String]

    var topoDF: DataFrame = objMask.getTopologyDataFrame()
    val crdDF = objMask.getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    var atomDataBase = topoDF.join(crdDF, Seq("index"), "left")

    var input = "N CA C @1 801"
    val atomName = input.substring(0, input.indexOf('@'))
    val moleculeId1 = input.substring(input.indexOf('@') + 1)

    val atomList = atomName.split("\\s+").toList
    val moleculeIdList = moleculeId1.trim.split("\\s+").toList

    val x = List.range(moleculeIdList(0).toInt, moleculeIdList(1).toInt + 1)
    val atommaskedDS = atomDataBase.filter($"molId".isin(x: _*))
    val MaskedDS = atommaskedDS.filter($"atomName".isin(atomList: _*)).withColumnRenamed("value[0]", "X").withColumnRenamed("value[1]", "Y").withColumnRenamed("value[2]", "Z")
    MaskedDS.where("frame_no = 1").orderBy("index").show(100)
    println(MaskedDS.count())
    cal = Calendar.getInstance
    var viewTime = cal.getTimeInMillis
    MaskedDS.createTempView("maskedData")

    val mainMaskedDS = spark.sql("select int(index), int(frame_no), atomName, x, y, z,molId from maskedData ORDER BY frame_no,index")
    cal = Calendar.getInstance
    var endTime = cal.getTimeInMillis
    outputBuffer += ("View Time" + (endTime - viewTime))

    outputBuffer += ("Masking Time" + (endTime - startTime))
    val new_rdd = sc.parallelize(outputBuffer)
    new_rdd.repartition(1).saveAsTextFile(path + "//maskingTime")
    (mainMaskedDS.collect(), moleculeIdList(1).toInt * 3)
  }

  def psiAngleCalculation(mainMaskedDS: Array[Row], count: Int, path: String): Unit = {

    var cal = Calendar.getInstance
    var time = cal.getTimeInMillis
    var count1: Int = 1
    var noOfCRDs = objConstant.get_NumberOfCrdFiles()
    var outputBuffer = new ArrayBuffer[String]
    var iterator: Int = 0
    var crd_itr = count * 10

    for (crd_count <- 0 until noOfCRDs) {

      iterator = crd_count * crd_itr

      for (frame_index <- 0 until 10) {

        iterator += frame_index * count
        for (j <- 0 until count - 3 by 3) {

          var thisrow = mainMaskedDS.apply(iterator)
          var (x1, y1, z1): (Float, Float, Float) =
            (thisrow.get(3).toString.toFloat, thisrow.get(4).toString.toFloat, thisrow.get(5).toString.toFloat)

          var thisrow1 = mainMaskedDS.apply(iterator + 1)
          var (x2, y2, z2): (Float, Float, Float) =
            (thisrow1.get(3).toString.toFloat, thisrow1.get(4).toString.toFloat, thisrow1.get(5).toString.toFloat)

          var thisrow3 = mainMaskedDS.apply(iterator + 2)
          var (x3, y3, z3): (Float, Float, Float) =
            (thisrow3.get(3).toString.toFloat, thisrow3.get(4).toString.toFloat, thisrow3.get(5).toString.toFloat)

          var thisrow4 = mainMaskedDS.apply(iterator + 3)
          var (x4, y4, z4): (Float, Float, Float) =
            (thisrow4.get(3).toString.toFloat, thisrow4.get(4).toString.toFloat, thisrow4.get(5).toString.toFloat)

          var anglePsi = DihedralAngle(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4)
          outputBuffer += (count1 + "." + " CRD No: " + crd_count + " Frame No: " + frame_index + " Psi Angle is " + anglePsi)
          println(count1 + "." + "CRD No :" + crd_count + "Frame No" + frame_index + "Psi Angle is " + anglePsi)
          count1 += 1
          iterator += 3
        }
        iterator = 0
      }
    }

    cal = Calendar.getInstance
    var time1 = cal.getTimeInMillis
    time1 = time1 - time
    outputBuffer += ("Time : " + time1)
    val new_rdd = sc.parallelize(outputBuffer)
    new_rdd.repartition(1).saveAsTextFile(path + "//psi")

  }

  def phiAngleCalculation(mainMaskedDS: Array[Row], count: Int, path: String): Unit = {

    var cal = Calendar.getInstance
    var time = cal.getTimeInMillis
    var crdCount = objConstant.get_NumberOfCrdFiles()
    var iterator: Int = 0
    var crd_itr = count * 10
    var outputBuffer = new ArrayBuffer[String]
    var cnt: Int = 1

    for (crd_count <- 0 until crdCount) {

      iterator = crd_count * crd_itr

      for (frame_index <- 0 until 10) {

        iterator += frame_index * count + 2

        for (j <- 2 until count - 3 by 3) {

          var thisrow = mainMaskedDS.apply(iterator)
          var (x1, y1, z1): (Float, Float, Float) = (thisrow.get(3).toString.toFloat, thisrow.get(4).toString.toFloat, thisrow.get(5).toString.toFloat)

          var thisrow1 = mainMaskedDS.apply(iterator + 1)
          var (x2, y2, z2): (Float, Float, Float) = (thisrow1.get(3).toString.toFloat, thisrow1.get(4).toString.toFloat, thisrow1.get(5).toString.toFloat)

          var thisrow3 = mainMaskedDS.apply(iterator + 2)
          var (x3, y3, z3): (Float, Float, Float) = (thisrow3.get(3).toString.toFloat, thisrow3.get(4).toString.toFloat, thisrow3.get(5).toString.toFloat)

          var thisrow4 = mainMaskedDS.apply(iterator + 3)
          var (x4, y4, z4): (Float, Float, Float) = (thisrow4.get(3).toString.toFloat, thisrow4.get(4).toString.toFloat, thisrow4.get(5).toString.toFloat)

          var anglePhi = DihedralAngle(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4)

          outputBuffer += (cnt + "." + " CRD No : " + crd_count + " Frame No: " + frame_index + " Phi Angle is " + anglePhi)
          println(cnt + "." + " CRD No: " + crd_count + " Frame No " + frame_index + " Phi Angle is " + anglePhi)
          cnt = cnt + 1
          iterator += 3
        }
        iterator = 0
      }
    }
    cal = Calendar.getInstance
    var time1 = cal.getTimeInMillis
    time1 = time1 - time
    outputBuffer += ("Time : " + time1)
    val new_rdd = sc.parallelize(outputBuffer)
    new_rdd.repartition(1).saveAsTextFile(path + "//phi")
  }

  def omegaAngleCalculation(mainMaskedDS: Array[Row], count: Int, path: String): Unit = {

    var cal = Calendar.getInstance
    var time = cal.getTimeInMillis
    var noOfCRDs = objConstant.get_NumberOfCrdFiles()
    var iterator: Int = 0
    var crd_itr = count * 10
    var outputBuffer = new ArrayBuffer[String]
    var cnt: Int = 1

    for (crd_count <- 0 until noOfCRDs) {

      iterator = crd_count * crd_itr

      for (frame_index <- 0 until 10) {

        iterator += frame_index * count + 1

        for (j <- 1 until count - 3 by 3) {

          var thisrow = mainMaskedDS.apply(iterator)
          var (x1, y1, z1): (Float, Float, Float) = (thisrow.get(3).toString.toFloat, thisrow.get(4).toString.toFloat, thisrow.get(5).toString.toFloat)

          var thisrow1 = mainMaskedDS.apply(iterator + 1)
          var (x2, y2, z2): (Float, Float, Float) = (thisrow1.get(3).toString.toFloat, thisrow1.get(4).toString.toFloat, thisrow1.get(5).toString.toFloat)

          var thisrow3 = mainMaskedDS.apply(iterator + 2)
          var (x3, y3, z3): (Float, Float, Float) = (thisrow3.get(3).toString.toFloat, thisrow3.get(4).toString.toFloat, thisrow3.get(5).toString.toFloat)

          var thisrow4 = mainMaskedDS.apply(iterator + 3)
          var (x4, y4, z4): (Float, Float, Float) = (thisrow4.get(3).toString.toFloat, thisrow4.get(4).toString.toFloat, thisrow4.get(5).toString.toFloat)

          var angleOmega = DihedralAngle(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4)

          outputBuffer += (cnt + "." + " CRD No: " + crd_count + " Frame No: " + frame_index + " Omega Angle is " + angleOmega)
          println(cnt + "." + " CRD No: " + crd_count + " Frame No: " + frame_index + "Omega Angle is " + angleOmega)
          cnt = cnt + 1
          iterator += 3
        }
        iterator = 0
      }
    }
    cal = Calendar.getInstance
    var time1 = cal.getTimeInMillis
    time1 = time1 - time
    outputBuffer += ("Time :" + time1)
    val new_rdd = sc.parallelize(outputBuffer)
    new_rdd.repartition(1).saveAsTextFile(path + "//omega")
  }

  def Distance3d(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float = {
    var dx: Float = (x2 - x1)
    var dy: Float = (y2 - y1)
    var dz: Float = (z2 - z1)
    var tmp = dx * dx + dy * dy + dz * dz
    math.sqrt(tmp).toFloat
  }

  def VectorSubtract(v1_x: Float, v1_y: Float, v1_z: Float, v2_x: Float, v2_y: Float, v2_z: Float): (Float, Float, Float) = {
    (v1_x - v2_x, v1_y - v2_y, v1_z - v2_z)
  }

  def CrossProduct(v1_x: Float, v1_y: Float, v1_z: Float, v2_x: Float, v2_y: Float, v2_z: Float): (Float, Float, Float) = {
    (v1_y * v2_z - v1_z * v2_y, v1_z * v2_x - v1_x * v2_z, v1_x * v2_y - v1_y * v2_x)
  }

  def DotProduct3d(v1_x: Float, v1_y: Float, v1_z: Float, v2_x: Float, v2_y: Float, v2_z: Float) = {
    (v1_x * v2_x + v1_y * v2_y + v1_z * v2_z)
  }

  def CosAfromDotProduct(aDP: Float, aLine1Len: Float, aLine2Len: Float): (Float) = {
    aDP / (aLine1Len * aLine2Len)
  }

  def RadToDeg(Radians: Float): Float = {
    var result = Radians * (180 / 3.14159)
    result.toFloat
  }

  def Angle(a0_x: Float, a0_y: Float, a0_z: Float, a2_x: Float, a2_y: Float, a2_z: Float, a3_x: Float, a3_y: Float, a3_z: Float): Float = {

    var lenA: Float = Distance3d(a0_x, a0_y, a0_z, a2_x, a2_y, a2_z)
    var lenB: Float = Distance3d(a0_x, a0_y, a0_z, a3_x, a3_y, a3_z)

    var dx = a2_x - a0_x
    var dy = a2_y - a0_y
    var dz = a2_z - a0_z
    var vA_x = dx
    var vA_y = dy
    var vA_z = dz

    dx = a3_x - a0_x
    dy = a3_y - a0_y
    dz = a3_z - a0_z
    var vB_x = dx
    var vB_y = dy
    var vB_z = dz

    var DProd: Float = DotProduct3d(vA_x, vA_y, vA_z, vB_x, vB_y, vB_z)
    var CosA: Float = CosAfromDotProduct(DProd, lenA, lenB)
    var degree: Float = RadToDeg(math.acos(CosA).toFloat)
    degree

  }

  def DihedralAngle(vA_x: Float, vA_y: Float, vA_z: Float, vB_x: Float, vB_y: Float, vB_z: Float, vC_x: Float, vC_y: Float, vC_z: Float, vD_x: Float, vD_y: Float, vD_z: Float): Float = {

    var (x1, y1, z1): (Float, Float, Float) = VectorSubtract(vC_x, vC_y, vC_z, vB_x, vB_y, vB_z)
    var (x2, y2, z2): (Float, Float, Float) = VectorSubtract(vA_x, vA_y, vA_z, vB_x, vB_y, vB_z)
    var (x3, y3, z3): (Float, Float, Float) = VectorSubtract(vD_x, vD_y, vD_z, vC_x, vC_y, vC_z)


    var (dd1_x, dd1_y, dd1_z): (Float, Float, Float) = CrossProduct(x1, y1, z1, x2, y2, z2)
    var (dd3_x, dd3_y, dd3_z): (Float, Float, Float) = CrossProduct(x1, y1, z1, x3, y3, z3)


    var r: Float = Angle(0, 0, 0, dd1_x, dd1_y, dd1_z, dd3_x, dd3_y, dd3_z)
    var (arr_pos1, arr_pos2, arr_pos3): (Float, Float, Float) = CrossProduct(x1, y1, z1, dd1_x, dd1_y, dd1_z)

    if (DotProduct3d(dd3_x, dd3_y, dd3_z, arr_pos1, arr_pos2, arr_pos3) < 0) {
      r = -r
    }
    r

  }
}