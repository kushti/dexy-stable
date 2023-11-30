{   // This box: (LP Swap box)
    //
    // TOKENS
    //   Tokens(0): NFT to uniquely identify this box

    //
    // valid swap is as follows
    //
    // the value feeNum / feeDenom is the fraction of fee
    // for example if feeNum = 3 and feeDenom = 1000 then fee is 0.003 = 0.3%

    // Note "sold" = sold by user (and added to LP, thus the reserves of LP box of sold currency will increase)
    // Fee is taken as follows:
    //  if amount sold by user is s then fee is taken out from s when calculating currency purchased by user
    //  As an example:
    //    feeNum = 3
    //    feeDenom = 1000
    //    (thus, fee is 0.3 %)
    //
    //  1. deltaSold = sold (must be > 0)
    //  2. soldOut = soldIn + deltaSold
    //  3. actualSold = sold * (1 - num/denom)
    //  4. actualBought = actualSold * rate
    //  5. boughtOut = boughtIn - actualBought
    //  The condition we enforce is boughtOut >= boughtIn - deltaBought

    // Thus, if we are selling X (i.e. NanoErgs, and buying Dexy, so that deltaErgs > 0)
    // actualDeltaErgs = deltaReservesX * (1 - feeNum / feeDenom)
    // rate = reservesYIn / reservesXIn
    // deltaReservesY >= - actualDeltaErgs * rate
    // or
    // deltaReservesY >= - actualDeltaErgs * reservesYIn / reservesXIn
    // or
    // deltaReservesY >= - deltaReservesX * (1 - feeNum / feeDenom) * reservesYIn / reservesXIn
    // deltaReservesY * reservesXIn >= - deltaReservesX * (1 - feeNum / feeDenom) * reservesYIn
    // deltaReservesY * reservesXIn * feeDenom >= - deltaReservesX * (feeDenom - feeNum) * reservesYIn
    // deltaReservesY * reservesXIn * feeDenom >= deltaReservesX * (feeNum - feeDenom) * reservesYIn

    val feeNum = $feeNumLp // 0.3 % if feeNum is 3 and feeDenom is 1000
    val feeDenom = $feeDenomLp

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

    val validSwap =
      deltaSupplyLp == 0 && (
        if (deltaReservesX > 0)
            reservesYIn.toBigInt * deltaReservesX * feeNum >= -deltaReservesY * (reservesXIn.toBigInt * feeDenom + deltaReservesX * feeNum)
        else
            reservesXIn.toBigInt * deltaReservesY * feeNum >= -deltaReservesX * (reservesYIn.toBigInt * feeDenom + deltaReservesY * feeNum)
      )

    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
                        successor.value >= SELF.value                        &&
                        successor.tokens == SELF.tokens

    sigmaProp(validSwap && selfPreserved)
}