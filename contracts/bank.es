{ 
  // This box: (dexyUSD emission box)
  //   tokens(0): emissionNFT identifying the box
  //   tokens(1): dexyUSD tokens to be emitted
  
  val selfOutIndex = getVar[Int](0).get
  
  val oraclePoolNFT = fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box
  val swappingNFT = fromBase64("Fho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify swapping box for future use
  
  val validEmission = {
    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle-pool (v1 and v2) box containing rate in R4
  
    val validOP = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
  
    val oraclePoolRate = oraclePoolBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  
    val selfOut = OUTPUTS(selfOutIndex)
  
    val validSelfOut = selfOut.tokens(0) == SELF.tokens(0) && // emissionNFT and quantity preserved
                     selfOut.propositionBytes == SELF.propositionBytes && // script preserved
                     selfOut.tokens(1)._1 == SELF.tokens(1)._1 && // dexyUSD tokenId preserved
                     selfOut.value > SELF.value // can only purchase dexyUSD, not sell it
                     
    val inTokens = SELF.tokens(1)._2
    val outTokens = selfOut.tokens(1)._2
  
    val deltaErgs = selfOut.value - SELF.value // deltaErgs must be (+)ve because ergs must increase
  
    val deltaTokens = inTokens - outTokens // outTokens must be < inTokens (see below)
  
    val validDelta = deltaErgs >= deltaTokens * oraclePoolRate // deltaTokens must be (+)ve, since both deltaErgs and oraclePoolRate are (+)ve
  
    validOP && validSelfOut && validDelta
  }
  
  val validTopping = INPUTS(0).tokens(0)._1 == swappingNFT
  
  sigmaProp(validEmission || validTopping)
}
