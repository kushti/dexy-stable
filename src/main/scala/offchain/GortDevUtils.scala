package offchain

import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.util.encode.Base16
import sigmastate.Values.ByteConstant
import sigmastate.interpreter.ContextExtension

import scala.util.Try

/**
 * Offchain functions to work with GORT dev emission contract
 */
object GortDevUtils extends App {

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = OffchainUtils.scanIds)

  val pay2DevEmissionScanId: Int = ???
  val devEmissionScanId: Int = ???

  def pay2GortDevEmission(): Option[ErgoBox] = utils.unspentScanBoxes(pay2DevEmissionScanId).headOption

  def gortDevEmission(): Option[ErgoBox] = utils.unspentScanBoxes(devEmissionScanId).headOption

  // merge pay-to-emission and emission boxes
  def merge() = {
    val res = Try {
      val creationHeight = utils.currentHeight()

      val emissionInputBox = gortDevEmission().get
      val pay2EmissionInputBox = pay2GortDevEmission().get

      val inputBoxes = IndexedSeq(emissionInputBox, pay2EmissionInputBox)
      val inputValue = inputBoxes.map(_.value).sum

      val inputs = IndexedSeq(new UnsignedInput(emissionInputBox.id, ContextExtension(Map(0.toByte -> ByteConstant(0)))), new UnsignedInput(pay2EmissionInputBox.id))

      val feeOut = utils.feeOut(creationHeight)

      require(inputValue - feeOut.value >= emissionInputBox.value)

      val inGort = emissionInputBox.additionalTokens.apply(1)
      val emissionOutTokens = emissionInputBox.additionalTokens.updated(1, inGort._1 -> (inGort._2 + pay2EmissionInputBox.additionalTokens.apply(0)._2))
      val emissionOutRegs = emissionInputBox.additionalRegisters
      val emissionOut = new ErgoBoxCandidate(inputValue - feeOut.value, emissionInputBox.ergoTree, creationHeight, emissionOutTokens, emissionOutRegs)

      val outs = IndexedSeq(
        emissionOut,
        feeOut
      )

      val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outs)
      val txId = utils.signTransaction("Merge: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty)
      Base16.encode(txId)
    }
    println("Merge result: " + res)
  }

  def payout() = {
    val res = Try {
      val creationHeight = utils.currentHeight()
      val emissionInputBox = gortDevEmission().get
      
      val feeOut = utils.feeOut(creationHeight)

    }
    println("Payout result: " + res)
  }

}
