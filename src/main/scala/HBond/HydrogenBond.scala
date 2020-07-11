package HBond

import Masking.{CONSTANTS, Mask}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, lit, udf, _}

class HydrogenBond(inputArgs: Array[String], spark: SparkSession) {
  def distance(x: Double, y: Double, z: Double, x1: Double, y1: Double, z1: Double): Double = {
    val a = scala.math.pow(x1 - x, 2)
    val b = scala.math.pow(y1 - y, 2)
    val c = scala.math.pow(z1 - z, 2)
    scala.math.sqrt(a + b + c)
  }

  def angle(x: Double, y: Double, z: Double, x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double = {
    scala.math.acos((scala.math.pow(distance(x, y, z, x1, y1, z1), 2) + scala.math.pow(distance(x, y, z, x2, y2, z2), 2) - scala.math.pow(distance(x1, y1, z1, x2, y2, z2), 2)) / (2 * distance(x, y, z, x1, y1, z1) * distance(x, y, z, x2, y2, z2))).toDegrees
  }

  def grid(x: Double, y: Double, z: Double) = {
    (scala.math.floor(x / 3.5) + scala.math.floor(y / 3.5) * 33 + scala.math.floor(z / 3.5) * 33 * 33).toInt
  }

  def gridn(a: Int) = {
    List[Int](a, a - 1, a + 1, a - 33, a - 33 - 1, a - 33 + 1, a + 33, a + 33 - 1, a + 33 + 1, a - 1089, a - 1 - 1089, a + 1 - 1089, a - 33 - 1089, a - 33 - 1 - 1089, a - 33 + 1 - 1089, a + 33 - 1089, a + 33 - 1 - 1089, a + 33 + 1 - 1089, a + 1089, a - 1 + 1089, a + 1 + 1089, a - 33 + 1089, a - 33 - 1 + 1089, a - 33 + 1 + 1089, a + 33 + 1089, a + 33 - 1 + 1089, a + 33 + 1 + 1089)
  }


  def processHBond(): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)
    val constant = new CONSTANTS()
    val masking = new Mask(constant, spark)

    //input
    val args = inputArgs(0).split("\\s+")
    val userDist = args(3).toDouble
    val userAngle = args(4).toDouble
    constant.set_CRDDIRECTORY(args(0))
    constant.set_TOPOFILE(args(2))
    constant.set_NumberOfCrdFiles(args(1).toInt)

    val n = constant.get_NumberOfCrdFiles() * 10
    val t1 = System.nanoTime()
    val crdFile = masking.getCoordinateDataFrame().withColumnRenamed("crdIndex", "index")
    val topoFile = masking.getTopologyDataFrame()
    val t = crdFile.join(broadcast(topoFile), Seq("index"), "left")

    val tq = t.withColumn("Atom", substring(col("atomName"), 1, 1))
      .withColumn("index", t("index").cast(sql.types.IntegerType))

    val p1 = tq.repartition(col("frame_no"))

    p1.persist().take(1)
    val t3 = p1.filter(col("frame_no").equalTo("0"))
    val d6 = t3.filter(col("molName").notEqual("PRO"))
    d6.persist().take(1)
    val w = Window.orderBy("index")

    val hydrogen = d6.withColumn("lag", lag(col("Atom"), 1).over(w))
      .withColumn("dindex", lag(col("index"), 1).over(w))
      .filter(col("Atom").equalTo("H"))
      .filter(col("lag").isin("N", "O"))

    val hydrogen1 = d6.withColumn("lag", lag(col("Atom"), 2).over(w))
      .withColumn("dindex", lag(col("index"), 2).over(w))
      .filter(col("Atom").equalTo("H")).filter(col("lag").isin("N"))
      .filter(col("atomName").endsWith("2"))

    val hydrogen2 = d6.withColumn("lag", lag(col("Atom"), 3).over(w))
      .withColumn("dindex", lag(col("index"), 3).over(w))
      .filter(col("Atom")
        .equalTo("H"))
      .filter(col("lag").isin("N"))
      .filter(col("atomName").endsWith("3"))

    val hydrogen3 = d6.filter(col("molName").equalTo("U5"))
      .filter(col("atomName").isin("HO5'", "O5'"))
      .withColumn("lag", lead(col("Atom"), 1).over(w))
      .withColumn("dindex", lead(col("index"), 1).over(w))
      .filter(col("lag").isin("N", "O"))

    val hydrogen4 = hydrogen.union(hydrogen1)
      .union(hydrogen2)
      .union(hydrogen3)
      .select("index", "dindex")

    hydrogen4.persist().take(1)

    println((System.nanoTime() - t1) / 1e9d)

    val x = List.range(0, n).par

    x.foreach { a =>
      val t2 = System.nanoTime()
      val grd = udf[Int, Double, Double, Double](grid)
      val grdlis = udf[List[Int], Int](gridn)

      val t4 = p1.filter(col("frame_no").equalTo(a.toString))
        .filter(col("Atom").isin("N", "O", "H"))

      val acceptor = t4.filter(col("Atom").isin("N", "O"))

      val t5 = acceptor.withColumnRenamed("molName", "AccpmolName")
        .withColumnRenamed("Atom", "Accplastcol")
        .withColumnRenamed("atomName", "AccpAtomName")
        .withColumnRenamed("molId", "Accpindex")
        .withColumnRenamed("x", "x2")
        .withColumnRenamed("y", "y2")
        .withColumnRenamed("z", "z2")
        .withColumnRenamed("frame_no", "af")
        .withColumn("Grid_No", grd(col("x2"), col("y2"), col("z2")))

      val donor = acceptor.withColumnRenamed("molName", "dmolName")
        .withColumnRenamed("Atom", "dlastcol")
        .withColumnRenamed("atomName", "dAtomName")
        .withColumnRenamed("molId", "dmolId")
        .withColumnRenamed("index", "dindex")
        .withColumnRenamed("x", "x1")
        .withColumnRenamed("y", "y1")
        .withColumnRenamed("z", "z1")
        .withColumnRenamed("frame_no", "df")

      val h5 = t4.join(broadcast(hydrogen4), Seq("index"))

      val h6 = h5.join(broadcast(donor), Seq("dindex"))
        .withColumn("hGrid_No", grd(col("x"), col("y"), col("z")))

      val h7 = h6.withColumn("Gridn", grdlis(col("hGrid_No")))

      val h8 = h7.withColumn("Grid_No", explode(h7("Gridn")))
      val merge = h8.join(broadcast(t5), Seq("Grid_No"), "inner")

      val dist = udf[Double, Double, Double, Double, Double, Double, Double](distance)

      val ang = udf[Double, Double, Double, Double, Double, Double, Double, Double, Double, Double](angle)

      val m1 = merge.withColumn("distance", dist(col("x1"),
        col("y1"), col("z1"), col("x2"), col("y2"), col("z2")))
        .filter(col("distance") <= userDist)
        .filter(col("distance") >= 1.5)

      val m2 = m1.withColumn("angle", ang(col("x"),
        col("y"), col("z"), col("x2"),
        col("y2"), col("z2"), col("x1"),
        col("y1"), col("z1")))
        .filter(col("angle") > userAngle)

      val m3 = m2.withColumn("hlist", concat(col("AccpMolName"),
        lit("_"), col("Accpindex"),
        lit("@"), col("AccpAtomName"),
        lit("-"), col("molName"),
        lit("_"), col("molId"),
        lit("@"), col("datomname")))

      val m4 = m3.select("hlist", "distance", "angle", "frame_no")

      m4.repartition(1).write.mode("overwrite").format("csv").save(args(5) + "/a" + a)

      println((System.nanoTime() - t2) / 1e9d + "end " + a)
    }
    println((System.nanoTime() - t1) / 1e9d)
  }
}