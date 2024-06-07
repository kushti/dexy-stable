{
    // Extract-to-the-future script. It allows for Dexy extraction from the LP when the bank reserves are lean and Dexy
    // is depegged in the pool, to return Dexy tokens back to the LP when the peg is restored
    //
    // TOKENS
    //   tokens(0): extractionNFT
    //   tokens(1): Dexy tokens
    //
    // REGISTERS
    //   R3 (creation-info)
    //
    // TRANSACTIONS
    //
    // [1] Extract to future
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Extract       |  Extract       |   Tracking (95%)
    // 2               |                |   Bank   (to check that bank is empty)
    //
    // [2] Reverse Extract to future (release)
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Extract       |  Extract       |   Tracking (101%)

    // ToDo: verify following in tests
    //   cannot change prop bytes for LP, Extract and Tracking box
    //   cannot change tokens/nanoErgs in LP, extract and tracking box except what is permitted

    // Oracle data:
    // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

    val lpBoxInIndex = 0
    val lpBoxOutIndex = 0

    val selfOutIndex = 1

    // for data inputs
    val oracleBoxIndex = 0
    val bankBoxIndex = 2
    val tracking95BoxIndex = 1

    val tracking101BoxIndex = 1

    // if less nanoErgs than this number in bank box, then the bank is considered "empty"
    // 10000 Erg
    val minBankNanoErgs = 10000 * 1000000000L // 10000 ERG

    val tracking95NFT = fromBase64("$tracking95NFT")
    val tracking101NFT = fromBase64("$tracking101NFT")
    val bankNFT = fromBase64("$bankNFT")
    val lpNFT = fromBase64("$lpNFT")
    val oracleNFT = fromBase64("$oracleNFT")

    val T_extract = 720 // blocks for which the rate is below 95%
    val T_release = 2 // blocks for which the rate is above 101%
    val T_delay = 360 // delay between any burn/release operation  ("T_burn" in the paper)

    val buffer = 5 // allowable error in setting height due to congestion

    val updateNFT = fromBase64("$updateNFT")
    val validUpdate = INPUTS(0).tokens(0)._1 == updateNFT

    val validAction = if (validUpdate) {
      true
    } else {

      // tracking box should record at least T_extract blocks of < 95%
      val tracking95Box = CONTEXT.dataInputs(tracking95BoxIndex)
      val tracking101Box = CONTEXT.dataInputs(tracking101BoxIndex)
      val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)

      val tracker95Height = tracking95Box.R7[Int].get
      val tracker101Height = tracking101Box.R7[Int].get

      val lpBoxIn = INPUTS(lpBoxInIndex)
      val lpBoxOut = OUTPUTS(lpBoxInIndex)

      val successor = OUTPUTS(selfOutIndex)

      val lastBurnOrRelease = SELF.creationInfo._1

      val validDelay = lastBurnOrRelease < HEIGHT - T_delay

      val validSuccessor = successor.tokens(0)._1 == SELF.tokens(0)._1          &&  // NFT preserved
                           successor.tokens(1)._1 == SELF.tokens(1)._1          &&  // Dexy token id preserved
                           successor.propositionBytes == SELF.propositionBytes  &&
                           successor.value >= SELF.value                        &&
                           successor.creationInfo._1 >= HEIGHT - buffer

      val deltaDexy = successor.tokens(1)._2 - SELF.tokens(1)._2 // can be +ve or -ve

      val validBankBox = if (CONTEXT.dataInputs.size > bankBoxIndex) {
        CONTEXT.dataInputs(bankBoxIndex).tokens(0)._1 == bankNFT &&
          CONTEXT.dataInputs(bankBoxIndex).value <= minBankNanoErgs
      } else false

      val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT

      val reservesYOut = lpBoxOut.tokens(2)._2
      val reservesYIn = lpBoxIn.tokens(2)._2
      val reservesXOut = lpBoxOut.value
      val reservesXIn = lpBoxIn.value

      val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT                               && // Maybe this check not needed? (see LP box)
                       lpBoxOut.tokens(0)._1 == lpBoxIn.tokens(0)._1               && // NFT preserved
                       lpBoxOut.tokens(1) == lpBoxIn.tokens(1)                     && // LP tokens preserved
                       lpBoxOut.tokens(2)._1 == lpBoxIn.tokens(2)._1               && // Dexy token Id preserved
                       lpBoxOut.tokens(2)._1 == SELF.tokens(1)._1                  && // Dexy token Id is same as tokens stored here
                       reservesYOut == reservesYIn - deltaDexy                     && // Dexy token qty preserved
                       reservesXOut == reservesXIn                                 &&
                       lpBoxOut.propositionBytes == lpBoxIn.propositionBytes

      val validTracking95Box = tracking95Box.tokens(0)._1 == tracking95NFT
      val validTracking101Box = tracking101Box.tokens(0)._1 == tracking101NFT

      // oracle delivers nanoErgs per 1 kg of gold
      // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
      // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
      val oracleRateXY = oracleBox.R4[Long].get / 1000000L
      val lpRateXYOut = reservesXOut / reservesYOut

      val validExtractAmount = oracleRateXY * 97 < lpRateXYOut * 100 &&  // oracleRate must be > 0.97 * lpRate at output
                                 oracleRateXY * 98 > lpRateXYOut * 100   // oracleRate must be < 0.98 * oracleRate at output

      val validReleaseAmount = oracleRateXY * 101 > lpRateXYOut * 100    // oracleRate must be > 1.01 * lpRate at output to release

      val validExtract  = deltaDexy > 0                           &&
                          validTracking95Box                      &&
                          (HEIGHT - tracker95Height) > T_extract  && // at least T_extract blocks have passed after crossing below 95%
                          validBankBox                            &&
                          validExtractAmount

      val validRelease  = deltaDexy < 0                          &&
                          validTracking101Box                    &&
                          HEIGHT - tracker101Height > T_release  &&
                          validReleaseAmount

      validSuccessor && validDelay && validLpBox && validOracleBox && (validRelease || validExtract)
    }

    sigmaProp(validAction)
}