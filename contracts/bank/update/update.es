{
  // Bank update script. Taken from SigmaUSD contracts.
  //
  // This box (update box):
  // Registers empty
  //
  // ballot boxes (in other inputs)
  // R4 the group element of the owner of the ballot token [GroupElement]
  // R5 the box id of this box [Coll[Byte]]
  // R6 the value voted for [Coll[Byte]]

  val bankNFT = fromBase64("$bankNFT")

  val ballotTokenId = fromBase64("$ballotTokenId")

  // todo: consider proper value before deployment
  val minVotes = 3

  // collect and update in one step
  val updateBoxOut = OUTPUTS(0) // copy of this box is the 1st output
  val validUpdateIn = SELF.id == INPUTS(0).id // this is 1st input

  val bankBoxIn = INPUTS(1) // bank box is 2nd input
  val bankBoxOut = OUTPUTS(1) // copy of bank box is the 2nd output

  // compute the hash of the bank output box. This should be the value voted for
  val bankBoxOutHash = blake2b256(bankBoxOut.propositionBytes)

  val validBankIn = bankBoxIn.tokens.size == 2 && bankBoxIn.tokens(0)._1 == bankNFT
  val validBankOut = bankBoxIn.tokens == bankBoxOut.tokens &&
                     bankBoxIn.value == bankBoxOut.value


  val validUpdateOut = SELF.tokens == updateBoxOut.tokens &&
                       SELF.propositionBytes == updateBoxOut.propositionBytes &&
                       updateBoxOut.value >= SELF.value

  def isValidBallot(b:Box) = {
    b.tokens.size > 0 &&
    b.tokens(0)._1 == ballotTokenId &&
    b.R5[Coll[Byte]].get == SELF.id && // ensure vote corresponds to this box ****
    b.R6[Coll[Byte]].get == bankBoxOutHash // check value voted for
  }

  val ballotBoxes = INPUTS.filter(isValidBallot)

  val votesCount = ballotBoxes.fold(0L, {(accum: Long, b: Box) => accum + b.tokens(0)._2})

  sigmaProp(validBankIn && validBankOut && validUpdateIn && validUpdateOut && votesCount >= minVotes)
}