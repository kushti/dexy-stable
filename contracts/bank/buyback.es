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
  // 1 BuyBack       |  BuyBack       |
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

  val action = getVar[Int](0).get

  if(action == 0) {
    // swap path
    val buybackNft = SELF.tokens(0)._1

    // checking that swap inputs provided
    val poolInput = INPUTS(0)
    val swapNft = poolInput.tokens(0)._1 == fromBase64("$gortLpNFT")

    def noTokens(b: Box) = b.tokens.size == 0
    val outputsCorrect = OUTPUTS.slice(2, OUTPUTS.size).forall(noTokens)

    val selfOut = OUTPUTS(1)
    val price = poolInput.value / poolInput.tokens(2)._2
    val gortObtained = selfOut.tokens(1)._2
    val maxErgDelta = price * gortObtained * 11 / 10
    val selfCorrect = selfOut.tokens(0)._1 == buybackNft &&
                      selfOut.tokens(1)._1 == fromBase64("$gortId") &&
                      gortObtained >= 0 && // todo: should be >
                      SELF.value - selfOut.value <= maxErgDelta

    val swap = swapNft && outputsCorrect && selfCorrect
    sigmaProp(swap)
  } else if(action == 1) {
    // top-up path
    val topUp = OUTPUTS(0).tokens == SELF.tokens &&
                OUTPUTS(0).propositionBytes == SELF.propositionBytes &&
                SELF.value < OUTPUTS(0).value
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