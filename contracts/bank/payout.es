{
  // This box: (payout box)
  //
  // TOKENS
  //   tokens(0): Payout NFT
  //
  // REGISTERS
  //   R4: (Int) height when last payment was done. Non-nullable, so set to 0 initially
  //
  // TRANSACTIONS
  // [1] Payout
  //   Input    |  Output   |   Data-Input
  // -------------------------------------
  // 0 Payout   |  Payout   |   Oracle
  // 1 Bank     |  Bank     |
  // 2 BuyBack  |  BuyBack  |

  // In the above transaction, the "payouts" (rewards) will be stored in a "Reward" box
  // The payout box just enforces the correct logic for such rewards and does not store the actual rewards
  // The reward box must be protected by a script whose hash is stored in R4 of the payout box

  val payoutThreshold = 100000000000000L // nanoErgs (100000 Ergs)

  val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box
  val lpNFT = fromBase64("$lpNFT")
  val buybackNft = fromBase64("$buybackNFT")

  // inputs indices
  val bankInIndex = 1
  val buybackInIndex = 2

  // outputs indices
  val selfOutIndex = 0
  val bankOutIndex = 1
  val buybackOutIndex = 2

  // data inputs indices
  val oracleIndex = 0
  val lpIndex = 1

  val bankBoxIn = INPUTS(bankInIndex)
  val buybackBoxIn = INPUTS(buybackInIndex)

  val bankBoxOut = OUTPUTS(bankOutIndex)
  val successor = OUTPUTS(selfOutIndex)
  val buybackBoxOut = OUTPUTS(buybackOutIndex)

  val oracleBox = CONTEXT.dataInputs(oracleIndex)

  val validOracle = oracleBox.tokens(0)._1 == oracleNFT

  val bankDexy = bankBoxIn.tokens(1)._2

  // oracle delivers nanoErgs per 1 kg of gold
  // we divide it by 1000000 to get nanoErg per dexy, i.e. 1mg of gold
  // can assume always > 0 (ref oracle pool contracts) NanoErgs per USD
  val oracleRate = oracleBox.R4[Long].get / 1000000L

  val dexyInCirculation = $initialDexyTokens - bankDexy
  val collateralized = oracleRate * dexyInCirculation * 8 < bankBoxIn.value // > 800% collateralization

  val maxPaymentAmount = bankBoxIn.value / 200 // 0.5 % max can be taken
  val paymentAmount = bankBoxIn.value - bankBoxOut.value
  val properPayment = (paymentAmount > 0) && (paymentAmount <= maxPaymentAmount) &&
                        (buybackBoxOut.value - buybackBoxIn.value == paymentAmount)

  val lastPayment = SELF.R4[Int].get
  val buffer = 5 // error margin in height
  val delayInPayments = 5040 // ~ 1 week
  val properHeight = lastPayment + delayInPayments <= HEIGHT
  val properNewR4 = successor.R4[Int].get >= HEIGHT - buffer

  // no need to validate bank NFT and proposition here
  // value is checked in validPayout
  val validBank = bankBoxOut.tokens == bankBoxIn.tokens                     // tokens preserved

  // payout script box preservation
  val validSuccessor = successor.propositionBytes == SELF.propositionBytes && // script preserved
                       successor.tokens == SELF.tokens                     && // NFT preserved
                       successor.value >= SELF.value                       && // Ergs preserved or increased
                       properNewR4

  val validBuyBackIn = buybackBoxIn.tokens(0)._1 == buybackNft

  val validPayout = validBuyBackIn                                                && // script of reward box is correct
                    collateralized                                                && // enough over-collateralization
                    properPayment                                                 && // bank paying 0.5% of its reserves out
                    properHeight                                                     // after enough delay

  sigmaProp(validBank && validSuccessor && validPayout && validOracle)
}