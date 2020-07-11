package RMSD

import scala.collection.mutable.ArrayBuffer

class CONSTANTS {

  private var CRDFILE = ""
  private var TOPOFILE = ""
  private var NUMBER_OF_CRDFILES: Int = 1
  private var CRDDIRECTORY = ""
  private val OUTPUTFILE = ""
  private val ATOM_START_DELIM = "%FLAG ATOM_NAME                                                                 "
  private val ATOM_END_DELIM = "%FLAG CHARGE                                                                    "
  private val ATOM_SIZE = 4
  private val MOLECULE_START_DELIM = "%FLAG RESIDUE_LABEL                                                             "
  private val MOLECULE_END_DELIM = "%FLAG RESIDUE_POINTER                                                           "
  private val MOLECULE_SIZE = 4
  private val MOLECULE_CHAIN_START_DELIM = "%FLAG RESIDUE_POINTER                                                           "
  private val MOLECULE_CHAIN_END_DELIM = "%FLAG BOND_FORCE_CONSTANT                                                       "
  private val MOLECULE_CHAIN_SIZE = 8
  private val TER_START_DELIM = "%FLAG ATOMS_PER_MOLECULE                                                        "
  private val TER_END_DELIM = "%FLAG BOX_DIMENSIONS                                                            "
  private val TER_SIZE = 8
  private val COORDINATE_INDEX = 2
  private val SKIP_LINE = 2
  var coordinateArray = new ArrayBuffer[Float]()


  def set_NumberOfCrdFiles(value: Int): Unit = {
    this.NUMBER_OF_CRDFILES = value
  }

  def get_NumberOfCrdFiles(): Int = {
    return this.NUMBER_OF_CRDFILES;
  }

  def set_CRDFILE(path: String): Unit = {
    this.CRDFILE = path
  }

  def set_TOPOFILE(path: String): Unit = {
    this.TOPOFILE = path
  }

  def set_CRDDIRECTORY(path: String): Unit = {
    this.CRDDIRECTORY = path
  }

  def get_coordinateIndex(): Int = {
    return COORDINATE_INDEX
  }

  def get_crdFile(): String = {
    return CRDFILE
  }

  def get_topoFile(): String = {
    return TOPOFILE
  }

  def get_outputFile(): String = {
    return OUTPUTFILE
  }

  def get_atomStartDelim(): String = {
    return ATOM_START_DELIM
  }

  def get_atomEndDelim(): String = {
    return ATOM_END_DELIM
  }

  def get_atomSize(): Int = {
    return ATOM_SIZE
  }

  def get_moleculeStartDelim(): String = {
    return MOLECULE_START_DELIM
  }

  def get_moleculeEndDelim(): String = {
    return MOLECULE_END_DELIM
  }

  def get_moleculeSize(): Int = {
    return MOLECULE_SIZE
  }

  def get_moleculeChainStartDelim(): String = {
    return MOLECULE_CHAIN_START_DELIM
  }

  def get_moleculeChainEndDelim(): String = {
    return MOLECULE_CHAIN_END_DELIM
  }

  def get_moleculeChainSize(): Int = {
    return MOLECULE_CHAIN_SIZE
  }

  def get_terStartDelim(): String = {
    return TER_START_DELIM
  }

  def get_terEndDelim(): String = {
    return TER_END_DELIM
  }

  def get_terSize(): Int = {
    return TER_SIZE
  }

  def get_skipLine(): Int = {
    return SKIP_LINE
  }

  def get_crdDirectory(): String = {
    return CRDDIRECTORY
  }

}
