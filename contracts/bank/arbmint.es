{
  // This box: (arbitrage-mint box)
  //
  // TOKENS
  //   tokens(0): Arbitrage-mint NFT
  //
  // REGISTERS
  //   R4: (Int) height at which counter will reset
  //   R5: (Long) remaining Dexy tokens available to be purchased before counter is reset
  //
  // TRANSACTIONS
  //
  // [1] Arbitrage Mint
  //   Input         |  Output         |   Data-Input
  // ------------------------------------------------
  // 0 ArbitrageMint |  ArbitrageMint  |   Oracle
  // 1 Bank          |  Bank           |   LP
  //                 |                 |   Tracking101box

  // Oracle data:
  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

  // input indices
  val bankInIndex = 1

  // output indices
  val selfOutIndex = 0
  val bankOutIndex = 1

  // data input indices
  val oracleBoxIndex = 0
  val lpBoxIndex = 1
  val tracking101BoxIndex = 2

  val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box
  val bankNFT = fromBase64("$bankNFT")
  val lpNFT = fromBase64("$lpNFT")
  val tracking101NFT = fromBase64("$tracking101NFT")


  val T_arb = 30 // 30 blocks = 1 hour
  val T_buffer = 5 // max delay permitted after broadcasting and confirmation of the tx spending this box
  val thresholdPercent = 101 // 101% or more value (of LP in terms of OraclePool) will trigger action

  val feeNum = 5
  val feeDenom = 1000
  // actual fee ratio is feeNum / feeDenom
  // example if feeNum = 5 and feeDenom = 1000 then fee = 0.005 = 0.5 %

  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex) // oracle-pool (v1 and v2) box containing rate in R4
  val lpBox = CONTEXT.dataInputs(lpBoxIndex)
  val tracking101Box = CONTEXT.dataInputs(tracking101BoxIndex)

  val tracking101Height = tracking101Box.R7[Int].get

  val bankBoxIn = INPUTS(bankInIndex)

  val successor = OUTPUTS(selfOutIndex)
  val bankBoxOut = OUTPUTS(bankOutIndex)

  val selfInR4 = SELF.R4[Int].get
  val selfInR5 = SELF.R5[Long].get
  val successorR4 = successor.R4[Int].get
  val successorR5 = successor.R5[Long].get

  val isCounterReset = HEIGHT > selfInR4

  val oracleRateWithoutFee = oracleBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  val oracleRateWithFee = oracleRateWithoutFee * (feeNum + feeDenom) / feeDenom

  val lpReservesX = lpBox.value
  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves
  val lpRate = lpReservesX / lpReservesY

  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
  val ergsAdded = bankBoxOut.value - bankBoxIn.value
  val validDelta = ergsAdded >= dexyMinted * oracleRateWithFee && ergsAdded > 0 // dexyMinted must be (+)ve, since both ergsAdded and oracleRateWithFee are (+)ve

  val maxAllowedIfReset = (lpReservesX - oracleRateWithFee * lpReservesY) / oracleRateWithFee

  // above formula:
  // Before mint rate is lpReservesX / lpReservesY, which should be greater than oracleRateWithFee
  // After mint rate is lpReservesX / (lpReservesY + dexyMinted), which should be same or less than than oracleRateWithFee
  //  Thus:
  //   lpReservesX / lpReservesY > oracleRateWithFee
  //   lpReservesX / (lpReservesY + dexyMinted) <= oracleRateWithFee
  // above gives min value of dexyMinted = (lpReservesX - oracleRateWithFee * lpReservesY) / oracleRateWithFee

  val availableToMint = if (isCounterReset) maxAllowedIfReset else selfInR5

  val validAmount = dexyMinted <= availableToMint

  val validSuccessorR4 = if (!isCounterReset) {
    successorR4 == selfInR4
  } else { // set R4 to HEIGHT_AT_BROADCAST + T_arb + T_buffer
    successorR4 >= HEIGHT + T_arb &&
    successorR4 <= HEIGHT + T_arb + T_buffer
  }

  val validSuccessorR5 = successorR5 == availableToMint - dexyMinted

  val validBankBoxInOut = bankBoxIn.tokens(0)._1 == bankNFT
  val validLpBox = lpBox.tokens(0)._1 == lpNFT
  val validTracking101Box = tracking101Box.tokens(0)._1 == tracking101NFT
  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
  val validSuccessor = successor.tokens == SELF.tokens                     && // NFT preserved
                       successor.propositionBytes == SELF.propositionBytes && // script preserved
                       successor.value >= SELF.value                       &&
                       validSuccessorR5                                    &&
                       validSuccessorR4

  val validDelay = tracking101Height < HEIGHT - T_arb // at least T_arb blocks have passed since the tracking started
  val validThreshold = lpRate * 100 > thresholdPercent * oracleRateWithFee

  sigmaProp(validDelay && validThreshold && validAmount && validBankBoxInOut && validLpBox && validOracleBox && validTracking101Box && validSuccessor && validDelta)
}