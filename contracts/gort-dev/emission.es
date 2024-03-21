{
  // Contract which is releasing GORT to developers (Gold cooperative), 1 ERG per block.
  // It supports two actions: top-up and withdrawal

  // Registers:
  // R4 (int) - last payment height
  // R5 (SigmaProp) - auth

  // tokens:
  // #0 - box id NFT
  // #1 - GORT

  // context vars:
  // #0 - output index of SELF
  // #1 - action (top-up / withdrawal)

  val lastHeight = SELF.R4[Int].get // last payout height

  val selfOutIndex = getVar[Short](0).get

  val selfOut = OUTPUTS(selfOutIndex)

  // strip header byte from trees
  val selfOutTree = selfOut.propositionBytes.slice(1, selfOut.propositionBytes.size)
  val selfInTree = SELF.propositionBytes.slice(1, SELF.propositionBytes.size)

  val commonPreservationRules =
    selfOut.tokens(0) == SELF.tokens(0) && // NFT preserved
    selfOut.tokens(1)._1 == SELF.tokens(1)._1 && // GORT token id preserved
    selfOutTree == selfInTree && // script preserved
    selfOut.value >= SELF.value // ERG value preserved or increased

  val action = getVar[Byte](1).get

  if (action == 0) {  // top-up:
    sigmaProp(
        commonPreservationRules &&
        selfOut.tokens(1)._2 > SELF.tokens(1)._2 && // GORTs must be added
        selfOut.R4[Int].get == lastHeight && // payout height preserved
        selfOut.R5[SigmaProp].get == SELF.R5[SigmaProp].get // auth preserved
    )
  } else {  // withdrawal
    val newHeight = selfOut.R4[Int].get
    sigmaProp(
        commonPreservationRules &&
        SELF.tokens(1)._2 > selfOut.tokens(1)._2 && // GORTs withdrawn
        SELF.tokens(1)._2 - selfOut.tokens(1)._2 <= newHeight - lastHeight && // <= 1 GORT per block can be released
        newHeight > lastHeight && // payout height increased
        newHeight <= HEIGHT   // can't take rewards from future
        // R5 is not checked here so could be changed
    ) && SELF.R5[SigmaProp].get // signature from proposition stored in R5 is needed to withdraw (and possibly update payout data)
  }
}