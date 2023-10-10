{ // ===== Contract Information ===== //
  // taken from https://github.com/spectrum-finance/ergo-dex/blob/master/contracts/amm/cfmm/v1/n2t/SwapBuy.sc
  // Name: SwapBuy
  // Description: Contract that validates user's Token -> ERG swap in the CFMM n2t Pool.
  val FeeDenom            = 1000
  val FeeNum              = 997
  val DexFeePerTokenNum   = 5L
  val DexFeePerTokenDenom = 1000L
  val MinQuoteAmount      = 100L

  val poolIn = INPUTS(0)

  val validTrade =
    if (INPUTS.size >= 3 && poolIn.tokens.size == 3) {
      val rewardBox = OUTPUTS(2)

      val baseAmount = SELF.tokens(0)._2

      val poolNFT = poolIn.tokens(0)._1 // first token id is NFT

      val poolReservesX = poolIn.value.toBigInt // nanoErgs is X asset amount
      val poolReservesY = poolIn.tokens(2)._2.toBigInt // third token amount is Y asset amount

      val validPoolIn = poolNFT == fromBase64("$lpNFT")

      val deltaNErgs    = rewardBox.value - SELF.value // this is quoteAmount - fee
      val quoteAmount   = deltaNErgs.toBigInt * DexFeePerTokenDenom / (DexFeePerTokenDenom - DexFeePerTokenNum)
      val relaxedOutput = quoteAmount + 1 // handle rounding loss
      val fairPrice =
        poolReservesX * baseAmount * (FeeDenom - FeeNum) <= relaxedOutput * (poolReservesY * FeeDenom + baseAmount * (FeeDenom - FeeNum))

    //  val validMinerFee = OUTPUTS.map { (o: Box) =>
    //    if (o.propositionBytes == MinerPropBytes) o.value else 0L
    //  }.fold(0L, { (a: Long, b: Long) => a + b }) <= MaxMinerFee

      validPoolIn  &&
//      rewardBox.propositionBytes == Pk.propBytes &&
      quoteAmount >= MinQuoteAmount  &&
      fairPrice
    // && validMinerFee

    } else false

  // sigmaProp(Pk || validTrade)
  sigmaProp(validTrade)
}