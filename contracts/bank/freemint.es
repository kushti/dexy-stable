{
  // Free mint action script
  //
  // This box: (free-mint box)
  //
  // TOKENS
  //   tokens(0): Free-mint NFT
  //
  // REGISTERS
  //   R4: (Int) height at which counter will reset
  //   R5: (Long) remaining Dexy tokens available to be purchased before counter is reset
  //
  // TRANSACTIONS
  // [1] Free Mint
  //   Input    |  Output   |   Data-Input
  // -------------------------------------
  // 0 FreeMint |  FreeMint |   Oracle
  // 1 Bank     |  Bank     |   LP


  // Oracle data:
  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

  // inputs indices
  val bankInIndex = 1

  // outputs indices
  val selfOutIndex = 0
  val bankOutIndex = 1

  // data inputs indices
  val oracleBoxIndex = 0
  val lpBoxIndex = 1

  val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box
  val bankNFT = fromBase64("$bankNFT")
  val lpNFT = fromBase64("$lpNFT")

  val T_free = 100
  val T_buffer = 5 // max delay permitted after broadcasting and confirmation of the tx spending this box

  val feeNum = 10
  val feeDenom = 1000
  // actual fee ratio is feeNum / feeDenom
  // example if feeNum = 10 and feeDenom = 1000 then fee = 0.01 = 1 %

  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex) // oracle-pool (v1 and v2) box containing rate in R4
  val lpBox = CONTEXT.dataInputs(lpBoxIndex)
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

  val validRateFreeMint = 98 * lpRate < oracleRateWithoutFee * 100 &&
                          oracleRateWithoutFee * 100 < 102 * lpRate

  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
  val ergsAdded = bankBoxOut.value - bankBoxIn.value
  val validDelta = ergsAdded >= dexyMinted * oracleRateWithFee && ergsAdded > 0 // dexyMinted must be (+)ve, since both ergsAdded and oracleRateWithFee are (+)ve

  val maxAllowedIfReset = lpReservesY / 100

  val availableToMint = if (isCounterReset) maxAllowedIfReset else selfInR5

  val validAmount = dexyMinted <= availableToMint

  val validSuccessorR4 = if (!isCounterReset) {
    successorR4 == selfInR4
  } else { // set R4 to HEIGHT_AT_BROADCAST + T_free + T_buffer
    successorR4 >= HEIGHT + T_free &&
    successorR4 <= HEIGHT + T_free + T_buffer
  }
  val validSuccessorR5 = successorR5 == availableToMint - dexyMinted

  val validBankBoxInOut = bankBoxIn.tokens(0)._1 == bankNFT
  val validLpBox = lpBox.tokens(0)._1 == lpNFT
  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
  val validSuccessor = successor.tokens == SELF.tokens                     && // NFT preserved
                       successor.propositionBytes == SELF.propositionBytes && // script preserved
                       successor.value >= SELF.value                       &&
                       validSuccessorR5                                    &&
                       validSuccessorR4

  sigmaProp(validAmount && validBankBoxInOut && validLpBox && validOracleBox && validSuccessor && validDelta && validRateFreeMint)
}