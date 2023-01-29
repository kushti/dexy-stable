package dexy

import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import kiosk.script.ScriptUtil
import scorex.util.encode.Base64

object DexySpec {
  // tokens for main boxes
  val oracleNFT = "472B4B6250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val bankNFT = "861A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val lpNFT = "361A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val extractionNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A54" // TODO replace with actual
  val payoutNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A52" // TODO replace with actual
  val lpSwapNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A51" // TODO replace with actual
  val lpMintNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A50" // TODO replace with actual
  val lpRedeemNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A59" // TODO replace with actual

  val freeMintNFT = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val arbitrageMintNFT = "961A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual

  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking98NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val tracking95NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A55" // TODO replace with actual
  val tracking101NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A58" // TODO replace with actual

  // High level idea:
  // There are 3 main boxes in the protocol, and the others are auxiliary boxes to manage the main boxes
  // Main boxes:
  //   1. Bank box that emits Dexy tokens
  //   2. Liquidity pool (LP) box that allows swapping Dexy with Ergs
  //   3. Oracle (pool) box that has the rate of Erg/USD in R4 (Long) in units nanoErgs per USD

  lazy val initialDexyTokens = 10000000000000L

  val initialLp =
    100000000000L // initially how many Lp minted (and we need to add that many to tokens(1), after removing some for token burning as in UniSwap v2)

  val feeNumLp = 3
  val feeDenomLp = 1000

  lazy val bankScript =
    s"""{
       |  // This box: (Bank box)
       |  //
       |  // TOKENS
       |  //   tokens(0): bankNFT identifying the box
       |  //   tokens(1): dexyUSD tokens to be emitted
       |  // REGISTERS
       |  //   None
       |  //
       |  // TRANSACTIONS
       |  //
       |  // [1] Arbitrage Mint
       |  //   Input         |  Output         |   Data-Input
       |  // ------------------------------------------------
       |  // 0 ArbitrageMint |  ArbitrageMint  |   Oracle
       |  // 1 Bank          |  Bank           |   LP
       |  //
       |  // [2] Free Mint
       |  //   Input    |  Output   |   Data-Input
       |  // -------------------------------------
       |  // 0 FreeMint |  FreeMint |   Oracle
       |  // 1 Bank     |  Bank     |   LP
       |  //
       |  // [3] Intervention
       |  //   Input         |  Output        |   Data-Input
       |  // -----------------------------------------------
       |  // 0 LP            |  LP            |   Oracle
       |  // 1 Bank          |  Bank          |   Tracking (98%)
       |  // 2 Intervention  |  Intervention  |
       |  //
       |  // [4] Payout
       |  //   Input         |  Output        |   Data-Input
       |  // -----------------------------------------------
       |  // 0 Payout        |  Payout        |   Oracle
       |  // 1 Bank          |  Bank          |   LP
       |  // 2               |  Reward        |
       |
       |  // This box emits DexyUSD. The contract only enforces some basic rules (such as the contract and token Ids) are preserved.
       |  // It does not does not encode the emission logic. It just requires certain boxes in the inputs to contain certain NFTs.
       |  // Those boxes in turn encode the emission logic (and logic for other auxiliary flows, such as intervention).
       |  // The minting logic (that emits Dexy tokens) is encoded in the FreeMint and ArbitrageMint boxes
       |
       |  // Oracle data:
       |  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format
       |
       |  // inputs indices
       |  val mintInIndex = 0         // 1st input is mint (or LP box in case of intervention, which we ensure in intervention box)
       |  val interventionInIndex = 2 // 3rd input is intervention box
       |  val payoutInIndex = 0       // 1st input is payout box
       |
       |  // outputs indices
       |  val selfOutIndex = 1        // 2nd output is self copy
       |
       |  val freeMintNFT = fromBase64("${Base64.encode(freeMintNFT.decodeHex)}")
       |  val arbitrageMintNFT = fromBase64("${Base64.encode(arbitrageMintNFT.decodeHex)}")
       |  val interventionNFT = fromBase64("${Base64.encode(interventionNFT.decodeHex)}")
       |  val payoutNFT = fromBase64("${Base64.encode(payoutNFT.decodeHex)}")
       |
       |  val successor = OUTPUTS(selfOutIndex)
       |
       |  val validSuccessor = successor.tokens(0) == SELF.tokens(0)                && // NFT preserved
       |                       successor.propositionBytes == SELF.propositionBytes  && // script preserved
       |                       successor.tokens(1)._1 == SELF.tokens(1)._1             // dexyUSD token Id preserved (but amount will change)
       |
       |  val validMint = INPUTS(mintInIndex).tokens(0)._1 == freeMintNFT        ||
       |                  INPUTS(mintInIndex).tokens(0)._1 == arbitrageMintNFT
       |
       |  val validIntervention = INPUTS(interventionInIndex).tokens(0)._1 == interventionNFT
       |
       |  val validPayout = INPUTS(payoutInIndex).tokens(0)._1 == payoutNFT
       |
       |  sigmaProp(validSuccessor && (validMint || validIntervention || validPayout))
       |}
       |""".stripMargin

  // arbitrage mint box
  val arbitrageMintScript =
    s"""{
       |  // This box: (arbitrage-mint box)
       |  //
       |  // TOKENS
       |  //   tokens(0): Arbitrage-mint NFT
       |  //
       |  // REGISTERS
       |  //   R4: (Int) height at which counter will reset
       |  //   R5: (Long) remaining Dexy tokens available to be purchased before counter is reset
       |  //
       |  // TRANSACTIONS
       |  //
       |  // [1] Arbitrage Mint
       |  //   Input         |  Output         |   Data-Input
       |  // ------------------------------------------------
       |  // 0 ArbitrageMint |  ArbitrageMint  |   Oracle
       |  // 1 Bank          |  Bank           |   LP
       |  //                 |                 |   Tracking101box
       |
       |  // Oracle data:
       |  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format
       |
       |  // input indices
       |  val bankInIndex = 1
       |
       |  // output indices
       |  val selfOutIndex = 0
       |  val bankOutIndex = 1
       |
       |  // data input indices
       |  val oracleBoxIndex = 0
       |  val lpBoxIndex = 1
       |  val tracking101BoxIndex = 2
       |
       |  val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}") // to identify oracle pool box
       |  val bankNFT = fromBase64("${Base64.encode(bankNFT.decodeHex)}")
       |  val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}")
       |  val tracking101NFT = fromBase64("${Base64.encode(tracking101NFT.decodeHex)}") 
       |  
       |  
       |  val T_arb = 30 // 30 blocks = 1 hour
       |  val T_buffer = 5 // max delay permitted after broadcasting and confirmation of the tx spending this box
       |  val thresholdPercent = 101 // 101% or more value (of LP in terms of OraclePool) will trigger action
       |
       |  val feeNum = 5
       |  val feeDenom = 1000
       |  // actual fee ratio is feeNum / feeDenom
       |  // example if feeNum = 5 and feeDenom = 1000 then fee = 0.005 = 0.5 %
       |
       |  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex) // oracle-pool (v1 and v2) box containing rate in R4
       |  val lpBox = CONTEXT.dataInputs(lpBoxIndex)
       |  val tracking101Box = CONTEXT.dataInputs(tracking101BoxIndex)
       |  
       |  val tracking101Height = tracking101Box.R7[Int].get
       |  
       |  val bankBoxIn = INPUTS(bankInIndex)
       |
       |  val successor = OUTPUTS(selfOutIndex)
       |  val bankBoxOut = OUTPUTS(bankOutIndex)
       |
       |  val selfInR4 = SELF.R4[Int].get
       |  val selfInR5 = SELF.R5[Long].get
       |  val successorR4 = successor.R4[Int].get
       |  val successorR5 = successor.R5[Long].get
       |
       |  val isCounterReset = HEIGHT > selfInR4
       |
       |  val oracleRateWithoutFee = oracleBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
       |  val oracleRateWithFee = oracleRateWithoutFee * (feeNum + feeDenom) / feeDenom
       |
       |  val lpReservesX = lpBox.value
       |  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves
       |  val lpRate = lpReservesX / lpReservesY
       |
       |  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
       |  val ergsAdded = bankBoxOut.value - bankBoxIn.value
       |  val validDelta = ergsAdded >= dexyMinted * oracleRateWithFee && ergsAdded > 0 // dexyMinted must be (+)ve, since both ergsAdded and oracleRateWithFee are (+)ve
       |
       |  val maxAllowedIfReset = (lpReservesX - oracleRateWithFee * lpReservesY) / oracleRateWithFee
       |
       |  // above formula:
       |  // Before mint rate is lpReservesX / lpReservesY, which should be greater than oracleRateWithFee
       |  // After mint rate is lpReservesX / (lpReservesY + dexyMinted), which should be same or less than than oracleRateWithFee
       |  //  Thus:
       |  //   lpReservesX / lpReservesY > oracleRateWithFee
       |  //   lpReservesX / (lpReservesY + dexyMinted) <= oracleRateWithFee
       |  // above gives min value of dexyMinted = (lpReservesX - oracleRateWithFee * lpReservesY) / oracleRateWithFee
       |
       |  val availableToMint = if (isCounterReset) maxAllowedIfReset else selfInR5
       |
       |  val validAmount = dexyMinted <= availableToMint
       |
       |  val validSuccessorR4 = if (!isCounterReset) {
       |    successorR4 == selfInR4
       |  } else { // set R4 to HEIGHT_AT_BROADCAST + T_arb + T_buffer
       |    successorR4 >= HEIGHT + T_arb &&
       |    successorR4 <= HEIGHT + T_arb + T_buffer
       |  }
       |
       |  val validSuccessorR5 = successorR5 == availableToMint - dexyMinted
       |
       |  val validBankBoxInOut = bankBoxIn.tokens(0)._1 == bankNFT
       |  val validLpBox = lpBox.tokens(0)._1 == lpNFT
       |  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
       |  val validSuccessor = successor.tokens == SELF.tokens                     && // NFT preserved
       |                       successor.propositionBytes == SELF.propositionBytes && // script preserved
       |                       successor.value >= SELF.value                       &&
       |                       validSuccessorR5                                    &&
       |                       validSuccessorR4
       |
       |  val validDelay = tracking101Height < HEIGHT - T_arb // at least T_arb blocks have passed since the tracking started
       |  val validThreshold = lpRate * 100 > thresholdPercent * oracleRateWithFee
       |
       |  sigmaProp(validDelay && validThreshold && validAmount && validBankBoxInOut && validLpBox && validOracleBox && validSuccessor && validDelta)
       |}
       |""".stripMargin

  // free mint box
  val freeMintScript =
    s"""{
       |  // This box: (free-mint box)
       |  //
       |  // TOKENS
       |  //   tokens(0): Free-mint NFT
       |  //
       |  // REGISTERS
       |  //   R4: (Int) height at which counter will reset
       |  //   R5: (Long) remaining Dexy tokens available to be purchased before counter is reset
       |  //
       |  // TRANSACTIONS
       |  // [1] Free Mint
       |  //   Input    |  Output   |   Data-Input
       |  // -------------------------------------
       |  // 0 FreeMint |  FreeMint |   Oracle
       |  // 1 Bank     |  Bank     |   LP
       |
       |
       |  // Oracle data:
       |  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format
       |
       |  // inputs indices
       |  val bankInIndex = 1
       |
       |  // outputs indices
       |  val selfOutIndex = 0
       |  val bankOutIndex = 1
       |
       |  // data inputs indices
       |  val oracleBoxIndex = 0
       |  val lpBoxIndex = 1
       |
       |  val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}") // to identify oracle pool box
       |  val bankNFT = fromBase64("${Base64.encode(bankNFT.decodeHex)}")
       |  val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}")
       |
       |  val T_free = 100
       |  val T_buffer = 5 // max delay permitted after broadcasting and confirmation of the tx spending this box
       |
       |  val feeNum = 10
       |  val feeDenom = 1000
       |  // actual fee ratio is feeNum / feeDenom
       |  // example if feeNum = 10 and feeDenom = 1000 then fee = 0.01 = 1 %
       |
       |  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex) // oracle-pool (v1 and v2) box containing rate in R4
       |  val lpBox = CONTEXT.dataInputs(lpBoxIndex)
       |  val bankBoxIn = INPUTS(bankInIndex)
       |
       |  val successor = OUTPUTS(selfOutIndex)
       |  val bankBoxOut = OUTPUTS(bankOutIndex)
       |
       |  val selfInR4 = SELF.R4[Int].get
       |  val selfInR5 = SELF.R5[Long].get
       |  val successorR4 = successor.R4[Int].get
       |  val successorR5 = successor.R5[Long].get
       |
       |  val isCounterReset = HEIGHT > selfInR4
       |
       |  val oracleRateWithoutFee = oracleBox.R4[Long].get // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
       |  val oracleRateWithFee = oracleRateWithoutFee * (feeNum + feeDenom) / feeDenom
       |
       |  val lpReservesX = lpBox.value
       |  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves
       |  val lpRate = lpReservesX / lpReservesY
       |
       |  val validRateFreeMint = 98 * lpRate < oracleRateWithoutFee * 100 &&
       |                          oracleRateWithoutFee * 100 < 102 * lpRate
       |
       |  val dexyMinted = bankBoxIn.tokens(1)._2 - bankBoxOut.tokens(1)._2
       |  val ergsAdded = bankBoxOut.value - bankBoxIn.value
       |  val validDelta = ergsAdded >= dexyMinted * oracleRateWithFee && ergsAdded > 0 // dexyMinted must be (+)ve, since both ergsAdded and oracleRateWithFee are (+)ve
       |
       |  val maxAllowedIfReset = lpReservesY / 100
       |
       |  val availableToMint = if (isCounterReset) maxAllowedIfReset else selfInR5
       |
       |  val validAmount = dexyMinted <= availableToMint
       |
       |  val validSuccessorR4 = if (!isCounterReset) {
       |    successorR4 == selfInR4
       |  } else { // set R4 to HEIGHT_AT_BROADCAST + T_free + T_buffer
       |    successorR4 >= HEIGHT + T_free &&
       |    successorR4 <= HEIGHT + T_free + T_buffer
       |  }
       |  val validSuccessorR5 = successorR5 == availableToMint - dexyMinted
       |
       |  val validBankBoxInOut = bankBoxIn.tokens(0)._1 == bankNFT
       |  val validLpBox = lpBox.tokens(0)._1 == lpNFT
       |  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
       |  val validSuccessor = successor.tokens == SELF.tokens                     && // NFT preserved
       |                       successor.propositionBytes == SELF.propositionBytes && // script preserved
       |                       successor.value >= SELF.value                       &&
       |                       validSuccessorR5                                    &&
       |                       validSuccessorR4
       |
       |  sigmaProp(validAmount && validBankBoxInOut && validLpBox && validOracleBox && validSuccessor && validDelta && validRateFreeMint)
       |}
       |""".stripMargin

  // payout box
  lazy val payoutScript =
    s"""{
       |  // This box: (payout box)
       |  //
       |  // TOKENS
       |  //   tokens(0): Payout NFT
       |  //
       |  // REGISTERS
       |  //   R4: (Coll[Byte]) payout script hash
       |  //
       |  // TRANSACTIONS
       |  // [1] Payout
       |  //   Input    |  Output   |   Data-Input
       |  // -------------------------------------
       |  // 0 Payout   |  Payout   |   Oracle
       |  // 1 Bank     |  Bank     |   LP
       |  // 2          |  Reward   |
       |
       |  // In the above transaction, the "payouts" (rewards) will be stored in a "Reward" box
       |  // The payout box just enforces the correct logic for such rewards and does not store the actual rewards
       |  // The reward box must be protected by a script whose hash is stored in R4 of the payout box
       |
       |  val payoutThreshold = 100000000000000L // nanoErgs (100000 Ergs)
       |  val maxPayOut = 100000000000L // 100 Ergs
       |  val minPayOut = 10000000000L  // 10 Ergs
       |
       |  val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}") // to identify oracle pool box
       |  val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}")
       |
       |  // inputs indices
       |  val bankInIndex = 1
       |
       |  // outputs indices
       |  val selfOutIndex = 0
       |  val bankOutIndex = 1
       |  val rewardOutIndex = 2
       |
       |  // data inputs indices
       |  val oracleIndex = 0
       |  val lpIndex = 1
       |
       |  val bankBoxIn = INPUTS(bankInIndex)
       |
       |  val bankBoxOut = OUTPUTS(bankOutIndex)
       |  val successor = OUTPUTS(selfOutIndex)
       |  val rewardBoxOut = OUTPUTS(rewardOutIndex)
       |
       |  val oracleBox = CONTEXT.dataInputs(oracleIndex)
       |  val lpBox = CONTEXT.dataInputs(lpIndex)
       |
       |  val validOracle = oracleBox.tokens(0)._1 == oracleNFT
       |  val validLP = lpBox.tokens(0)._1 == lpNFT
       |
       |  val payoutScriptHash = SELF.R4[Coll[Byte]].get // payout script hash
       |  val successorR4 = successor.R4[Coll[Byte]].get // should be same as selfR4
       |
       |  val lpReservesX = lpBox.value
       |  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves
       |
       |  val bankDexy = bankBoxIn.tokens(1)._2
       |
       |  val ergsRemoved = bankBoxOut.value - bankBoxIn.value
       |  val ergsTaken = rewardBoxOut.value
       |
       |  val oracleRate = oracleBox.R4[Long].get // nanoErgs per USD
       |
       |  val lpRate = lpReservesX / lpReservesY
       |
       |  val dexyInCirculation = ${initialDexyTokens}L - bankDexy
       |
       |  // We have the parameter payoutThreshold for ensuring that the bank has at least that many ergs
       |  // before payout. However, we need to also ensure that there are enough ergs in the bank to
       |  // cover the "worst case scenario" described in  section "4 Worst Scenario and Bank Reserves" in paper,
       |  // The following notation is used
       |  val O = dexyInCirculation // (note: capital o, not zero)
       |  val p = lpRate // initial rate
       |  val s = oracleRate // final lower rate after crash
       |  val e = lpReservesX // Ergs in LP
       |  val u = lpReservesY // Dexy in LP
       |
       |  // we also use the symbol b = bankBoxOut.value (Remaining (nano)Ergs in bank)
       |  val b = bankBoxOut.value
       |
       |  // We want finalErgs in bank, b > sqrt(e * u / s) - e + O / s
       |  // or                         b + e - O / s > sqrt(e * u / s)
       |  // let x = b + e - O / s
       |  // then we need               x ^ 2 > e * u / s
       |
       |  val x = b.toBigInt + e - O / s
       |
       |  val y = e.toBigInt * u / s
       |
       |  val handledWorstCase = x * x > y
       |
       |  // no need to validate bank NFT here
       |  val validBank = bankBoxOut.propositionBytes == bankBoxIn.propositionBytes && // script preserved
       |                  bankBoxOut.tokens == bankBoxIn.tokens                     && // tokens preserved
       |                  ergsRemoved == ergsTaken
       |
       |  val validSuccessor = successor.propositionBytes == SELF.propositionBytes && // script preserved
       |                       successor.tokens == SELF.tokens                     && // NFT preserved
       |                       successor.value >= SELF.value                       && // Ergs preserved or increased
       |                       successor.R4[Coll[Byte]].get == payoutScriptHash
       |
       |  val validPayout = blake2b256(rewardBoxOut.propositionBytes) == payoutScriptHash && // script of reward box is correct
       |                    bankBoxIn.value >= payoutThreshold                            && // bank box must had large balance
       |                    ergsTaken >= minPayOut                                        && // cannot take too little (dust, etc)
       |                    ergsTaken <= maxPayOut                                           // cannot take too much
       |
       |  sigmaProp(validBank && validSuccessor && validPayout && validOracle && validLP && handledWorstCase)
       |}
       |""".stripMargin

  // below contract is adapted from N2T DEX contract in EIP-14 https://github.com/ergoplatform/eips/blob/de30f94ace1c18a9772e1dd0f65f00caf774eea3/eip-0014.md?plain=1#L558-L636
  lazy val lpScript =
    s"""{
       |    // This box: (LP box)
       |    //
       |    // TOKENS
       |    //   Tokens(0): NFT to uniquely identify LP box.
       |    //   Tokens(1): LP tokens
       |    //   Tokens(2): Y tokens, the Dexy tokens (Note that X tokens are NanoErgs (the value)
       |    //
       |    // TRANSACTIONS
       |    //
       |    // [1] Intervention
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |   Oracle
       |    // 1 Bank          |  Bank          |
       |    // 2 Intervention  |  Intervention  |
       |    //
       |    // [2] Swap
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |
       |    // 1 Swap          |  Swap          |
       |    //
       |    // [3] Redeem LP tokens
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |   Oracle
       |    // 1 Redeem        |  Redeem
       |    //
       |    // [4] Mint LP tokens
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |
       |    // 1 Mint          |  Mint
       |    //
       |    // [5] Extract to future
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |   Oracle
       |    // 1 Extract       |  Extract       |   Bank
       |    // 2               |                |   Tracking (95%)
       |    //
       |    // [6] Release extracted to future tokens
       |    //   Input         |  Output        |   Data-Input
       |    // -----------------------------------------------
       |    // 0 LP            |  LP            |   Oracle
       |    // 1 Extract       |  Extract       |   Tracking (101%)
       |    //
       |    // -------------------------------------------------------------
       |    // Notation:
       |    //
       |    // X is the primary token
       |    // Y is the secondary token
       |    // In DexyUSD, X is NanoErg and Y is USD
       |
       |
       |    // inputs
       |    val interventionBoxIndex = 2   
       |    val extractBoxIndex = 1
       |    val lpActionBoxIndex = 1 // swap/redeem/mint
       |
       |    // outputs
       |    val selfOutIndex = 0
       |
       |    val interventionNFT = fromBase64("${Base64.encode(interventionNFT.decodeHex)}")
       |    val extractionNFT = fromBase64("${Base64.encode(extractionNFT.decodeHex)}")
       |    val swapNFT = fromBase64("${Base64.encode(lpSwapNFT.decodeHex)}")
       |    val mintNFT = fromBase64("${Base64.encode(lpMintNFT.decodeHex)}")
       |    val redeemNFT = fromBase64("${Base64.encode(lpRedeemNFT.decodeHex)}")
       |
       |    val interventionBox = INPUTS(interventionBoxIndex)
       |    val extractBox = INPUTS(extractBoxIndex)
       |    val swapBox = INPUTS(lpActionBoxIndex)
       |    val mintBox = INPUTS(lpActionBoxIndex)
       |    val redeemBox = INPUTS(lpActionBoxIndex)
       |
       |    val successor = OUTPUTS(selfOutIndex) // copy of this box after exchange
       |
       |    val validSwap      = swapBox.tokens(0)._1 == swapNFT
       |    val validMint      = mintBox.tokens(0)._1 == mintNFT
       |    val validRedeem    = redeemBox.tokens(0)._1 == redeemNFT
       |
       |    val validIntervention = interventionBox.tokens.size > 0 && interventionBox.tokens(0)._1 == interventionNFT
       |    val validExtraction   = extractBox.tokens(0)._1 == extractionNFT
       |    
       |    val lpNftIn      = SELF.tokens(0)
       |    val lpReservesIn = SELF.tokens(1)
       |    val tokenYIn     = SELF.tokens(2)
       |
       |    val lpNftOut      = successor.tokens(0)
       |    val lpReservesOut = successor.tokens(1)
       |    val tokenYOut     = successor.tokens(2)
       |
       |    val preservedScript      = successor.propositionBytes == SELF.propositionBytes
       |    val preservedLpNft       = lpNftIn == lpNftOut
       |    val preservedLpTokenId   = lpReservesOut._1 == lpReservesIn._1
       |    val preservedDexyTokenId = tokenYOut._1 == tokenYIn._1
       |
       |    // Note:
       |    //    supplyLpIn = initialLp - lpReservesIn._2
       |    //    supplyLpOut = initialLp - lpReservesOut._2
       |    // Thus:
       |    //    deltaSupplyLp = supplyLpOut - supplyLpIn
       |    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
       |    //                  = lpReservesIn._2 - lpReservesOut._2
       |
       |    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2
       |
       |    // since tokens can be repeated, we ensure for sanity that there are no more tokens
       |    val noMoreTokens         = successor.tokens.size == 3
       |  
       |    val lpAction = validSwap || validMint || validRedeem
       |
       |    val dexyAction = (validIntervention || validExtraction) &&
       |                      deltaSupplyLp == 0 // ensure Lp tokens are not extracted during dexyAction
       |    sigmaProp(
       |        preservedScript           &&
       |        preservedLpNft            &&
       |        preservedLpTokenId        &&
       |        preservedDexyTokenId      &&
       |        noMoreTokens              &&
       |        (lpAction || dexyAction)
       |    )
       |}
       |""".stripMargin

  val lpSwapScript =
    s"""{   // This box: (LP Swap box)
       |    //
       |    // TOKENS
       |    //   Tokens(0): NFT to uniquely identify this box
       |
       |    //
       |    // valid swap is as follows
       |    //
       |    // the value feeNum / feeDenom is the fraction of fee
       |    // for example if feeNum = 3 and feeDenom = 1000 then fee is 0.003 = 0.3%
       |
       |    // Note "sold" = sold by user (and added to LP, thus the reserves of LP box of sold currency will increase)
       |    // Fee is taken as follows:
       |    //  if amount sold by user is s then fee is taken out from s when calculating currency purchased by user
       |    //  As an example:
       |    //    feeNum = 3
       |    //    feeDenom = 1000
       |    //    (thus, fee is 0.3 %)
       |    //
       |    //  1. deltaSold = sold (must be > 0)
       |    //  2. soldOut = soldIn + deltaSold
       |    //  3. actualSold = sold * (1 - num/denom)
       |    //  4. actualBought = actualSold * rate
       |    //  5. boughtOut = boughtIn - actualBought
       |    //  The condition we enforce is boughtOut >= boughtIn - deltaBought
       |
       |    // Thus, if we are selling X (i.e. NanoErgs, and buying Dexy, so that deltaErgs > 0)
       |    // actualDeltaErgs = deltaReservesX * (1 - feeNum / feeDenom)
       |    // rate = reservesYIn / reservesXIn
       |    // deltaReservesY >= - actualDeltaErgs * rate
       |    // or
       |    // deltaReservesY >= - actualDeltaErgs * reservesYIn / reservesXIn
       |    // or
       |    // deltaReservesY >= - deltaReservesX * (1 - feeNum / feeDenom) * reservesYIn / reservesXIn
       |    // deltaReservesY * reservesXIn >= - deltaReservesX * (1 - feeNum / feeDenom) * reservesYIn
       |    // deltaReservesY * reservesXIn * feeDenom >= - deltaReservesX * (feeDenom - feeNum) * reservesYIn
       |    // deltaReservesY * reservesXIn * feeDenom >= deltaReservesX * (feeNum - feeDenom) * reservesYIn
       |
       |    val feeNum = ${feeNumLp}L // 0.3 % if feeNum is 3 and feeDenom is 1000
       |    val feeDenom = ${feeDenomLp}L
       |
       |    val lpBoxInIndex = 0
       |    val lpBoxOutIndex = 0
       |
       |    val selfOutIndex = 1
       |    val successor = OUTPUTS(selfOutIndex)
       |
       |    val lpBoxIn = INPUTS(lpBoxInIndex)
       |    val lpBoxOut = OUTPUTS(lpBoxOutIndex)
       |
       |    val lpReservesIn = lpBoxIn.tokens(1)
       |    val lpReservesOut = lpBoxOut.tokens(1)
       |
       |    val reservesXIn = lpBoxIn.value
       |    val reservesYIn = lpBoxIn.tokens(2)._2
       |
       |    val reservesXOut = lpBoxOut.value
       |    val reservesYOut = lpBoxOut.tokens(2)._2
       |
       |    // Note:
       |    //    supplyLpIn = initialLp - lpReservesIn._2
       |    //    supplyLpOut = initialLp - lpReservesOut._2
       |    // Thus:
       |    //    deltaSupplyLp = supplyLpOut - supplyLpIn
       |    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
       |    //                  = lpReservesIn._2 - lpReservesOut._2
       |
       |    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2
       |    val deltaReservesX = reservesXOut - reservesXIn
       |    val deltaReservesY = reservesYOut - reservesYIn
       |
       |    val validSwap =
       |      deltaSupplyLp == 0 && (
       |        if (deltaReservesX > 0)
       |           deltaReservesY.toBigInt * reservesXIn * feeDenom >= deltaReservesX.toBigInt * (feeNum - feeDenom) * reservesYIn
       |        else
       |           deltaReservesX.toBigInt * reservesYIn * feeDenom >= deltaReservesY.toBigInt * (feeNum - feeDenom) * reservesXIn
       |      )
       |
       |    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
       |                        successor.value >= SELF.value                        &&
       |                        successor.tokens == SELF.tokens
       |
       |    sigmaProp(validSwap && selfPreserved)
       |}
       |""".stripMargin

  val lpMintScript =
    s"""{   // This box: (LP Mint box)
       |    //
       |    // TOKENS
       |    //   Tokens(0): NFT to uniquely identify this box
       |
       |    val lpBoxInIndex = 0
       |    val lpBoxOutIndex = 0
       |
       |    val selfOutIndex = 1
       |    val successor = OUTPUTS(selfOutIndex)
       |
       |    val lpBoxIn = INPUTS(lpBoxInIndex)
       |    val lpBoxOut = OUTPUTS(lpBoxOutIndex)
       |
       |    val lpReservesIn = lpBoxIn.tokens(1)
       |    val lpReservesOut = lpBoxOut.tokens(1)
       |
       |    val reservesXIn = lpBoxIn.value
       |    val reservesYIn = lpBoxIn.tokens(2)._2
       |
       |    val reservesXOut = lpBoxOut.value
       |    val reservesYOut = lpBoxOut.tokens(2)._2
       |
       |    val supplyLpIn = ${initialLp}L - lpReservesIn._2
       |
       |    // Note:
       |    //    supplyLpIn = initialLp - lpReservesIn._2
       |    //    supplyLpOut = initialLp - lpReservesOut._2
       |    // Thus:
       |    //    deltaSupplyLp = supplyLpOut - supplyLpIn
       |    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
       |    //                  = lpReservesIn._2 - lpReservesOut._2
       |
       |    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2
       |    val deltaReservesX = reservesXOut - reservesXIn
       |    val deltaReservesY = reservesYOut - reservesYIn
       |
       |    // LP formulae below using UniSwap v2 (with initial token burning by bootstrapping with positive R4)
       |    val validMintLp = deltaSupplyLp > 0 && deltaReservesX > 0 && deltaReservesY > 0 && {
       |        val sharesUnlocked = min(
       |            deltaReservesX.toBigInt * supplyLpIn / reservesXIn,
       |            deltaReservesY.toBigInt * supplyLpIn / reservesYIn
       |        )
       |        deltaSupplyLp <= sharesUnlocked
       |    }
       |
       |    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
       |                        successor.value >= SELF.value                        &&
       |                        successor.tokens == SELF.tokens
       |
       |    sigmaProp(validMintLp && selfPreserved)
       |}
       |""".stripMargin

  val lpRedeemScript =
    s"""{   // This box: (LP Redeem box)
       |    //
       |    // TOKENS
       |    //   Tokens(0): NFT to uniquely identify this box
       |
       |    val initialLp = ${initialLp}L   // How many LP initially minted. Used to compute Lp in circulation (supply Lp).
       |    // Note that at bootstrap, we may have initialLp > tokens stored in LP box quantity to consider the initial token burning in UniSwap v2
       |
       |    val lpBoxInIndex = 0 // input
       |    val oracleBoxIndex = 0 // data input
       |    val lpBoxOutIndex = 0 // output
       |    val selfOutIndex = 1 // output
       |
       |    val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}") // to identify oracle pool box
       |
       |    val lpBoxIn = INPUTS(lpBoxInIndex)
       |
       |    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
       |    val lpBoxOut = OUTPUTS(lpBoxOutIndex)
       |    val successor = OUTPUTS(selfOutIndex)
       |
       |    val lpReservesIn = lpBoxIn.tokens(1)
       |    val lpReservesOut = lpBoxOut.tokens(1)
       |
       |    val reservesXIn = lpBoxIn.value
       |    val reservesYIn = lpBoxIn.tokens(2)._2
       |
       |    val reservesXOut = lpBoxOut.value
       |    val reservesYOut = lpBoxOut.tokens(2)._2
       |
       |    val supplyLpIn = initialLp - lpReservesIn._2
       |
       |    val oracleRateXy = oracleBox.R4[Long].get
       |    val lpRateXyIn = reservesXIn / reservesYIn  // we can assume that reservesYIn > 0 (since at least one token must exist)
       |
       |    val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
       |
       |    val validRateForRedeemingLp = validOracleBox && oracleRateXy > lpRateXyIn * 98 / 100 // lpRate must be >= 0.98 * oracleRate // these parameters need to be tweaked
       |
       |    // Note:
       |    //    supplyLpIn = initialLp - lpReservesIn._2
       |    //    supplyLpOut = initialLp - lpReservesOut._2
       |    // Thus:
       |    //    deltaSupplyLp = supplyLpOut - supplyLpIn
       |    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
       |    //                  = lpReservesIn._2 - lpReservesOut._2
       |
       |    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2
       |    val deltaReservesX = reservesXOut - reservesXIn
       |    val deltaReservesY = reservesYOut - reservesYIn
       |
       |    val validRedemption = deltaSupplyLp < 0 && deltaReservesX < 0 && deltaReservesY < 0 && {
       |        val _deltaSupplyLp = deltaSupplyLp.toBigInt
       |        // note: _deltaSupplyLp, deltaReservesX and deltaReservesY are negative
       |        deltaReservesX.toBigInt * supplyLpIn >= _deltaSupplyLp * reservesXIn && deltaReservesY.toBigInt * supplyLpIn >= _deltaSupplyLp * reservesYIn
       |    } && validRateForRedeemingLp
       |
       |    val selfPreserved = successor.propositionBytes == SELF.propositionBytes  &&
       |                        successor.value >= SELF.value                        &&
       |                        successor.tokens == SELF.tokens
       |
       |    sigmaProp(validRedemption && selfPreserved)
       |}
       |""".stripMargin

  val trackingScript =
    s"""{   
       |    // This box: Tracking box
       |    // 
       |    // TOKENS
       |    //   tokens(0): Tracking NFT
       |    // 
       |    // REGISTERS
       |    //   R4: Int (numerator)
       |    //   R5: Int (denominator)
       |    //   R6: Boolean (isBelow, a flag indicating the type of tracking) 
       |    //   R7: Int (trackingHeight) 
       |    // 
       |    // TRANSACTIONS 
       |    // [1] Update tracker
       |    //   Input         |  Output        |   Data-Input 
       |    // -----------------------------------------------
       |    // 0 Tracking box  |  Tracking box  |   Oracle 
       |    // 1               |                |   LP
       |    
       |  
       |    // Oracle data:
       |    // R4 of the oracle contains the rate "nanoErgs per USD" in Long format  
       |
       |    // 
       |    // A "tracker" is like a monitor that triggers an alarm when an event occurs. 
       |    // The alarm continues to "ring" until the tracker resets the alarm, which can only happen after the event has ended.
       |    // Thus, if the alarm is in a "triggered" state, we can be sure that the event is ongoing (i.e., not yet ended).
       |    // In our case, the event we are monitoring is the ratio of LP rate and Oracle rate going below (or above) some value.
       |    // The registers define the ratio to monitor (R4, R5) and whether we are monitoring above or below (R6), along with 
       |    // the height at which the trigger occurred (R7). The value in R7 will be "infinity" if the event has ended. 
       |    
       |    // This box is be spent whenever tracker state must change i.e., move from trigger to reset or vice versa 
       |    // This box can only be be spent if the tracker state changes.
       |    // Someone must spend this box keeping LP as data input.
       |      
       |    val threshold = 3 // error threshold in trigger height 
       |    
       |    val oracleBoxIndex = 0
       |    val lpBoxIndex = 1
       |    val selfOutIndex = 0
       |
       |    val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}") // to identify LP box 
       |    val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}") // to identify oracle pool box
       |    
       |    val lpBox = CONTEXT.dataInputs(lpBoxIndex)
       |    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
       |    val successor = OUTPUTS(selfOutIndex)
       |    
       |    val tokenY = lpBox.tokens(2)  // Dexy USDtokens
       |    
       |    val validLp = lpBox.tokens(0)._1 == lpNFT
       |    
       |    val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
       |    val validSuccessor = successor.tokens == SELF.tokens                      && 
       |                         successor.propositionBytes == SELF.propositionBytes  && 
       |                         SELF.value <= successor.value
       |
       |    val oracleRateXY = oracleBox.R4[Long].get
       |    val reservesX = lpBox.value
       |    val reservesY = tokenY._2   // Dexy tokens quantity
       |    val lpRateXY = reservesX / reservesY  // we can assume that reservesY > 0 (since at least one token must exist)
       |
       |    // Let t = num/denom
       |    // trackerHeight is the height at which the tracker was "triggered". 
       |    // If the tracker "reset", then trackerHeight will store Int.MaxValue
       |    // 
       |    // isBelow tells us if the tracking should be tracking "below" or "above" the ratio.  
       |    // Let r be the ratio "oracle pool rate" / "LP rate", where the term "rate" denotes "Ergs per dexy"
       |    // Now, if "isBelow" is true (i.e. "lower" tracking), then the tracker will be triggered when r goes below t and will be reset once r goes above t
       |    
       |    // there are three tracking boxes as of now:
       |    // box # | num | denom | height | isBelow
       |    // ------+-----+-------+--------+--------
       |    // 1     | 95  | 100   | _      | true     (for extracting to future)
       |    // 2     | 98  | 100   | _      | true     (for arbitrage mint)
       |    // 3     | 101 | 100   | _      | false    (for release in future - reverse of extract to future)
       |    
       |    
       |    val denomIn = SELF.R4[Int].get
       |    val numIn = SELF.R5[Int].get
       |    val isBelowIn = SELF.R6[Boolean].get
       |    val trackerHeightIn = SELF.R7[Int].get
       |    
       |    val denomOut = successor.R4[Int].get
       |    val numOut = successor.R5[Int].get
       |    val isBelowOut = successor.R6[Boolean].get
       |    val trackerHeightOut = successor.R7[Int].get
       |    
       |    val validTracking = {
       |        // For a ratio of 95%, set num to 95 and denom to 100 (equivalently 19, 20), and set isBelow to true
       |        // Then trackerHeight will be set when oracle pool rate becomes <= 95% of LP rate 
       |        // and it will be reset to Int.MaxValue when that rate becomes > than 95% of LP rate
       |        // 
       |        // Let oracle pool rate be P and LP rate at earlier point be L0 and currently (via data input) be L1
       |        // Let N and D denote num and denom respectively. Then we can use the following table to decide correctness
       |        // 
       |        // EVENT    | isBelow | INPUT       | OUTPUT
       |        // ---------+---------+-------------+-----------
       |        // trigger  | true    | P/L0 >= N/D | P/L1 <  N/D 
       |        // reset    | true    | P/L0 <  N/D | P/L1 >= N/D (reverse of 1st row)
       |        // ---------+---------+-------------+------------
       |        // trigger  | false   | P/L0 <= N/D | P/L1 >  N/D 
       |        // reset    | false   | P/L0 >  N/D | P/L1 <= N/D (reverse of 1st row) 
       |        
       |        val x = oracleRateXY * denomIn
       |        val y = numIn * lpRateXY
       |        
       |        val notTriggeredEarlier = trackerHeightIn == ${Int.MaxValue}  // Infinity
       |        val triggeredNow = trackerHeightOut >= HEIGHT - threshold && 
       |                           trackerHeightOut <= HEIGHT  
       |         
       |        val notResetEarlier = trackerHeightIn < ${Int.MaxValue}       // Less than Infinity
       |        val resetNow = trackerHeightOut == ${Int.MaxValue}            // Infinity
       |         
       |        val trigger = ((isBelowIn && x < y) || (!isBelowIn && x > y)) && notTriggeredEarlier && triggeredNow
       |        val reset = ((isBelowIn && x >= y) || (!isBelowIn && x <= y)) && notResetEarlier && resetNow
       |        val correctAction = trigger || reset  
       |        
       |        numOut == numIn          && 
       |        denomOut == denomIn      && 
       |        isBelowOut == isBelowIn  && 
       |        correctAction
       |    }
       |    
       |    sigmaProp(validSuccessor && validLp && validTracking && validOracleBox)
       |}
       |""".stripMargin

  val interventionScript =
    s"""{  
       |  // This box: Intervention box
       |  // 
       |  // TOKENS
       |  //   tokens(0): Intervention NFT
       |  //   
       |  // REGISTERS
       |  // 
       |  // TRANSACTIONS
       |  // [1] Intervention
       |  //   Input         |  Output        |   Data-Input 
       |  // -----------------------------------------------
       |  // 0 LP            |  LP            |   Oracle
       |  // 1 Bank          |  Bank          |   Tracking (98%)
       |  // 2 Intervention  |  Intervention  |   
       |
       |  // Oracle data:
       |  // R4 of the oracle contains the rate "nanoErgs per USD" in Long format
       |
       |  // inputs indices
       |  val lpInIndex = 0
       |  val bankInIndex = 1
       |  
       |  // outputs indices
       |  val lpOutIndex = 0
       |  val bankOutIndex = 1
       |  val selfOutIndex = 2    // SELF should be third input
       |  
       |  // data inputs indices
       |  val oracleBoxIndex = 0 
       |  val trackingBoxIndex = 1
       |
       |  val lastIntervention = SELF.creationInfo._1
       |  val buffer = 3 // error margin in height
       |  val T = 100 // from paper, gap between two interventions
       |  val T_int = 20 // blocks after which a trigger swap event can be completed, provided rate has not crossed oracle pool rate
       |   
       |  val bankNFT = fromBase64("${Base64.encode(bankNFT.decodeHex)}") 
       |  val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}") 
       |  val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}")
       |  val tracking98NFT = fromBase64("${Base64.encode(tracking98NFT.decodeHex)}")
       |  
       |  val thresholdPercent = 98 // 98% or less value (of LP in terms of OraclePool) will trigger action (ensure less than 100)
       |  
       |  val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
       |  val trackingBox = CONTEXT.dataInputs(trackingBoxIndex)
       |  
       |  val lpBoxIn = INPUTS(lpInIndex)
       |  val bankBoxIn = INPUTS(bankInIndex)
       |
       |  val lpBoxOut = OUTPUTS(lpOutIndex)
       |  val bankBoxOut = OUTPUTS(bankOutIndex)
       |  
       |  val successor = OUTPUTS(selfOutIndex) 
       |  
       |  val lpTokenYIn    = lpBoxIn.tokens(2)
       |  val lpTokenYOut    = lpBoxOut.tokens(2)
       |  
       |  val lpReservesXIn = lpBoxIn.value
       |  val lpReservesYIn = lpTokenYIn._2
       |  
       |  val lpReservesXOut = lpBoxOut.value
       |  val lpReservesYOut = lpTokenYOut._2
       |  
       |  val lpRateXyInTimesLpReservesYIn = lpReservesXIn.toBigInt   // we can assume that reservesYIn > 0 (since at least one token must exist)
       |  val lpRateXyOutTimesLpReservesYOut = lpReservesXOut.toBigInt  // we can assume that reservesYOut > 0 (since at least one token must exist)
       |  
       |  val oracleRateXy = oracleBox.R4[Long].get.toBigInt
       |   
       |  val validThreshold = lpRateXyInTimesLpReservesYIn * 100 < oracleRateXy * thresholdPercent * lpReservesYIn
       |
       |  // check data inputs are correct
       |  val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT 
       |  val validTrackingBox = trackingBox.tokens(0)._1 == tracking98NFT
       |
       |  // check that inputs are correct
       |  val validLpBoxIn = lpBoxIn.tokens(0)._1 == lpNFT
       |  val validBankBoxIn = bankBoxIn.tokens(0)._1 == bankNFT
       |
       |  // check that self output is correct
       |  val validSuccessor = successor.propositionBytes == SELF.propositionBytes  &&
       |                       successor.tokens == SELF.tokens                      &&
       |                       successor.value >= SELF.value                        &&
       |                       successor.creationInfo._1 >= HEIGHT - buffer
       |
       |
       |  val validGap = lastIntervention < HEIGHT - T
       |  
       |  val deltaBankTokens =  bankBoxOut.tokens(1)._2 - bankBoxIn.tokens(1)._2
       |  val deltaBankErgs = bankBoxIn.value - bankBoxOut.value
       |  val deltaLpX = lpReservesXOut - lpReservesXIn
       |  val deltaLpY = lpReservesYIn - lpReservesYOut
       |  
       |  val trackingHeight = trackingBox.R7[Int].get
       |  
       |  val validTracking = trackingHeight < HEIGHT - T_int // at least T_int blocks have passed since the tracking started
       |                  
       |  val validAmount = lpRateXyOutTimesLpReservesYOut * 1000 <= oracleRateXy * lpReservesYOut * 995    // new rate must be <= 99.5 times oracle rate
       |
       |  val validDeltas = deltaBankErgs <= deltaLpX  &&  // ergs reduced in bank box must be <= ergs gained in LP
       |                    deltaBankTokens >= deltaLpY &&   // tokens gained in bank box must be >= tokens reduced in LP
       |                    deltaLpX > 0
       |
       |  val validSwap = validAmount      &&
       |                  validDeltas      &&
       |                  validBankBoxIn   &&
       |                  validLpBoxIn     &&
       |                  validSuccessor   &&
       |                  validOracleBox   &&
       |                  validTrackingBox &&
       |                  validThreshold   &&
       |                  validTracking    &&
       |                  validGap
       |   
       |  sigmaProp(validSwap)
       |}
       |""".stripMargin

  val extractScript =
    s"""|{   
        |    // This box: Extract to future
        |    // 
        |    // TOKENS
        |    //   tokens(0): extractionNFT 
        |    //   tokens(1): Dexy tokens
        |    // 
        |    // REGISTERS
        |    //   R3 (creation-info)
        |    // 
        |    // TRANSACTIONS
        |    //
        |    // [1] Extract to future
        |    //   Input         |  Output        |   Data-Input 
        |    // -----------------------------------------------
        |    // 0 LP            |  LP            |   Oracle
        |    // 1 Extract       |  Extract       |   Tracking (95%)
        |    // 2               |                |   Bank   (to check that bank is empty)
        |    // 
        |    // [2] Reverse Extract to future (release)
        |    //   Input         |  Output        |   Data-Input 
        |    // -----------------------------------------------
        |    // 0 LP            |  LP            |   Oracle
        |    // 1 Extract       |  Extract       |   Tracking (101%)
        |        
        |    // ToDo: verify following
        |    //   cannot change prop bytes for LP, Extract and Tracking box
        |    //   cannot change tokens/nanoErgs in LP, extract and tracking box except what is permitted
        |
        |    // Oracle data:
        |    // R4 of the oracle contains the rate "nanoErgs per USD" in Long format
        |
        |    val lpBoxInIndex = 0
        |    val lpBoxOutIndex = 0
        |    
        |    val selfOutIndex = 1
        |    
        |    // for data inputs
        |    val oracleBoxIndex = 0
        |    val bankBoxIndex = 2
        |    val tracking95BoxIndex = 1
        |    
        |    val tracking101BoxIndex = 1
        |    
        |    val minBankNanoErgs = 10000000000L // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
        |    
        |    val tracking95NFT = fromBase64("${Base64.encode(tracking95NFT.decodeHex)}")
        |    val tracking101NFT = fromBase64("${Base64.encode(tracking101NFT.decodeHex)}")
        |    val bankNFT = fromBase64("${Base64.encode(bankNFT.decodeHex)}")
        |    val lpNFT = fromBase64("${Base64.encode(lpNFT.decodeHex)}")
        |    val oracleNFT = fromBase64("${Base64.encode(oracleNFT.decodeHex)}")
        |    
        |    val T_extract = 10 // blocks for which the rate is below 95%
        |    val T_release = 2 // blocks for which the rate is above 101%
        |    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
        |    
        |    val buffer = 3 // allowable error in setting height due to congestion 
        |    
        |    // tracking box should record at least T_extract blocks of < 95%
        |    val tracking95Box = CONTEXT.dataInputs(tracking95BoxIndex)
        |    val tracking101Box = CONTEXT.dataInputs(tracking101BoxIndex)
        |    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
        |     
        |    val tracker95Height = tracking95Box.R7[Int].get
        |    val tracker101Height = tracking101Box.R7[Int].get
        |    
        |    val lpBoxIn = INPUTS(lpBoxInIndex)
        |    val lpBoxOut = OUTPUTS(lpBoxInIndex)
        |    
        |    val successor = OUTPUTS(selfOutIndex)
        |    
        |    val lastBurnOrRelease = SELF.creationInfo._1 
        |     
        |    val validDelay = lastBurnOrRelease < HEIGHT - T_delay
        |     
        |    val validSuccessor = successor.tokens(0)._1 == SELF.tokens(0)._1          &&  // NFT preserved
        |                         successor.tokens(1)._1 == SELF.tokens(1)._1          &&  // Dexy token id preserved
        |                         successor.propositionBytes == SELF.propositionBytes  &&
        |                         successor.value >= SELF.value                        &&
        |                         successor.creationInfo._1 >= HEIGHT - buffer         
        |                            
        |    val deltaDexy = successor.tokens(1)._2 - SELF.tokens(1)._2 // can be +ve or -ve 
        |    
        |    val validBankBox = if (CONTEXT.dataInputs.size > bankBoxIndex) {
        |      CONTEXT.dataInputs(bankBoxIndex).tokens(0)._1 == bankNFT &&
        |      CONTEXT.dataInputs(bankBoxIndex).value <= minBankNanoErgs
        |    } else false
        |    
        |    val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT 
        |    
        |    val reservesYOut = lpBoxOut.tokens(2)._2
        |    val reservesYIn = lpBoxIn.tokens(2)._2
        |    val reservesXOut = lpBoxOut.value
        |    val reservesXIn = lpBoxIn.value
        |    
        |    val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT                               && // Maybe this check not needed? (see LP box)
        |                     lpBoxOut.tokens(0)._1 == lpBoxIn.tokens(0)._1               && // NFT preserved 
        |                     lpBoxOut.tokens(1) == lpBoxIn.tokens(1)                     && // LP tokens preserved
        |                     lpBoxOut.tokens(2)._1 == lpBoxIn.tokens(2)._1               && // Dexy token Id preserved
        |                     lpBoxOut.tokens(2)._1 == SELF.tokens(1)._1                  && // Dexy token Id is same as tokens stored here
        |                     reservesYOut == reservesYIn - deltaDexy                     && // Dexy token qty preserved
        |                     reservesXOut == reservesXIn                                 &&
        |                     lpBoxOut.propositionBytes == lpBoxIn.propositionBytes  
        |     
        |    val validTracking95Box = tracking95Box.tokens(0)._1 == tracking95NFT
        |    val validTracking101Box = tracking101Box.tokens(0)._1 == tracking101NFT
        |    
        |    val oracleRateXY = oracleBox.R4[Long].get
        |    val lpRateXYOut = reservesXOut / reservesYOut
        |    
        |    val validExtractAmount = oracleRateXY * 100 > lpRateXYOut * 98 && // lpRate at output must be >= 0.98 * oracleRate   
        |                             oracleRateXY * 100 < lpRateXYOut * 101   // lpRate at output must be <= 1.01 * oracleRate 
        |                             // ToDo: possibly tweak the 101 requirement (or remove it?)
        |                             
        |    val validReleaseAmount = oracleRateXY * 100 > lpRateXYOut * 101 && // lpRate at output must be >= 1.01 * oracleRate   
        |                             oracleRateXY * 100 < lpRateXYOut * 104    // lpRate at output must be <= 1.04 * oracleRate 
        |                             // ToDo: possibly tweak the 104 requirement (or remove it?)
        |                             
        |    
        |    val validExtract  = deltaDexy > 0                           &&
        |                        validTracking95Box                      &&
        |                        (HEIGHT - tracker95Height) > T_extract  && // at least T_extract blocks have passed after crossing below 95% 
        |                        validBankBox                            && 
        |                        validExtractAmount                      
        |
        |    val validRelease  = deltaDexy < 0                          &&
        |                        validTracking101Box                    &&
        |                        HEIGHT - tracker101Height > T_release  && 
        |                        validReleaseAmount                     
        |                         
        |    sigmaProp(validSuccessor && validDelay && validLpBox && validOracleBox && (validRelease || validExtract))
        |}
        |""".stripMargin

  val bankErgoTree = ScriptUtil.compile(Map(), bankScript)
  val bankAddress = getStringFromAddress(getAddressFromErgoTree(bankErgoTree))
  val arbitrageMintErgoTree = ScriptUtil.compile(Map(), arbitrageMintScript)
  val arbitrageMintAddress = getStringFromAddress(getAddressFromErgoTree(arbitrageMintErgoTree))
  val freeMintErgoTree = ScriptUtil.compile(Map(), freeMintScript)
  val freeMintAddress = getStringFromAddress(getAddressFromErgoTree(freeMintErgoTree))
  val payoutErgoTree = ScriptUtil.compile(Map(), payoutScript)
  val payoutAddress = getStringFromAddress(getAddressFromErgoTree(payoutErgoTree))
  val lpErgoTree = ScriptUtil.compile(Map(), lpScript)
  val lpAddress = getStringFromAddress(getAddressFromErgoTree(lpErgoTree))
  val lpSwapErgoTree = ScriptUtil.compile(Map(), lpSwapScript)
  val lpSwapAddress = getStringFromAddress(getAddressFromErgoTree(lpSwapErgoTree))
  val lpMintErgoTree = ScriptUtil.compile(Map(), lpMintScript)
  val lpMintAddress = getStringFromAddress(getAddressFromErgoTree(lpMintErgoTree))
  val lpRedeemErgoTree = ScriptUtil.compile(Map(), lpRedeemScript)
  val lpRedeemAddress = getStringFromAddress(getAddressFromErgoTree(lpRedeemErgoTree))
  val trackingErgoTree = ScriptUtil.compile(Map(), trackingScript)
  val trackingAddress = getStringFromAddress(getAddressFromErgoTree(trackingErgoTree))
  val extractErgoTree = ScriptUtil.compile(Map(), extractScript)
  val extractAddress = getStringFromAddress(getAddressFromErgoTree(extractErgoTree))
  val interventionErgoTree = ScriptUtil.compile(Map(), interventionScript)
  val interventionAddress = getStringFromAddress(getAddressFromErgoTree(interventionErgoTree))

  def main(args: Array[String]): Unit = {
    println(s"Bank: $bankAddress")
    println(bankScript)
    println()

    println(s"ArbitrageMint: $arbitrageMintAddress")
    println(arbitrageMintScript)
    println()

    println(s"FreeMint: $freeMintAddress")
    println(freeMintScript)
    println()

    println(s"Payout: $payoutAddress")
    println(payoutScript)
    println()

    println(s"LP: $lpAddress")
    println(lpScript)
    println()

    println(s"LP Swap: $lpSwapAddress")
    println(lpSwapScript)
    println()

    println(s"LP Mint: $lpMintAddress")
    println(lpMintScript)
    println()

    println(s"LP Redeem: $lpRedeemAddress")
    println(lpRedeemScript)
    println()

    println(s"Tracking: $trackingAddress")
    println(trackingScript)
    println()

    println(s"Extract: $extractAddress")
    println(extractScript)
    println()

    println(s"Intervention: $interventionAddress")
    println(interventionScript)
    println()
  }
}
