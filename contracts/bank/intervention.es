{
  // Intervention script. It encodes intervention action, where the bank is buying back Dexy tokens from the LP to
  // restore the peg in the LP.
  //
  // Parameters: intervention happens every T = 360 blocks if Dexy token price in the LP is <= 98% of oracle price.
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
  // input #2 is SELF

  // outputs indices
  val lpOutIndex = 0
  val bankOutIndex = 1
  val selfOutIndex = 2    // SELF should be third input

  // data inputs indices
  val oracleBoxIndex = 0
  val trackingBoxIndex = 1

  val lastIntervention = SELF.creationInfo._1
  val buffer = 5 // error margin in height
  val T = 360 // from paper, gap between two interventions
  val T_int = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate

  val bankNFT = fromBase64("$bankNFT")
  val lpNFT = fromBase64("$lpNFT")
  val oracleNFT = fromBase64("$oracleNFT")
  val tracking98NFT = fromBase64("$tracking98NFT")

  val thresholdPercent = 98 // 98% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)

  val updateNFT = fromBase64("$updateNFT")
  val validUpdate = INPUTS(0).tokens(0)._1 == updateNFT

  val validAction = if (validUpdate) {
    true
  } else {

    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
    val trackingBox = CONTEXT.dataInputs(trackingBoxIndex)

    val lpBoxIn = INPUTS(lpInIndex)
    val bankBoxIn = INPUTS(bankInIndex)

    val lpBoxOut = OUTPUTS(lpOutIndex)
    val bankBoxOut = OUTPUTS(bankOutIndex)

    val successor = OUTPUTS(selfOutIndex)

    val lpTokenYIn = lpBoxIn.tokens(2)
    val lpTokenYOut = lpBoxOut.tokens(2)

    val lpReservesXIn = lpBoxIn.value
    val lpReservesYIn = lpTokenYIn._2

    val lpReservesXOut = lpBoxOut.value
    val lpReservesYOut = lpTokenYOut._2

    val lpReservesXInBigInt = lpReservesXIn.toBigInt   // we can assume that reservesYIn > 0 (since at least one token must exist)
    val lpReservesXOutBigInt = lpReservesXOut.toBigInt  // we can assume that reservesYOut > 0 (since at least one token must exist)

    // oracle delivers nanoErgs per 1 kg of gold
    // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
    // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
    val oracleRateXy = (oracleBox.R4[Long].get / 1000000L).toBigInt

    val validThreshold = lpReservesXInBigInt * 100 < oracleRateXy * thresholdPercent * lpReservesYIn

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

    val validMaxSpending = lpReservesXOutBigInt * 1000 <= oracleRateXy * lpReservesYOut * 995  &&   // new rate must be <= 99.5 * oracle rate
                           deltaBankErgs <= bankBoxIn.value / 100 // no more than 1% of reserves spent per intervention

    // dexy price
    val price = lpBoxIn.value / lpBoxIn.tokens(2)._2

    val validDeltas = deltaBankErgs <= deltaLpX  &&  // ergs reduced in bank box must be <= ergs gained in LP
                      deltaBankTokens >= deltaLpY &&   // tokens gained in bank box must be >= tokens reduced in LP
                      deltaLpY.toBigInt * lpReservesXIn <= deltaLpX.toBigInt * lpReservesYIn && // swap condition w/out fees
                      deltaLpX > 0 &&
                      deltaBankErgs <= price * deltaBankTokens / 100 * 102 // max slippage 2%

    validMaxSpending &&
    validDeltas      &&
    validBankBoxIn   &&
    validLpBoxIn     &&
    validSuccessor   &&
    validOracleBox   &&
    validTrackingBox &&
    validThreshold   &&
    validTracking    &&
    validGap
  }

  sigmaProp(validAction)
}