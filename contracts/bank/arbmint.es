{
  // Arbitrage mint action script
  //
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
  // 2 BuyBack       |  BuyBack        |   Tracking101box

  // Oracle data:
  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

  // input indices
  val bankInIndex = 1
  val buybackInIndex = 2

  // output indices
  val selfOutIndex = 0
  val bankOutIndex = 1
  val buybackOutIndex = 2

  // data input indices
  val oracleBoxIndex = 0
  val lpBoxIndex = 1
  val tracking101BoxIndex = 2

  val bankNFT = fromBase64("$bankNFT")
  val buybackNft = fromBase64("$buybackNFT")

  val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box
  val lpNFT = fromBase64("$lpNFT")
  val tracking101NFT = fromBase64("$tracking101NFT")

  val T_arb = 30 // 30 blocks = 1 hour
  val T_buffer = 5 // max delay permitted after broadcasting and confirmation of the tx spending this box
  val thresholdPercent = 101 // 101% or more value (of LP in terms of OraclePool) will trigger action

  // feeNum = bankFeeNum + buybackFeeNum
  // actual fee ratio is feeNum / feeDenom
  // we have feeNum = 5 and feeDenom = 1000 then minting fee = 0.005 = 0.5 %
  val bankFeeNum = 3
  val buybackFeeNum = 2
  val feeDenom = 1000

  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex) // oracle-pool (v1 and v2) box containing rate in R4
  val lpBox = CONTEXT.dataInputs(lpBoxIndex)
  val tracking101Box = CONTEXT.dataInputs(tracking101BoxIndex)

  val tracking101Height = tracking101Box.R7[Int].get

  val bankBoxIn = INPUTS(bankInIndex)
  val buybackBoxIn = INPUTS(buybackInIndex)

  val successor = OUTPUTS(selfOutIndex)
  val bankBoxOut = OUTPUTS(bankOutIndex)
  val buybackOut = OUTPUTS(buybackOutIndex)

  val selfInR4 = SELF.R4[Int].get
  val selfInR5 = SELF.R5[Long].get
  val successorR4 = successor.R4[Int].get
  val successorR5 = successor.R5[Long].get

  val isCounterReset = HEIGHT > selfInR4

  // oracle delivers nanoErgs per 1 kg of gold
  // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
  // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  val oracleRate = oracleBox.R4[Long].get / 1000000L

  val lpReservesX = lpBox.value
  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves
  val lpRate = lpReservesX / lpReservesY

  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
  val ergsAdded = bankBoxOut.value - bankBoxIn.value
  val bankRate = oracleRate * (bankFeeNum + feeDenom) / feeDenom
  val validBankDelta = ergsAdded >= dexyMinted * bankRate && ergsAdded > 0 // dexyMinted must be (+)ve, since both ergsAdded and bankRate are (+)ve

  val buybackErgsAdded = buybackOut.value - buybackBoxIn.value
  val buybackRate = oracleRate * buybackFeeNum / feeDenom
  val validBuybackDelta = buybackErgsAdded >= dexyMinted * buybackRate && buybackErgsAdded > 0
  val validDelta = validBankDelta && validBuybackDelta
  val oracleRateWithFee = bankRate + buybackRate

  // how many dexy tokens allowed to mint per period
  // oracle rate is in X/Y , so (X - X/Y*Y) / (X/Y) is in Y
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
  val validBuyBackIn = buybackBoxIn.tokens(0)._1 == buybackNft
  val validSuccessor = successor.tokens == SELF.tokens                     && // NFT preserved
                       successor.propositionBytes == SELF.propositionBytes && // script preserved
                       successor.value >= SELF.value                       &&
                       validSuccessorR5                                    &&
                       validSuccessorR4

  val validDelay = tracking101Height < HEIGHT - T_arb // at least T_arb blocks have passed since the tracking started
  val validThreshold = lpRate * 100 > thresholdPercent * oracleRateWithFee

  sigmaProp(validDelay && validThreshold && validAmount && validBankBoxInOut && validLpBox && validBuyBackIn &&
            validOracleBox && validTracking101Box && validSuccessor && validDelta)
}