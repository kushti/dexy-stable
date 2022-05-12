{
  // Tracking box
  //   R4: Crossing Counter of LP box
  //   tokens(0): TrackingNFT 
  
  val thresholdPercent = 90 // 90% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
  val errorMargin = 3 // number of blocks where tracking error is allowed
  
  val lpNFT = fromBase64("Nho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify LP box for future use
  val oraclePoolNFT = fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box
  
  val lpBox = CONTEXT.dataInputs(0)
  val oraclePoolBox = CONTEXT.dataInputs(1)
  
  val validLpBox = lpBox.tokens(0)._1 == lpNFT
  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
  
  val tokenY    = lpBox.tokens(2)
  
  val reservesX = lpBox.value
  val reservesY = tokenY._2
  
  val lpRateXY  = reservesX / reservesY  // we can assume that reservesY > 0 (since at least one token must exist)
  
  val oraclePoolRateXY = oraclePoolBox.R4[Long].get
   
  val crossCounter = lpBox.R5[Int].get // stores how many times LP rate has crossed oracle pool rate (by cross, we mean going from above to below or vice versa)
  
  val successor = OUTPUTS(0)

  val validThreshold = lpRateXY * 100 < thresholdPercent * oraclePoolRateXY 
  
  val validSuccessor = successor.propositionBytes == SELF.propositionBytes && 
                       successor.tokens == SELF.tokens && 
                       successor.value >= SELF.value
  
  val validTracking = successor.R4[Int].get == crossCounter &&
                      successor.creationInfo._1 > (HEIGHT - errorMargin)
   
    sigmaProp(
      validLpBox &&
      validOraclePoolBox && 
      validThreshold &&
      validSuccessor &&
      validTracking
     )
}
