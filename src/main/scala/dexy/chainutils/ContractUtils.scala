package dexy.chainutils

trait ContractUtils {
  def defaultSubstitutionMap: Map[String, String]

  // totally inefficient substitution method, but ok for our contracts
  def substitute(contract: String, substitutionMap: Map[String, String] = defaultSubstitutionMap): String = {
    substitutionMap.foldLeft(contract) { case (c, (k, v)) =>
      c.replace("$" + k, v)
    }
  }

  def readContract(path: String,
                   substitutionMap: Map[String, String] = defaultSubstitutionMap,
                   additionalFields: Map[String, String] = Map.empty): String = {
    val substitutionMapComplete = if (additionalFields.isEmpty) {
      substitutionMap
    } else {
      substitutionMap ++ additionalFields
    }
    substitute(scala.io.Source.fromFile("contracts/" + path, "utf-8").getLines.mkString("\n"), substitutionMapComplete)
  }

  def readContract(path: String,
                   additionalField: (String, String)): String = {
    readContract(path, defaultSubstitutionMap, Map(additionalField))
  }

}
