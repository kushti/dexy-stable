{
  // Contract update script. Taken from SigmaUSD contracts.
  //
  // This box (update box):
  // Registers empty
  //
  // ballot boxes (in other inputs)
  // R4 the group element of the owner of the ballot token [GroupElement]
  // R5 the box id of this box [Coll[Byte]]
  // R6 the value voted for [Coll[Byte]]

  // contractToUpdateNFT is whether bankNFT, extractNFT, interventionNFT
  val contractToUpdateNFT = fromBase64("$contractToUpdateNFT")

  val ballotTokenId = fromBase64("$ballotTokenId")

  // todo: consider proper value before deployment
  val minVotes = 3 // out of 5

  // collect and update in one step
  val updateBoxOut = OUTPUTS(0) // copy of this box is the 1st output

  val validUpdateIn = SELF.id == INPUTS(0).id // this is 1st input

  val contractToUpdateBoxIn = INPUTS(1) // contract to update box is 2nd input
  val contractToUpdateBoxOut = OUTPUTS(1) // copy of contract to update box is the 2nd output

  // compute the hash of the contract to update output box. This should be the value voted for
  val contractToUpdateBoxOutHash = blake2b256(contractToUpdateBoxOut.propositionBytes)

  val validContractToUpdateIn = contractToUpdateBoxIn.tokens.size >= 1 &&
                                    contractToUpdateBoxIn.tokens(0)._1 == contractToUpdateNFT

  val validContractToUpdateOut = contractToUpdateBoxIn.tokens == contractToUpdateBoxOut.tokens &&
                                    contractToUpdateBoxIn.value == contractToUpdateBoxOut.value

  // burning of update NFT token is not possible, but it is possible to update a contract to
  // not to rely to update NFT anymore
  val validUpdateOut = SELF.tokens == updateBoxOut.tokens &&
                       SELF.propositionBytes == updateBoxOut.propositionBytes &&
                       updateBoxOut.value >= SELF.value

  def isValidBallot(b:Box) = {
    b.tokens.size > 0 &&
    b.tokens(0)._1 == ballotTokenId &&
    b.R5[Coll[Byte]].get == SELF.id && // ensure vote corresponds to this box ****
    b.R6[Coll[Byte]].get == contractToUpdateBoxOutHash // check value voted for
  }

  val ballotBoxes = INPUTS.filter(isValidBallot)

  val votesCount = ballotBoxes.fold(0L, {(accum: Long, b: Box) => accum + b.tokens(0)._2})

  val validVotes = votesCount >= minVotes

  sigmaProp(validContractToUpdateIn && validContractToUpdateOut && validUpdateIn && validUpdateOut && validVotes)
}