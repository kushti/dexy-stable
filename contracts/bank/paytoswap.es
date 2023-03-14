{
  // GORT (Gold Oracle Reward Token) buyback script
  //
  // Swap:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 LP            |  LP            |
  // 1 Swap          |  Swap          |
  // 2 Pay-to-swap   |  mining fee    |
  //
  // Top-up:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 BuyBack       |  BuyBack       |

  // checking that swap inputs provided
  val swapNft = INPUTS(0).tokens(0)._1 == fromBase64("$gortLpNFT") &&
                  INPUTS(1).tokens(0)._1 == fromBase64("$gortLpSwapNFT")

  val topUp = OUTPUTS(0).tokens == SELF.tokens && SELF.value < OUTPUTS(0).value
  sigmaProp(swapNft || topUp)

}