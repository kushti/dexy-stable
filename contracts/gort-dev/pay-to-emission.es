{

  // todo: is the contract needed, or emission contract can be paid directly ?

  val emissionOutput = OUTPUTS(0)
  val emissionInput = INPUTS(0)

  sigmaProp (
    INPUTS.size == 2 && // only 2 inputs to disallow spending multiple pay-2-emission boxes with one emission box
    emissionInput.tokens(0)._1 == fromBase64("$gortDevEmissionNFT") &&
    emissionOutput.tokens(1)._1 == SELF.tokens(0)._1 &&
    emissionOutput.tokens(1)._2 - emissionInput.tokens(1)._2 == SELF.tokens(0)._2
  )

}