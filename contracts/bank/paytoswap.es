{
  // GORT (Gold Oracle Reward Token) buyback script
  //
  // It is accepting ERGs (via top-up action),
  //
  // Tokens:
  //  - buyback NFT
  //
  // Swap:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 LP            |  LP            |
  // 1 Swap          |  Swap          |
  // 2 BuyBack       |  BuyBack       |
  //
  // Top-up:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 BuyBack       |  BuyBack       |
  //
  // Return:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 Pool          |  Pool          |
  // 1 Refresh       |  Refresh       |
  // 2 BuyBack       |  BuyBack       |

  // swap path

  val buybackNft = SELF.tokens(0)._1

  // checking that swap inputs provided
  val swapNft = INPUTS(0).tokens(0)._1 == fromBase64("$gortLpNFT") &&
                  INPUTS(1).tokens(0)._1 == fromBase64("$gortLpSwapNFT")
  val outputsCorrect = OUTPUTS.size == 4 && OUTPUTS(3).tokens.size == 0 && OUTPUTS(2).tokens(0)._1 == buybackNft

  val swap = swapNft && outputsCorrect

  // top-up path
  val topUp = OUTPUTS(0).tokens == SELF.tokens &&
              OUTPUTS(0).propositionBytes == SELF.propositionBytes &&
              SELF.value < OUTPUTS(0).value

  // return path
  val giveback = OUTPUTS(2).tokens(0) == SELF.tokens(0) &&
               OUTPUTS(2).tokens.size == 1 &&
               OUTPUTS(2).propositionBytes == SELF.propositionBytes &&
               SELF.value == OUTPUTS(2).value

  sigmaProp(swap || topUp || giveback)

}