package dexy

import dexy.DexySpec.nftDictionary

trait ContractUtils {
  def substitutionMap: Map[String, String]

  // totally inefficient substitution method, but ok for our contracts
  def substitute(contract: String): String = {
    substitutionMap.foldLeft(contract){case (c, (k,v)) =>
      c.replace("$"+k, v)
    }
  }

  def readContract(path: String) = {
    substitute(scala.io.Source.fromFile("contracts/" + path, "utf-8").getLines.mkString("\n"))
  }

}
