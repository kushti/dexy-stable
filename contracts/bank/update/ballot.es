{ // This box (ballot box):
  // R4 the group element of the owner of the ballot token [GroupElement]
  // R5 dummy Int due to AOTC non-lazy evaluation (since bank box has Long at R5). Due to the line marked ****
  // R6 the box id of the update box [Coll[Byte]]
  // R7 the value voted for [Coll[Byte]]

  // Base-64 version of bank update NFT 239c170b7e82f94e6b05416f14b8a2a57e0bfff0e3c93f4abbcd160b6a5b271a
  // Got via http://tomeko.net/online_tools/hex_to_base64.php
  val updateNFT = fromBase64("$updateNFT")

  val pubKey = SELF.R4[GroupElement].get

  val index = INPUTS.indexOf(SELF, 0)

  val output = OUTPUTS(index)

  val isBasicCopy = output.R4[GroupElement].get == pubKey && // todo: change to .isDefined so pubkey rotation possible?
                    output.propositionBytes == SELF.propositionBytes &&
                    output.tokens == SELF.tokens &&
                    output.value >= SELF.value

  val castVote = proveDlog(pubKey)

  val notVoted = output.R7[Coll[Byte]].isDefined == false

  val update = INPUTS(0).tokens(0)._1 == updateNFT && notVoted

  // Note: We want that during an update, a new valid vote should not be generated
  // notVoted ensures that the update action does not generate a new vote.
  // This is already prevented by having R6 contain the id of the update box,
  // This guarantees that R6 can never contain this box Id (because it depends on the outputs)
  // However, notVoted is used for additional security

  sigmaProp(
    isBasicCopy && (castVote || update)
  )
}