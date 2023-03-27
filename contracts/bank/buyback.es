{
  // GORT (Gold Oracle Reward Token) buyback script
  //
  // It is accepting ERGs (via top-up action), swapping them in ERG/GORT LP pool to get GORT, and gives GORT back
  // to oracle pool
  //
  // Tokens:
  //  - buyback NFT
  //  - gort
  //
  //  Registers:
  //    None
  //
  // Swap:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 LP            |  LP            |
  // 1 BuyBack       |  BuyBack       |
  //
  // Top-up:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 mint action   |  mint action   |
  // 1 Bank          |  Bank          |
  // 2 BuyBack       |  BuyBack       |
  //
  // Give back:
  //
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 Pool          |  Pool          |
  // 1 Refresh       |  Refresh       |
  // 2 BuyBack       |  BuyBack       |

  val action = getVar[Int](0).get

  if(action == 0) {
    // swap path
    val buybackNft = SELF.tokens(0)._1

    // checking that swap inputs provided
    val poolInput = INPUTS(0)
    val lpCorrect = poolInput.tokens(0)._1 == fromBase64("$gortLpNFT")

    // checking that gort tokens are in LP and buyback outputs only
    // we consider other outputs are fee and maybe change, and change output could not have tokens
    def noTokens(b: Box) = b.tokens.size == 0
    val outputsCorrect = OUTPUTS.slice(2, OUTPUTS.size).forall(noTokens)

    val selfOut = OUTPUTS(1)
    val selfOutCorrect = selfOut.tokens(0)._1 == buybackNft &&
                          selfOut.tokens(1)._1 == fromBase64("$gortId")

    val price = poolInput.value / poolInput.tokens(2)._2
    val gortObtained = selfOut.tokens(1)._2 - SELF.tokens(1)._2
    val maxErgDelta = price * gortObtained / 100 * 105 // max slippage 5%
    val ergDelta = SELF.value - selfOut.value
    val poolOutput = OUTPUTS(0)
    val swapCorrect = gortObtained > 0 && ergDelta <= maxErgDelta && poolOutput.value - poolInput.value == ergDelta

    sigmaProp(lpCorrect && outputsCorrect && selfOutCorrect && swapCorrect)
  } else if(action == 1) {
    // top-up path
    val selfOut = OUTPUTS(2)
    val topUp = selfOut.tokens == SELF.tokens &&
                selfOut.propositionBytes == SELF.propositionBytes &&
                SELF.value < selfOut.value
    sigmaProp(topUp)
  } else {
    // return path
    val minStartHeight = HEIGHT - $epochLength
    val poolIn = INPUTS(0)

    def isValidDataPoint(b: Box) = if (b.R6[Long].isDefined) {
        b.creationInfo._1    >= minStartHeight &&
        b.tokens(0)._1       == fromBase64("$oracleTokenId") &&
        b.R5[Int].get        == poolIn.R5[Int].get
    }  else false

    val dataPoints = INPUTS.filter(isValidDataPoint)
    val rewardEmitted = dataPoints.size * 2

    val selfGort = SELF.tokens(1)._2
    val properGiving =  poolIn.tokens(0)._1 == fromBase64("$oracleNFT") &&
                        OUTPUTS(0).tokens(1)._2 >= poolIn.tokens(1)._2 + selfGort - rewardEmitted

    val giveback = OUTPUTS(2).tokens(0) == SELF.tokens(0) &&
                   OUTPUTS(2).propositionBytes == SELF.propositionBytes &&
                   SELF.value == OUTPUTS(2).value &&
                   properGiving

    sigmaProp(giveback)
  }
}