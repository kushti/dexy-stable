{
    // LP subcontract for deposit (aka LP tokens mint) action.
    //
    // This box: (LP Mint box)
    //
    // TOKENS
    //   Tokens(0): NFT to uniquely identify this box

    val lpBoxInIndex = 0
    val lpBoxOutIndex = 0

    val selfOutIndex = 1
    val successor = OUTPUTS(selfOutIndex)

    val lpBoxIn = INPUTS(lpBoxInIndex)
    val lpBoxOut = OUTPUTS(lpBoxOutIndex)

    val lpReservesIn = lpBoxIn.tokens(1)
    val lpReservesOut = lpBoxOut.tokens(1)

    val reservesXIn = lpBoxIn.value
    val reservesYIn = lpBoxIn.tokens(2)._2

    val reservesXOut = lpBoxOut.value
    val reservesYOut = lpBoxOut.tokens(2)._2

    val supplyLpIn = $initialLp - lpReservesIn._2

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

    // LP formulae below using UniSwap v2 (with initial token burning by bootstrapping with positive R4)
    val validMintLp = deltaSupplyLp > 0 && deltaReservesX > 0 && deltaReservesY > 0 && {
        val sharesUnlocked = min(
            deltaReservesX.toBigInt * supplyLpIn / reservesXIn,
            deltaReservesY.toBigInt * supplyLpIn / reservesYIn
        )
        deltaSupplyLp <= sharesUnlocked
    }

    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
                        successor.value >= SELF.value                        &&
                        successor.tokens == SELF.tokens

    sigmaProp(validMintLp && selfPreserved)
}