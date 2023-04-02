{
  // This box: (payout box)
  //
  // TOKENS
  //   tokens(0): Payout NFT
  //
  // REGISTERS
  //   R4: (Coll[Byte]) payout script hash
  //
  // TRANSACTIONS
  // [1] Payout
  //   Input    |  Output   |   Data-Input
  // -------------------------------------
  // 0 Payout   |  Payout   |   Oracle
  // 1 Bank     |  Bank     |   LP
  // 2          |  Reward   |

  // In the above transaction, the "payouts" (rewards) will be stored in a "Reward" box
  // The payout box just enforces the correct logic for such rewards and does not store the actual rewards
  // The reward box must be protected by a script whose hash is stored in R4 of the payout box

  val payoutThreshold = 100000000000000L // nanoErgs (100000 Ergs)
  val maxPayOut = 100000000000L // 100 Ergs
  val minPayOut = 10000000000L  // 10 Ergs

  val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box
  val lpNFT = fromBase64("$lpNFT")

  // inputs indices
  val bankInIndex = 1

  // outputs indices
  val selfOutIndex = 0
  val bankOutIndex = 1
  val rewardOutIndex = 2

  // data inputs indices
  val oracleIndex = 0
  val lpIndex = 1

  val bankBoxIn = INPUTS(bankInIndex)

  val bankBoxOut = OUTPUTS(bankOutIndex)
  val successor = OUTPUTS(selfOutIndex)
  val rewardBoxOut = OUTPUTS(rewardOutIndex)

  val oracleBox = CONTEXT.dataInputs(oracleIndex)
  val lpBox = CONTEXT.dataInputs(lpIndex)

  val validOracle = oracleBox.tokens(0)._1 == oracleNFT
  val validLP = lpBox.tokens(0)._1 == lpNFT

  val payoutScriptHash = SELF.R4[Coll[Byte]].get // payout script hash
  val successorR4 = successor.R4[Coll[Byte]].get // should be same as selfR4

  val lpReservesX = lpBox.value
  val lpReservesY = lpBox.tokens(2)._2 // dexyReserves

  val bankDexy = bankBoxIn.tokens(1)._2

  val ergsRemoved = bankBoxOut.value - bankBoxIn.value
  val ergsTaken = rewardBoxOut.value

  // oracle delivers nanoErgs per 1 kg of gold
  // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
  // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  val oracleRate = oracleBox.R4[Long].get / 1000000L

  val lpRate = lpReservesX / lpReservesY

  val dexyInCirculation = $initialDexyTokens - bankDexy
  val collateralized = oracleRate * bankBoxIn.value > dexyInCirculation * 2 // > 200% collateralization

  // no need to validate bank NFT and proposition here
  val validBank = bankBoxOut.tokens == bankBoxIn.tokens                     && // tokens preserved
                  ergsRemoved == ergsTaken

  val validSuccessor = successor.propositionBytes == SELF.propositionBytes && // script preserved
                       successor.tokens == SELF.tokens                     && // NFT preserved
                       successor.value >= SELF.value                       && // Ergs preserved or increased
                       successor.R4[Coll[Byte]].get == payoutScriptHash

  val validPayout = blake2b256(rewardBoxOut.propositionBytes) == payoutScriptHash && // script of reward box is correct
                    bankBoxIn.value >= payoutThreshold                            && // bank box must had large balance
                    ergsTaken >= minPayOut                                        && // cannot take too little (dust, etc)
                    ergsTaken <= maxPayOut                                           // cannot take too much

  sigmaProp(validBank && validSuccessor && validPayout && validOracle && validLP && collateralized)
}