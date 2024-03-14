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

  val lastHeight = SELF.R4[Int].get

  val selfOutIndex = getVar[Short](0).get

  val selfOut = OUTPUTS(selfOutIndex)

  val action = getVar[Byte](1).get

  if (action == 0) {  // top-up:

    // strip header byte from trees
    val selfOutTree = selfOut.propositionBytes.slice(1, selfOut.propositionBytes.size)
    val selfInTree = SELF.propositionBytes.slice(1, SELF.propositionBytes.size)

    sigmaProp(
        selfOut.tokens(0) == SELF.tokens(0) &&
        selfOut.tokens(1)._1 == SELF.tokens(1)._1 &&
        selfOut.tokens(1)._2 > SELF.tokens(1)._2 && // GORT added
        selfOut.R4[Int].get == lastHeight &&
        selfOutTree == selfInTree &&
        selfOut.value >= SELF.value
    )
  } else {  // withdrawal
    val newHeight = selfOut.R4[Int].get
    sigmaProp(
        selfOut.tokens(0) == SELF.tokens(0) &&
        selfOut.tokens(1)._1 == SELF.tokens(1)._1 &&
        SELF.tokens(1)._2 > selfOut.tokens(1)._2 &&
        SELF.tokens(1)._2 - selfOut.tokens(1)._2 <= newHeight - lastHeight && // 1 GORT per block can be released
        selfOut.propositionBytes == SELF.propositionBytes &&
        selfOut.value >= SELF.value &&
        newHeight > lastHeight &&
        newHeight <= HEIGHT   // can't take rewards from future
    ) && SELF.R5[SigmaProp].get
  }
}