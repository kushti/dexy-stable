{ // This box (ballot box):
  // R4 the group element of the owner of the ballot token [GroupElement]
  // R5 the box id of the update box [Coll[Byte]]
  // R6 the value voted for [Coll[Byte]]

  val updateNFT = fromBase64("$updateNFT")

  val pubKey = SELF.R4[GroupElement].get

  val index = INPUTS.indexOf(SELF, 0)

  val output = OUTPUTS(index)

  val isBasicCopy = output.R4[GroupElement].get == pubKey && // todo: change to .isDefined so pubkey rotation possible?
                    output.propositionBytes == SELF.propositionBytes &&
                    output.tokens == SELF.tokens &&
                    output.value >= SELF.value

  val castVote = proveDlog(pubKey)

  val notVoted = output.R5[Coll[Byte]].isDefined == false
  val update = INPUTS(0).tokens(0)._1 == updateNFT && notVoted

  // Note: We want that during an update, a new valid vote should not be generated
  // notVoted ensures that the update action does not generate a new vote.
  // This is already prevented by having R5 contain the id of the update box,
  // This guarantees that R5 can never contain this box Id (because it depends on the outputs)
  // However, notVoted is used for additional security

  sigmaProp(
    isBasicCopy && (castVote || update)
  )
}