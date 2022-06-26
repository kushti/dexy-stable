package ergoplatform.dexy

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._
import sigmastate.Values.ErgoTree
import utils.Configuration

object DexyToken {
  val oraclePoolNFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f" // TODO replace with actual
  val interventionNFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f" // TODO replace with actual
  val freeMintNFT = "011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f" // TODO replace with actual
  val lpNFT = "361A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val bankNFT = "361A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual

  val dexyUSDToken = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val LPToken = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
}

case class DexyAddresses(bankAddress: ErgoAddress, freeMintAddress: ErgoAddress, lpAddress: ErgoAddress, interventionAddress: ErgoAddress)

object DexyContracts {
  var dexyAddresses: DexyAddresses = _
  private lazy val bankScript =
    s"""{
       |  // This box: (dexyUSD bank box)
       |  //   tokens(0): bankNFT identifying the box
       |  //   tokens(1): dexyUSD tokens to be emitted
       |
       |  // Usually bank box will be spent as follows
       |  //
       |  //   Mint
       |  //   Input    |  Output
       |  // 0 Bank     |  Bank
       |  // 1 ...      |  ...
       |  //
       |  //   Intervention
       |  //   Input    |  Output
       |  // 0 LP       |  LP
       |  // 1 Bank     |  Bank
       |  // 2 ...      |  ...
       |  //
       |  val selfOutIndex = getVar[Int](0).get
       |
       |  val oraclePoolNFT = oraclePoolNFTId // to identify oracle pool box
       |  val interventionNFT = interventionNFTId // to identify intervention box for future use
       |  val freeMintNFT = freeMintNFTId
       |  val lpNFT = lpNFTId
       |
       |  val validMint = {
       |    val oraclePoolBox = CONTEXT.dataInputs(0) // oracle-pool (v1 and v2) box containing rate in R4
       |
       |    val lpBox = CONTEXT.dataInputs(1)
       |
       |    val validOP = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
       |
       |    val validLP = lpBox.tokens(0)._1 == lpNFT
       |
       |    val oraclePoolRate = oraclePoolBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
       |
       |    val lpReservesX = lpBox.value
       |
       |    val lpReservesY = lpBox.tokens(2)._2
       |
       |    val lpRate = lpReservesX / lpReservesY
       |
       |    val validRateFreeMint = 98 * lpRate < oraclePoolRate * 100 &&
       |                            oraclePoolRate * 100 < 102 * lpRate
       |
       |    val selfOut = OUTPUTS(selfOutIndex)
       |
       |    val validSelfOut = selfOut.tokens(0) == SELF.tokens(0) && // bankNFT and quantity preserved
       |                       selfOut.propositionBytes == SELF.propositionBytes && // script preserved
       |                       selfOut.tokens(1)._1 == SELF.tokens(1)._1 && // dexyUSD tokenId preserved
       |                       selfOut.value > SELF.value // can only purchase dexyUSD, not sell it
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
       |    val validFreeExtraLogic = INPUTS(1).tokens(0)._1 == freeMintNFT
       |
       |    validOP && validLP && validSelfOut && validDelta && validRateFreeMint && validFreeExtraLogic
       |  }
       |
       |  val validIntervention = INPUTS(2).tokens(0)._1 == interventionNFT
       |
       |  sigmaProp(validMint || validIntervention)
       |}
       |""".stripMargin

  // free mint box
  private lazy val freeMintScript =
    s"""{
       |  // this box: (free-mint box)
       |  //   tokens(0): Free-mint NFT
       |  //
       |  //   R4: (Int) height at which counter will reset
       |  //   R5: (Long) remaining stablecoins available to be purchased before counter is reset
       |
       |  val bankNFT = bankNFTId
       |  val lpNFT = lpNFTId
       |  val tFree = 100
       |
       |  val bankBoxIn = INPUTS(0)
       |  val lpBox = CONTEXT.dataInputs(1) // bank box requires dataInputs(0) to be oracle pool box, so 1 is LP box
       |
       |  val selfOutIndex = getVar[Int](0).get
       |  val bankOutIndex = getVar[Int](1).get
       |
       |  val selfOut = OUTPUTS(selfOutIndex)
       |  val bankBoxOut = OUTPUTS(bankOutIndex)
       |
       |  val selfInR4 = SELF.R4[Int].get
       |  val selfInR5 = SELF.R5[Long].get
       |  val selfOutR4 = selfOut.R4[Int].get
       |  val selfOutR5 = selfOut.R5[Long].get
       |
       |  val isCounterReset = HEIGHT > selfInR4
       |
       |  val dexyReserves = lpBox.tokens(2)._2
       |
       |  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
       |
       |  val availableToMint = if (isCounterReset) dexyReserves / 100 else selfInR5
       |
       |  val validAmount = dexyMinted <= availableToMint
       |
       |  val validSelfOutR4 = selfOutR4 == (if (isCounterReset) HEIGHT + tFree else selfInR4)
       |  val validSelfOutR5 = selfOutR5 == availableToMint - dexyMinted
       |
       |  val validBankBoxInOut = bankBoxIn.tokens(0)._1 == bankNFT && bankBoxOut.tokens(0)._1 == bankNFT
       |  val validLpBox = lpBox.tokens(0)._1 == lpNFT
       |  val validSelfOut = selfOut.tokens == SELF.tokens && // NFT preserved
       |                     selfOut.propositionBytes == SELF.propositionBytes && // script preserved
       |                     selfOut.value > SELF.value && validSelfOutR5 && validSelfOutR4
       |
       |  sigmaProp(validAmount && validBankBoxInOut && validLpBox && validSelfOut)
       |
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
       |    //   R5: Stores the height where oracle pool rate becomes lower than LP rate. Reset to Long.MaxValue when rate crossed back. Called crossTracker below
       |    //   Tokens(0): LP NFT to uniquely identify NFT box. (Could we possibly do away with this?)
       |    //   Tokens(1): LP tokens
       |    //   Tokens(2): Y tokens (Note that X tokens are NanoErgs (the value)
       |    //
       |    // Data Input #0: (oracle pool box)
       |    //   R4: Rate in units of X per unit of Y
       |    //   Token(0): OP NFT to uniquely identify Oracle Pool
       |
       |    // constants
       |    val threshold = 3 // error threshold in crossTracker
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
       |    val crossTrackerIn = SELF.R5[Int].get
       |    val crossTrackerOut = successor.R5[Int].get
       |
       |    val validCrossCounter = {
       |      if (isCrossing) {
       |        if (lpRateXY1 > oraclePoolRateXY) {
       |          crossTrackerOut >= HEIGHT - threshold
       |        } else crossTrackerOut == ${Long.MaxValue}L
       |      } else crossTrackerOut == crossTrackerIn
       |    }
       |
       |    val validRateForRedeemingLP = oraclePoolRateXY > lpRateXY0 * 98 / 100 // lpRate must be >= 0.98 * oraclePoolRate // these parameters need to be tweaked
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

  private lazy val interventionScript =
    s"""{
       |  val lastIntervention = SELF.creationInfo._1
       |  val buffer = 3 // error margin in height
       |  val T = 100 // from paper, gap between two interventions
       |  val T_int = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate
       |  val bankNFT = bankNFTId
       |  val lpNFT = lpNFTId
       |  val oraclePoolNFT = oraclePoolNFTId
       |
       |  val thresholdPercent = 98 // 98% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
       |
       |  val oraclePoolBox = CONTEXT.dataInputs(0)
       |
       |  val lpBoxIn = INPUTS(0)
       |  val bankBoxIn = INPUTS(1)
       |
       |  val lpBoxOut = OUTPUTS(0)
       |  val bankBoxOut = OUTPUTS(1)
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
       |  val validOraclePoolBox = oraclePoolBox.tokens(0)._1 == oraclePoolNFT
       |  val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT
       |
       |  val validSuccessor = successor.propositionBytes == SELF.propositionBytes &&
       |                       successor.tokens == SELF.tokens &&
       |                       successor.value == SELF.value &&
       |                       successor.creationInfo._1 >= HEIGHT - buffer
       |
       |  val validBankBoxIn = bankBoxIn.tokens(0)._1 == bankNFT
       |  val validBankBoxOut = bankBoxOut.tokens(0) == bankBoxIn.tokens(0) &&
       |                        bankBoxOut.tokens(1)._1 == bankBoxIn.tokens(1)._1
       |
       |  val validGap = lastIntervention < HEIGHT - T
       |
       |  val deltaBankTokens =  bankBoxOut.tokens(1)._2 - bankBoxIn.tokens(1)._2
       |  val deltaBankErgs = bankBoxIn.value - bankBoxOut.value
       |  val deltaLpX = reservesXOut - reservesXIn
       |  val deltaLpY = reservesYIn - reservesYOut
       |
       |  val validLpIn = lpBoxIn.R5[Int].get < HEIGHT - T_int // at least T_int blocks have passed since the tracking started
       |
       |  val lpRateXYOutTimes100 = lpRateXYOut * 100
       |
       |  val validSwap = lpRateXYOutTimes100 >= oraclePoolRateXY * 105 && // new rate must be >= 1.05 times oracle rate
       |                  lpRateXYOutTimes100 <= oraclePoolRateXY * 110 && // new rate must be <= 1.1 times oracle rate
       |                  deltaBankErgs <= deltaLpX && // ergs reduced in bank box must be <= ergs gained in LP
       |                  deltaBankTokens >= deltaLpY && // tokens gained in bank box must be >= tokens reduced in LP
       |                  validBankBoxIn &&
       |                  validBankBoxOut &&
       |                  validSuccessor &&
       |                  validLpBox &&
       |                  validOraclePoolBox &&
       |                  validThreshold &&
       |                  validLpIn &&
       |                  validGap
       |
       |  sigmaProp(validSwap)
       |}
       |""".stripMargin

  Configuration.ergoClient.execute(ctx => {
    val bankCon: ErgoContract = ctx.compileContract(
    ConstantsBuilder.create()
      .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
      .item("interventionNFTId", ErgoId.create(DexyToken.interventionNFT).getBytes)
      .item("freeMintNFTId", ErgoId.create(DexyToken.freeMintNFT).getBytes)
      .item("lpNFTId", ErgoId.create(DexyToken.lpNFT).getBytes)
      .build(),
      bankScript
    )
    lazy val bankErgoTree: ErgoTree = bankCon.getErgoTree
    lazy val bankAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(bankErgoTree).get

    val freeMintCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("bankNFTId", ErgoId.create(DexyToken.bankNFT).getBytes)
        .item("lpNFTId", ErgoId.create(DexyToken.lpNFT).getBytes)
        .build(),
      freeMintScript
    )
    lazy val freeMintErgoTree: ErgoTree = freeMintCon.getErgoTree
    lazy val freeMintAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(freeMintErgoTree).get

    val lpCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
        .build(),
      lpScript
    )
    lazy val lpErgoTree: ErgoTree = lpCon.getErgoTree
    lazy val lpAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(lpErgoTree).get

    val interventionCon: ErgoContract = ctx.compileContract(
      ConstantsBuilder.create()
        .item("oraclePoolNFTId", ErgoId.create(DexyToken.oraclePoolNFT).getBytes)
        .item("lpNFTId", ErgoId.create(DexyToken.lpNFT).getBytes)
        .item("bankNFTId", ErgoId.create(DexyToken.bankNFT).getBytes)
        .build(),
      interventionScript
    )
    lazy val interventionErgoTree: ErgoTree = interventionCon.getErgoTree
    lazy val interventionAddress: ErgoAddress = Configuration.addressEncoder.fromProposition(interventionErgoTree).get

    dexyAddresses = DexyAddresses(bankAddress, freeMintAddress, lpAddress, interventionAddress)
  })

}
