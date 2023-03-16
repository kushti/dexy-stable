{
  // GORT (Gold Oracle Reward Token) buyback script
  //
  // It is accepting ERGs (via top-up action), swapping them in ERG/GORT LP pool to get GORT, and gives GORT back
  // to oracle pool
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
  // Give back:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 Pool          |  Pool          |
  // 1 Refresh       |  Refresh       |
  // 2 BuyBack       |  BuyBack       |

  // swap path

  val buybackNft = SELF.tokens(0)._1

  // checking that swap inputs provided
  val poolInput = INPUTS(0)
  val swapNft = poolInput.tokens(0)._1 == fromBase64("$gortLpNFT") &&
                  INPUTS(1).tokens(0)._1 == fromBase64("$gortLpSwapNFT")
  val outputsCorrect = OUTPUTS.size == 4 && OUTPUTS(3).tokens.size == 0
  val selfOut = OUTPUTS(2)
  val minPrice = poolInput.value / poolInput.tokens(2)._2
  val gortObtained = selfOut.tokens(1)._2
  val maxErgDelta = minPrice * gortObtained * 11 / 10
  val selfCorrect = selfOut.tokens(0)._1 == buybackNft &&
                    selfOut.tokens(1)._1 == fromBase64("$gortId") &&
                    SELF.value - selfOut.value <= maxErgDelta

  val swap = swapNft && outputsCorrect

  // top-up path
  val topUp = OUTPUTS(0).tokens == SELF.tokens &&
              OUTPUTS(0).propositionBytes == SELF.propositionBytes &&
              SELF.value < OUTPUTS(0).value

  // return path
  // todo: check pool tokens
  val giveback = OUTPUTS(2).tokens(0) == SELF.tokens(0) &&
               OUTPUTS(2).tokens.size == 1 &&
               OUTPUTS(2).propositionBytes == SELF.propositionBytes &&
               SELF.value == OUTPUTS(2).value

  sigmaProp(swap || topUp || giveback)

}