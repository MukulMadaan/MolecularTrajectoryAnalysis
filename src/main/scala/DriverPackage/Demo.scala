package DriverPackage

import Angle.AngleDriver
import AutoImaging.AutoImage
import AverageStructure.AverageStructure
import Dihedral.DihedralDriver
import Distance.DistanceDriver
import HBond.HydrogenBond
import Masking.{CONSTANTS, ReadFiles}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession

object Demo {
  def main(args: Array[String]): Unit = {
    Logger.getLogger("org").setLevel(Level.ERROR)

    val spark = SparkSession.builder.appName("Integrated_Module").master("yarn").getOrCreate()


    val function = args(2)

    val readFiles = new ReadFiles(new CONSTANTS)
    readFiles.setAtomConstants()

    function match {
      case "Distance" => val distanceDriver = new DistanceDriver(args, spark)
        distanceDriver.getDistance()
        println("Distance Done")

      case "Dihedral" => val dihedralDriver = new DihedralDriver(args, spark)
        dihedralDriver.getDihedralAngle()
        println("Dihedral Done")

      case "Angle" => val angleDriver = new AngleDriver(args, spark)
        angleDriver.getAngle()
        println("Angle Done")

      case "HBond" => val hBondDriver = new HydrogenBond(args, spark)
        hBondDriver.processHBond()
        println("HBond Done")

      case "AverageStructure" => val averageStructure = new AverageStructure(args, spark)
        averageStructure.getAverageStructure()
        println("Average Structure Done")

      case "GeneratePDB" => val argArray = args(0).split(" ")
        val autoImagedPDB= new AutoImage(argArray(0), argArray(1), argArray(2), argArray(3), spark)
        autoImagedPDB.pdbAutoImaging()
        println("AutoImaged PDB Done")
    }
  }
}
