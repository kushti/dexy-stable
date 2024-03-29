# Dexy StableCoin

* Author: @kushti, @scalahub, @code-for-uss
* Status: Proposed
* Created: 20-April-2022
* License: CC0
* Forking: not needed

## Description

This EIP defines a design for a stablecoin called "Dexy", first proposed by @kushti. 
Dexy uses a combination of oracle-pool and a liquidity pool. 

Below are the main aspects of Dexy.

1. **One-way tethering**: There is a minting (or "emission") contract that emits Dexy tokens (example DexyUSD) in a one-way swap using the oracle pool rate.
   The swap is one-way in the sense that we can only buy Dexy tokens by selling ergs to the box. We cannot do the reverse swap.

2. **Liquidify Pool**: The reverse swap, selling of Dexy tokens, is done via a Liquidity Pool (LP) which also permits buying Dexy tokens. The LP
   primarily uses the logic of Uniswap V2. The difference is that the LP also takes as input the oracle pool rate and uses that to modify certain logic. In particular,
   redeeming of LP tokens is not allowed when the oracle pool rate is below a certain percent (say 90%) of the LP rate.

3. In case the oracle pool rate is higher than LP rate, then traders can do arbitrage by minting Dexy tokens from the emission box and
   selling them to the LP.

4. In case the oracle pool rate is lower than LP rate, then the Ergs collected in the emission box can be used to bring the rate back up by performing a swap.
   We call this the "top-up swap".

The swap logic is encoded in a **swapping** contract.

There is another contract, the **tracking** contract that is responsible for tracking the LP's state. In particular, this contract
tracks the block at which the "top-up-swap" is initiated. The swap can be initiated when the LP rate falls below 90%.
Once initiated, if the LP rate remains below the oracle pool rate for a certain threshold number of blocks, the swap can be compleded.
On the other hand, if before the threshold the rate goes higher than oracle pool then the swap must be aborted.

The LP uses a "cross-counter" to keep count of the number of times the LP rate has crossed the oracle pool rate (from below or above) in a swap transaction.
If the cross-counter is preserved at swap initiation and completion then swap is valid, else it is aborted. This logic is present in the swapping box.

## Emission Contract

```scala
{ 
  // This box: (dexyUSD emission box)
  //   tokens(0): emissionNFT identifying the box
  //   tokens(1): dexyUSD tokens to be emitted
  
  val selfOutIndex = getVar[Int](0).get
  
  val oraclePoolNFT = fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box
  val swappingNFT = fromBase64("Fho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify swapping box for future use
  
  val validEmission = {
    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle-pool (v1 and v2) box containing rate in R4
  
    val validOP = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
  
    val oraclePoolRate = oraclePoolBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  
    val selfOut = OUTPUTS(selfOutIndex)
  
    val validSelfOut = selfOut.tokens(0) == SELF.tokens(0) && // emissionNFT and quantity preserved
                     selfOut.propositionBytes == SELF.propositionBytes && // script preserved
                     selfOut.tokens(1)._1 == SELF.tokens(1)._1 && // dexyUSD tokenId preserved
                     selfOut.value > SELF.value // can only purchase dexyUSD, not sell it
                     
    val inTokens = SELF.tokens(1)._2
    val outTokens = selfOut.tokens(1)._2
  
    val deltaErgs = selfOut.value - SELF.value // deltaErgs must be (+)ve because ergs must increase
  
    val deltaTokens = inTokens - outTokens // outTokens must be < inTokens (see below)
  
    val validDelta = deltaErgs >= deltaTokens * oraclePoolRate // deltaTokens must be (+)ve, since both deltaErgs and oraclePoolRate are (+)ve
  
    validOP && validSelfOut && validDelta
  }
  
  val validTopping = INPUTS(0).tokens(0)._1 == swappingNFT
  
  sigmaProp(validEmission || validTopping)
}
```
## Liquidity Pool Contract

```scala
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
```
## Tracking Contract

```scala
{
  // Tracking box
  //   R4: Crossing Counter of LP box
  //   tokens(0): TrackingNFT 
  
  val thresholdPercent = 90 // 90% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
  val errorMargin = 3 // number of blocks where tracking error is allowed
  
  val lpNFT = fromBase64("Nho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify LP box for future use
  val oraclePoolNFT = fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box
  
  val lpBox = CONTEXT.dataInputs(0)
  val oraclePoolBox = CONTEXT.dataInputs(1)
  
  val validLpBox = lpBox.tokens(0)._1 == lpNFT
  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
  
  val tokenY    = lpBox.tokens(2)
  
  val reservesX = lpBox.value
  val reservesY = tokenY._2
  
  val lpRateXY  = reservesX / reservesY  // we can assume that reservesY > 0 (since at least one token must exist)
  
  val oraclePoolRateXY = oraclePoolBox.R4[Long].get
   
  val crossCounter = lpBox.R5[Int].get // stores how many times LP rate has crossed oracle pool rate (by cross, we mean going from above to below or vice versa)
  
  val successor = OUTPUTS(0)

  val validThreshold = lpRateXY * 100 < thresholdPercent * oraclePoolRateXY 
  
  val validSuccessor = successor.propositionBytes == SELF.propositionBytes && 
                       successor.tokens == SELF.tokens && 
                       successor.value >= SELF.value
  
  val validTracking = successor.R4[Int].get == crossCounter &&
                      successor.creationInfo._1 > (HEIGHT - errorMargin)
   
    sigmaProp(
      validLpBox &&
      validOraclePoolBox && 
      validThreshold &&
      validSuccessor &&
      validTracking
     )
}
```

## Swapping Contract

```scala
{  
  val waitingPeriod = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate 
  val emissionNFT = fromBase64("Bho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify LP box for future use
  val lpNFT = fromBase64("Nho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify LP box for future use
  val trackingNFT = fromBase64("Jho6UlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify LP box for future use
  val oraclePoolNFT = fromBase64("RytLYlBlU2hWbVlxM3Q2dzl6JEMmRilKQE1jUWZUalc=") // to identify oracle pool box
  
  val thresholdPercent = 90 // 90% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100) 
  
  val oraclePoolBox = CONTEXT.dataInputs(0)
  val trackingBox = CONTEXT.dataInputs(1)
  
  val lpBoxIn = INPUTS(0)
  val emissionBoxIn = INPUTS(1)

  val lpBoxOut = OUTPUTS(0)
  val emissionBoxOut = OUTPUTS(1)
  
  val successor = OUTPUTS(2) // SELF should be INPUTS(2)
  
  val tokenYIn    = lpBoxIn.tokens(2)
  val tokenYOut    = lpBoxOut.tokens(2)
  
  val reservesXIn = lpBoxIn.value
  val reservesYIn = tokenYIn._2
  
  val reservesXOut = lpBoxOut.value
  val reservesYOut = tokenYOut._2
  
  val lpRateXYIn  = reservesXIn / reservesYIn  // we can assume that reservesYIn > 0 (since at least one token must exist)
  val lpRateXYOut  = reservesXOut / reservesYOut  // we can assume that reservesYOut > 0 (since at least one token must exist)
  
  val oraclePoolRateXY = oraclePoolBox.R4[Long].get
   
  val validThreshold = lpRateXYIn * 100 < thresholdPercent * oraclePoolRateXY
   
  val validTrackingBox = trackingBox.tokens(0)._1 == trackingNFT 
  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT 
  val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT
  
  val validSuccessor = successor.propositionBytes == SELF.propositionBytes &&
                       successor.tokens == SELF.tokens &&
                       successor.value == SELF.value
  
  val validEmissionBoxIn = emissionBoxIn.tokens(0)._1 == emissionNFT 
  val validEmissionBoxOut = emissionBoxOut.tokens(0) == emissionBoxIn.tokens(0) &&
                            emissionBoxOut.tokens(1)._1 == emissionBoxIn.tokens(1)._1
  
  val deltaEmissionTokens =  emissionBoxOut.tokens(1)._2 - emissionBoxIn.tokens(1)._2
  val deltaEmissionErgs = emissionBoxIn.value - emissionBoxOut.value
  val deltaLpX = reservesXOut - reservesXIn
  val deltaLpY = reservesYIn - reservesYOut

  val validLpIn = lpBoxIn.R5[Int].get == trackingBox.R4[Int].get && // no change in cross-counter
                  trackingBox.creationInfo._1 < HEIGHT - waitingPeriod // at least waitingPeriod blocks have passed since the tracking started
                  
  val lpRateXYOutTimes100 = lpRateXYOut * 100
  
  val validSwap = lpRateXYOutTimes100 >= oraclePoolRateXY * 105 && // new rate must be >= 1.05 times oracle rate
                  lpRateXYOutTimes100 <= oraclePoolRateXY * 110 && // new rate must be <= 1.1 times oracle rate
                  deltaEmissionErgs <= deltaLpX && // ergs reduced in emission box must be <= ergs gained in LP 
                  deltaEmissionTokens >= deltaLpY && // tokens gained in emission box must be >= tokens reduced in LP 
                  validEmissionBoxIn &&
                  validEmissionBoxOut &&
                  validSuccessor &&
                  validLpBox &&
                  validOraclePoolBox &&
                  validTrackingBox &&
                  validThreshold &&
                  validLpIn
   
  sigmaProp(validSwap)
}
```
