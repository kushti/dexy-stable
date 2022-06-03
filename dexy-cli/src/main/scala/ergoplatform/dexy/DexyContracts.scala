package ergoplatform.dexy

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._
import sigmastate.Values.ErgoTree
import utils.Configuration

object DexyToken {
  val oraclePoolNFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f" // TODO replace with actual
  val lpNFT = "361A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val trackingNFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val swappingNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val emissionNFT = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val dexyUSDToken = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val LPToken = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
}

case class DexyAddresses(emissionAddress: ErgoAddress, lpAddress: ErgoAddress, trackingAddress: ErgoAddress, swappingAddress: ErgoAddress)

object DexyContracts {
  var dexyAddresses: DexyAddresses = _

  private lazy val emissionScript =
    s"""{
       |  // This box: (dexyUSD emission box)
       |  //   tokens(0): emissionNFT identifying the box
       |  //   tokens(1): dexyUSD tokens to be emitted
       |
       |  val selfOutIndex = getVar[Int](0).get
       |
       |  val oraclePoolNFT = oraclePoolNFTId // to identify oracle pool box
       |  val swappingNFT = swappingNFTId // to identify LP box for future use
       |
       |  val validEmission = {
       |    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle-pool (v1 and v2) box containing rate in R4
       |
       |    val validOP = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
       |
       |    val oraclePoolRate = oraclePoolBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
       |
       |    val selfOut = OUTPUTS(selfOutIndex)
       |
       |    val validSelfOut = selfOut.tokens(0) == SELF.tokens(0) && // emissionNFT and quantity preserved
       |                     selfOut.propositionBytes == SELF.propositionBytes && // script preserved
       |                     selfOut.tokens(1)._1 == SELF.tokens(1)._1 && // dexyUSD tokenId preserved
       |                     selfOut.value > SELF.value // can only purchase dexyUSD, not sell it
       |
       |    val inTokens = SELF.tokens(1)._2
       |    val outTokens = selfOut.tokens(1)._2
       |
       |    val deltaErgs = selfOut.value - SELF.value // deltaErgs must be (+)ve because ergs must increase
       |
       |    val deltaTokens = inTokens - outTokens // outTokens must be < inTokens (see below)
       |
       |    val validDelta = deltaErgs >= deltaTokens * oraclePoolRate // deltaTokens must be (+)ve, since both deltaErgs and oraclePoolRate are (+)ve
       |
       |    validOP && validSelfOut && validDelta
       |  }
       |
       |  val validTopping = INPUTS(0).tokens(0)._1 == swappingNFT
       |
       |  sigmaProp(validEmission || validTopping)
       |}
       |""".stripMargin

  // below contract is adapted from N2T DEX contract in EIP-14 https://github.com/ergoplatform/eips/blob/de30f94ace1c18a9772e1dd0f65f00caf774eea3/eip-0014.md?plain=1#L558-L636
  private lazy val lpScript =
    s"""{
       |    // Notation:
       |    //
       |    // X is the primary token
       |    // Y is the secondary token
       |    // When using Erg-USD oracle v1, X is NanoErg and Y is USD
       |
       |    // This box: (LP box)
       |    //   R1 (value): X tokens in NanoErgs
       |    //   R4: How many LP in circulation (long). This can be non-zero when bootstrapping, to consider the initial token burning in UniSwap v2
       |    //   R5: Cross-counter. A counter to track how many times the rate has "crossed" the oracle pool rate. That is the oracle pool rate falls in between the before and after rates
       |    //   Tokens(0): LP NFT to uniquely identify NFT box. (Could we possibly do away with this?)
       |    //   Tokens(1): LP tokens
       |    //   Tokens(2): Y tokens (Note that X tokens are NanoErgs (the value)
       |    //
       |    // Data Input #0: (oracle pool box)
       |    //   R4: Rate in units of X per unit of Y
       |    //   Token(0): OP NFT to uniquely identify Oracle Pool
       |
       |    // constants
       |    val feeNum = 3 // 0.3 %
       |    val feeDenom = 1000
       |    val minStorageRent = 10000000L  // this many number of nanoErgs are going to be permanently locked
       |
       |    val successor = OUTPUTS(0) // copy of this box after exchange
       |    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle pool box
       |    val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFTId // to identify oracle pool box
       |
       |    val lpNFT0    = SELF.tokens(0)
       |    val reservedLP0 = SELF.tokens(1)
       |    val tokenY0     = SELF.tokens(2)
       |
       |    val lpNFT1    = successor.tokens(0)
       |    val reservedLP1 = successor.tokens(1)
       |    val tokenY1     = successor.tokens(2)
       |
       |    val supplyLP0 = SELF.R4[Long].get       // LP tokens in circulation in input LP box
       |    val supplyLP1 = successor.R4[Long].get  // LP tokens in circulation in output LP box
       |
       |    val validSuccessorScript = successor.propositionBytes == SELF.propositionBytes
       |
       |    val preservedLpNFT     = lpNFT1 == lpNFT0
       |    val validLP              = reservedLP1._1 == reservedLP0._1
       |    val validY               = tokenY1._1 == tokenY0._1
       |    val validSupplyLP1       = supplyLP1 >= 0
       |
       |    // since tokens can be repeated, we ensure for sanity that there are no more tokens
       |    val noMoreTokens         = successor.tokens.size == 3
       |
       |    val validStorageRent     = successor.value > minStorageRent
       |
       |    val reservesX0 = SELF.value
       |    val reservesY0 = tokenY0._2
       |    val reservesX1 = successor.value
       |    val reservesY1 = tokenY1._2
       |
       |    val oraclePoolRateXY = oraclePoolBox.R4[Long].get
       |    val lpRateXY0 = reservesX0 / reservesY0  // we can assume that reservesY0 > 0 (since at least one token must exist)
       |    val lpRateXY1 = reservesX1 / reservesY1  // we can assume that reservesY1 > 0 (since at least one token must exist)
       |    val isCrossing = (lpRateXY0 - oraclePoolRateXY) * (lpRateXY1 - oraclePoolRateXY) < 0 // if (and only if) oracle pool rate falls in between, then this will be negative
       |
       |    val crossCounterIn = SELF.R5[Int].get
       |    val crossCounterOut = successor.R5[Int].get
       |
       |    val validCrossCounter = crossCounterOut == {if (isCrossing) crossCounterIn + 1 else crossCounterIn}
       |
       |    val validRateForRedeemingLP = oraclePoolRateXY > lpRateXY0 * 9 / 10 // lpRate must be >= 0.9 oraclePoolRate // these parameters need to be tweaked
       |    // Do we need above if we also have the tracking contract?
       |
       |    val deltaSupplyLP  = supplyLP1 - supplyLP0
       |    val deltaReservesX = reservesX1 - reservesX0
       |    val deltaReservesY = reservesY1 - reservesY0
       |
       |    // LP formulae below using UniSwap v2 (with initial token burning by bootstrapping with positive R4)
       |    val validDepositing = {
       |        val sharesUnlocked = min(
       |            deltaReservesX.toBigInt * supplyLP0 / reservesX0,
       |            deltaReservesY.toBigInt * supplyLP0 / reservesY0
       |        )
       |        deltaSupplyLP <= sharesUnlocked
       |    }
       |
       |    val validRedemption = {
       |        val _deltaSupplyLP = deltaSupplyLP.toBigInt
       |        // note: _deltaSupplyLP, deltaReservesX and deltaReservesY are negative
       |        deltaReservesX.toBigInt * supplyLP0 >= _deltaSupplyLP * reservesX0 && deltaReservesY.toBigInt * supplyLP0 >= _deltaSupplyLP * reservesY0
       |    } && validRateForRedeemingLP
       |
       |    val validSwap =
       |        if (deltaReservesX > 0)
       |            reservesY0.toBigInt * deltaReservesX * feeNum >= -deltaReservesY * (reservesX0.toBigInt * feeDenom + deltaReservesX * feeNum)
       |        else
       |            reservesX0.toBigInt * deltaReservesY * feeNum >= -deltaReservesX * (reservesY0.toBigInt * feeDenom + deltaReservesY * feeNum)
       |
       |    val validAction =
       |        if (deltaSupplyLP == 0)
       |            validSwap
       |        else
       |            if (deltaReservesX > 0 && deltaReservesY > 0) validDepositing
       |            else validRedemption
       |
       |    sigmaProp(
       |        validSupplyLP1 &&
       |        validSuccessorScript &&
       |        validOraclePoolBox &&
       |        preservedLpNFT &&
       |        validLP &&
       |        validY &&
       |        noMoreTokens &&
       |        validAction &&
       |        validStorageRent &&
       |        validCrossCounter
       |    )
       |}
       |""".stripMargin

  private lazy val trackingScript =
    s"""{
       |  // Tracking box
       |  //   R4: Crossing Counter of LP box
       |  //   tokens(0): TrackingNFT
       |
       |  val thresholdPercent = 90 // 90% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
       |  val errorMargin = 3 // number of blocks where tracking error is allowed
       |
       |  val lpNFT = lpNFTId // to identify LP box for future use
       |  val oraclePoolNFT = oraclePoolNFTId // to identify oracle pool box
       |
       |  val lpBox = CONTEXT.dataInputs(0)
       |  val oraclePoolBox = CONTEXT.dataInputs(1)
       |
       |  val validLpBox = lpBox.tokens(0)._1 == lpNFT
       |  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
       |
       |  val tokenY    = lpBox.tokens(2)
       |
       |  val reservesX = lpBox.value
       |  val reservesY = tokenY._2
       |
       |  val lpRateXY  = reservesX / reservesY  // we can assume that reservesY > 0 (since at least one token must exist)
       |
       |  val oraclePoolRateXY = oraclePoolBox.R4[Long].get
       |
       |  val crossCounter = lpBox.R5[Int].get // stores how many times LP rate has crossed oracle pool rate (by cross, we mean going from above to below or vice versa)
       |
       |  val successor = OUTPUTS(0)
       |
       |  val validThreshold = lpRateXY * 100 < thresholdPercent * oraclePoolRateXY
       |
       |  val validSuccessor = successor.propositionBytes == SELF.propositionBytes &&
       |                       successor.tokens == SELF.tokens &&
       |                       successor.value >= SELF.value
       |
       |  val validTracking = successor.R4[Int].get == crossCounter &&
       |                      successor.creationInfo._1 > (HEIGHT - errorMargin)
       |
       |  sigmaProp(
       |    validLpBox &&
       |    validOraclePoolBox &&
       |    validThreshold &&
       |    validSuccessor &&
       |    validTracking
       |  )
       |}
       |""".stripMargin

  private lazy val swappingScript =
    s"""{
       |  val waitingPeriod = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate
       |  val emissionNFT = emissionNFTId // to identify LP box for future use
       |  val lpNFT = lpNFTId // to identify LP box for future use
       |  val trackingNFT = trackingNFTId // to identify LP box for future use
       |  val oraclePoolNFT = oraclePoolNFTId // to identify oracle pool box
       |
       |  val thresholdPercent = 90 // 90% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
       |
       |  val oraclePoolBox = CONTEXT.dataInputs(0)
       |  val trackingBox = CONTEXT.dataInputs(1)
       |
       |  val lpBoxIn = INPUTS(0)
       |  val emissionBoxIn = INPUTS(1)
       |
       |  val lpBoxOut = OUTPUTS(0)
       |  val emissionBoxOut = OUTPUTS(1)
       |
       |  val successor = OUTPUTS(2) // SELF should be INPUTS(2)
       |
       |  val tokenYIn    = lpBoxIn.tokens(2)
       |  val tokenYOut    = lpBoxOut.tokens(2)
       |
       |  val reservesXIn = lpBoxIn.value
       |  val reservesYIn = tokenYIn._2
       |
       |  val reservesXOut = lpBoxOut.value
       |  val reservesYOut = tokenYOut._2
       |
       |  val lpRateXYIn  = reservesXIn / reservesYIn  // we can assume that reservesYIn > 0 (since at least one token must exist)
       |  val lpRateXYOut  = reservesXOut / reservesYOut  // we can assume that reservesYOut > 0 (since at least one token must exist)
       |
       |  val oraclePoolRateXY = oraclePoolBox.R4[Long].get
       |
       |  val validThreshold = lpRateXYIn * 100 < thresholdPercent * oraclePoolRateXY
       |
       |  val validTrackingBox = trackingBox.tokens(0)._1 == trackingNFT
       |  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
       |  val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT
       |
       |  val validSuccessor = successor.propositionBytes == SELF.propositionBytes &&
       |                       successor.tokens == SELF.tokens &&
       |                       successor.value == SELF.value
       |
       |  val validEmissionBoxIn = emissionBoxIn.tokens(0)._1 == emissionNFT
       |  val validEmissionBoxOut = emissionBoxOut.tokens(0) == emissionBoxIn.tokens(0) &&
       |                            emissionBoxOut.tokens(1)._1 == emissionBoxIn.tokens(1)._1
       |
       |  val deltaEmissionTokens =  emissionBoxOut.tokens(1)._2 - emissionBoxIn.tokens(1)._2
       |  val deltaEmissionErgs = emissionBoxIn.value - emissionBoxOut.value
       |  val deltaLpX = reservesXOut - reservesXIn
       |  val deltaLpY = reservesYIn - reservesYOut
       |
       |  val validLpIn = lpBoxIn.R5[Int].get == trackingBox.R4[Int].get && // no change in cross-counter
       |                  trackingBox.creationInfo._1 < HEIGHT - waitingPeriod // at least waitingPeriod blocks have passed since the tracking started
       |
       |  val lpRateXYOutTimes100 = lpRateXYOut * 100
       |
       |  val validSwap = lpRateXYOutTimes100 >= oraclePoolRateXY * 105 && // new rate must be >= 1.05 times oracle rate
       |                  lpRateXYOutTimes100 <= oraclePoolRateXY * 110 && // new rate must be <= 1.1 times oracle rate
       |                  deltaEmissionErgs <= deltaLpX && // ergs reduced in emission box must be <= ergs gained in LP
       |                  deltaEmissionTokens >= deltaLpY && // tokens gained in emission box must be >= tokens reduced in LP
       |                  validEmissionBoxIn &&
       |                  validEmissionBoxOut &&
       |                  validSuccessor &&
       |                  validLpBox &&
       |                  validOraclePoolBox &&
       |                  validTrackingBox &&
       |                  validThreshold &&
       |                  validLpIn
       |
       |  sigmaProp(validSwap)
       |}
       |""".stripMargin

  Configuration.ergoClient.execute(ctx => {
    val emissionCon: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
      .item("swappingNFTId", ErgoId.create(DexyToken.trackingNFT).getBytes)
      .build(),
    emissionScript
    )
    lazy val emissionErgoTree: ErgoTree = emissionCon.getErgoTree
    lazy val emissionAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(emissionErgoTree).get


    val lpCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
        .build(),
      lpScript
    )
    lazy val lpErgoTree: ErgoTree = lpCon.getErgoTree
    lazy val lpAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(lpErgoTree).get


    val trackingCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("lpNFTId", ErgoId.create(DexyToken.lpNFT).getBytes)
        .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
        .build(),
      trackingScript
    )
    lazy val trackingErgoTree: ErgoTree = trackingCon.getErgoTree
    lazy val trackingAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(trackingErgoTree).get


    val swappingCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
        .item("lpNFTId", ErgoId.create(DexyToken.lpNFT).getBytes)
        .item("emissionNFTId", ErgoId.create(DexyToken.emissionNFT).getBytes)
        .item("trackingNFTId", ErgoId.create(DexyToken.trackingNFT).getBytes)
        .build(),
      swappingScript
    )
    lazy val swappingErgoTree: ErgoTree = swappingCon.getErgoTree
    lazy val swappingAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(swappingErgoTree).get

    dexyAddresses = DexyAddresses(emissionAddress, lpAddress, trackingAddress, swappingAddress)
  })

}
