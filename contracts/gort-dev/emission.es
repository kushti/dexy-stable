{
  // Registers:
  // R4 (int) - last payment height
  // R5 (SigmaProp) - auth

  // tokens:
  // #0 - box id NFT
  // #1 - GORT

  val lastHeight = SELF.R4[Int].get

  val selfOut = OUTPUTS(0)

  val action = getVar[Int](0).get

  if (action == 0) {  // top-up:
    sigmaProp(
        selfOut.tokens(0) == SELF.tokens(0) &&
        selfOut.tokens(1)._1 == SELF.tokens(1)._1 &&
        selfOut.tokens(1)._2 > SELF.tokens(1)._2 && // GORT added
        selfOut.R4[Int].get == lastHeight &&
        selfOut.propositionBytes == SELF.propositionBytes && // todo: skip first byte
        selfOut.value >= SELF.value
    )
  } else {  // withdrawal
    val newHeight = SELF.R4[Int].get
    sigmaProp(
        selfOut.tokens(0) == SELF.tokens(0) &&
        selfOut.tokens(1)._1 == SELF.tokens(1)._1 &&
        SELF.tokens(1)._2 - selfOut.tokens(1)._2 == newHeight - lastHeight && // 1 GORT per block can be released
        selfOut.R4[Int].get == lastHeight &&
        selfOut.propositionBytes == SELF.propositionBytes &&
        selfOut.value >= SELF.value &&
        newHeight > lastHeight &&
        newHeight <= HEIGHT   // can't take rewards from future
    ) && SELF.R5[SigmaProp].get
  }
}