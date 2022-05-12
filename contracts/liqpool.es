{
    // Notation:
    // 
    // X is the primary token
    // Y is the secondary token 
    // When using Erg-USD oracle v1, X is NanoErg and Y is USD   

    // This box: (LP box)
    //   R1 (value): X tokens in NanoErgs 
    //   R4: How many LP in circulation (long). This can be non-zero when bootstrapping, to consider the initial token burning in UniSwap v2
    //   R5: Cross-counter. A counter to track how many times the rate has "crossed" the oracle pool rate. That is the oracle pool rate falls in between the before and after rates
    //   Tokens(0): LP NFT to uniquely identify NFT box. (Could we possibly do away with this?) 
    //   Tokens(1): LP tokens
    //   Tokens(2): Y tokens (Note that X tokens are NanoErgs (the value) 
    //   
    // Data Input #0: (oracle pool box)
    //   R4: Rate in units of X per unit of Y
    //   Token(0): OP NFT to uniquely identify Oracle Pool
     
    // constants 
    val feeNum = 3 // 0.3 % 
    val feeDenom = 1000
    val minStorageRent = 10000000L  // this many number of nanoErgs are going to be permanently locked
    
    val successor = OUTPUTS(0) // copy of this box after exchange
    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle pool box
    val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box 
    
    val lpNFT0    = SELF.tokens(0)
    val reservedLP0 = SELF.tokens(1)
    val tokenY0     = SELF.tokens(2)

    val lpNFT1    = successor.tokens(0)
    val reservedLP1 = successor.tokens(1)
    val tokenY1     = successor.tokens(2)

    val supplyLP0 = SELF.R4[Long].get       // LP tokens in circulation in input LP box
    val supplyLP1 = successor.R4[Long].get  // LP tokens in circulation in output LP box

    val validSuccessorScript = successor.propositionBytes == SELF.propositionBytes
    
    val preservedLpNFT     = lpNFT1 == lpNFT0
    val validLP              = reservedLP1._1 == reservedLP0._1
    val validY               = tokenY1._1 == tokenY0._1
    val validSupplyLP1       = supplyLP1 >= 0
       
    // since tokens can be repeated, we ensure for sanity that there are no more tokens
    val noMoreTokens         = successor.tokens.size == 3
  
    val validStorageRent     = successor.value > minStorageRent

    val reservesX0 = SELF.value
    val reservesY0 = tokenY0._2
    val reservesX1 = successor.value
    val reservesY1 = tokenY1._2

    val oraclePoolRateXY = oraclePoolBox.R4[Long].get 
    val lpRateXY0 = reservesX0 / reservesY0  // we can assume that reservesY0 > 0 (since at least one token must exist) 
    val lpRateXY1 = reservesX1 / reservesY1  // we can assume that reservesY1 > 0 (since at least one token must exist)
    val isCrossing = (lpRateXY0 - oraclePoolRateXY) * (lpRateXY1 - oraclePoolRateXY) < 0 // if (and only if) oracle pool rate falls in between, then this will be negative
     
    val crossCounterIn = SELF.R5[Int].get
    val crossCounterOut = successor.R5[Int].get
    
    val validCrossCounter = crossCounterOut == {if (isCrossing) crossCounterIn + 1 else crossCounterIn}
     
    val validRateForRedeemingLP = oraclePoolRateXY > lpRateXY0 * 9 / 10 // lpRate must be >= 0.9 oraclePoolRate // these parameters need to be tweaked
    // Do we need above if we also have the tracking contract?
     
    val deltaSupplyLP  = supplyLP1 - supplyLP0
    val deltaReservesX = reservesX1 - reservesX0
    val deltaReservesY = reservesY1 - reservesY0
    
    // LP formulae below using UniSwap v2 (with initial token burning by bootstrapping with positive R4)
    val validDepositing = {
        val sharesUnlocked = min(
            deltaReservesX.toBigInt * supplyLP0 / reservesX0,
            deltaReservesY.toBigInt * supplyLP0 / reservesY0
        )
        deltaSupplyLP <= sharesUnlocked
    }

    val validRedemption = {
        val _deltaSupplyLP = deltaSupplyLP.toBigInt
        // note: _deltaSupplyLP, deltaReservesX and deltaReservesY are negative
        deltaReservesX.toBigInt * supplyLP0 >= _deltaSupplyLP * reservesX0 && deltaReservesY.toBigInt * supplyLP0 >= _deltaSupplyLP * reservesY0
    } && validRateForRedeemingLP

    val validSwap =
        if (deltaReservesX > 0)
            reservesY0.toBigInt * deltaReservesX * feeNum >= -deltaReservesY * (reservesX0.toBigInt * feeDenom + deltaReservesX * feeNum)
        else
            reservesX0.toBigInt * deltaReservesY * feeNum >= -deltaReservesX * (reservesY0.toBigInt * feeDenom + deltaReservesY * feeNum)

    val validAction =
        if (deltaSupplyLP == 0)
            validSwap
        else
            if (deltaReservesX > 0 && deltaReservesY > 0) validDepositing
            else validRedemption

    sigmaProp(
        validSupplyLP1 &&
        validSuccessorScript &&
        validOraclePoolBox &&
        preservedLpNFT &&
        validLP &&
        validY &&
        noMoreTokens &&
        validAction && 
        validStorageRent && 
        validCrossCounter
    )
}
