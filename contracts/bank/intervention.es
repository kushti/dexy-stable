{
  //  Intervention script
  //
  // This box: Intervention box
  //
  // TOKENS
  //   tokens(0): Intervention NFT
  //
  // REGISTERS
  //
  // TRANSACTIONS
  // [1] Intervention
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 LP            |  LP            |   Oracle
  // 1 Bank          |  Bank          |   Tracking (98%)
  // 2 Intervention  |  Intervention  |

  // Oracle data:
  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

  // inputs indices
  val lpInIndex = 0
  val bankInIndex = 1

  // outputs indices
  val lpOutIndex = 0
  val bankOutIndex = 1
  val selfOutIndex = 2    // SELF should be third input

  // data inputs indices
  val oracleBoxIndex = 0
  val trackingBoxIndex = 1

  val lastIntervention = SELF.creationInfo._1
  val buffer = 3 // error margin in height
  val T = 100 // from paper, gap between two interventions
  val T_int = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate

  val bankNFT = fromBase64("$bankNFT")
  val lpNFT = fromBase64("$lpNFT")
  val oracleNFT = fromBase64("$oracleNFT")
  val tracking98NFT = fromBase64("$tracking98NFT")

  val thresholdPercent = 98 // 98% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)

  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
  val trackingBox = CONTEXT.dataInputs(trackingBoxIndex)

  val lpBoxIn = INPUTS(lpInIndex)
  val bankBoxIn = INPUTS(bankInIndex)

  val lpBoxOut = OUTPUTS(lpOutIndex)
  val bankBoxOut = OUTPUTS(bankOutIndex)

  val successor = OUTPUTS(selfOutIndex)

  val lpTokenYIn    = lpBoxIn.tokens(2)
  val lpTokenYOut    = lpBoxOut.tokens(2)

  val lpReservesXIn = lpBoxIn.value
  val lpReservesYIn = lpTokenYIn._2

  val lpReservesXOut = lpBoxOut.value
  val lpReservesYOut = lpTokenYOut._2

  val lpRateXyInTimesLpReservesYIn = lpReservesXIn.toBigInt   // we can assume that reservesYIn > 0 (since at least one token must exist)
  val lpRateXyOutTimesLpReservesYOut = lpReservesXOut.toBigInt  // we can assume that reservesYOut > 0 (since at least one token must exist)

  val oracleRateXy = oracleBox.R4[Long].get.toBigInt

  val validThreshold = lpRateXyInTimesLpReservesYIn * 100 < oracleRateXy * thresholdPercent * lpReservesYIn

  // check data inputs are correct
  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
  val validTrackingBox = trackingBox.tokens(0)._1 == tracking98NFT

  // check that inputs are correct
  val validLpBoxIn = lpBoxIn.tokens(0)._1 == lpNFT
  val validBankBoxIn = bankBoxIn.tokens(0)._1 == bankNFT

  // check that self output is correct
  val validSuccessor = successor.propositionBytes == SELF.propositionBytes  &&
                       successor.tokens == SELF.tokens                      &&
                       successor.value >= SELF.value                        &&
                       successor.creationInfo._1 >= HEIGHT - buffer


  val validGap = lastIntervention < HEIGHT - T

  val deltaBankTokens =  bankBoxOut.tokens(1)._2 - bankBoxIn.tokens(1)._2
  val deltaBankErgs = bankBoxIn.value - bankBoxOut.value
  val deltaLpX = lpReservesXOut - lpReservesXIn
  val deltaLpY = lpReservesYIn - lpReservesYOut

  val trackingHeight = trackingBox.R7[Int].get

  val validTracking = trackingHeight < HEIGHT - T_int // at least T_int blocks have passed since the tracking started

  val validAmount = lpRateXyOutTimesLpReservesYOut * 1000 <= oracleRateXy * lpReservesYOut * 995    // new rate must be <= 99.5 times oracle rate

  val validDeltas = deltaBankErgs <= deltaLpX  &&  // ergs reduced in bank box must be <= ergs gained in LP
                    deltaBankTokens >= deltaLpY &&   // tokens gained in bank box must be >= tokens reduced in LP
                    deltaLpX > 0

  val validSwap = validAmount      &&
                  validDeltas      &&
                  validBankBoxIn   &&
                  validLpBoxIn     &&
                  validSuccessor   &&
                  validOracleBox   &&
                  validTrackingBox &&
                  validThreshold   &&
                  validTracking    &&
                  validGap

  sigmaProp(validSwap)
}