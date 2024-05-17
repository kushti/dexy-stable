{
    // LP subcontract for redeeming LP tokens action.
    //
    // This box: (LP Redeem box)
    //
    // TOKENS
    //   Tokens(0): NFT to uniquely identify this box

    val initialLp = $initialLp   // How many LP initially minted. Used to compute Lp in circulation (supply Lp).
    // Note that at bootstrap, we may have initialLp > tokens stored in LP box quantity to consider the initial token burning in UniSwap v2

    val lpBoxInIndex = 0 // input
    val oracleBoxIndex = 0 // data input
    val lpBoxOutIndex = 0 // output
    val selfOutIndex = 1 // output

    val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box

    val lpBoxIn = INPUTS(lpBoxInIndex)

    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
    val lpBoxOut = OUTPUTS(lpBoxOutIndex)
    val successor = OUTPUTS(selfOutIndex)

    val lpReservesIn = lpBoxIn.tokens(1)
    val lpReservesOut = lpBoxOut.tokens(1)

    val reservesXIn = lpBoxIn.value
    val reservesYIn = lpBoxIn.tokens(2)._2

    val reservesXOut = lpBoxOut.value
    val reservesYOut = lpBoxOut.tokens(2)._2

    // circulating supply of LP tokens
    val supplyLpIn = initialLp - lpReservesIn._2

    // oracle delivers nanoErgs per 1 kg of gold
    // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
    // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
    val oracleRateXy = oracleBox.R4[Long].get / 1000000L
    val lpRateXyIn = reservesXIn / reservesYIn  // we can assume that reservesYIn > 0 (since at least one token must exist)

    val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT

    val validRateForRedeemingLp = validOracleBox && lpRateXyIn > oracleRateXy * 98 / 100 // lpRate must be >= 0.98 * oracleRate // these parameters need to be tweaked

    // Note:
    //    supplyLpIn = initialLp - lpReservesIn._2
    //    supplyLpOut = initialLp - lpReservesOut._2
    // Thus:
    //    deltaSupplyLp = supplyLpOut - supplyLpIn
    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
    //                  = lpReservesIn._2 - lpReservesOut._2

    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2
    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    val validRedemption = deltaSupplyLp < 0 && deltaReservesX < 0 && deltaReservesY < 0 && {
        val _deltaSupplyLp = deltaSupplyLp.toBigInt
        // note: _deltaSupplyLp, deltaReservesX and deltaReservesY are negative
        // 2% fee
        deltaReservesX.toBigInt * supplyLpIn * 100 / 98 >= _deltaSupplyLp * reservesXIn &&
            deltaReservesY.toBigInt * supplyLpIn * 100 / 98 >= _deltaSupplyLp * reservesYIn
    } && validRateForRedeemingLp

    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
                        successor.value >= SELF.value                        &&
                        successor.tokens == SELF.tokens

    sigmaProp(validRedemption && selfPreserved)
}