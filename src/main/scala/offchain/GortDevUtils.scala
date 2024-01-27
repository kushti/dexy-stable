package offchain

import offchain.BuyBackUtils.utils
import org.ergoplatform.ErgoBox.R4
import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigmastate.Values.{ByteConstant, IntConstant}
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
      val currentHeight = utils.currentHeight()

      val emissionInputBox = gortDevEmission().get
      val pay2EmissionInputBox = pay2GortDevEmission().get

      val inputBoxes = IndexedSeq(emissionInputBox, pay2EmissionInputBox)
      val inputValue = inputBoxes.map(_.value).sum

      val inputs = IndexedSeq(new UnsignedInput(emissionInputBox.id, ContextExtension(Map(0.toByte -> ByteConstant(0)))), new UnsignedInput(pay2EmissionInputBox.id))

      val feeOut = utils.feeOut(currentHeight)

      require(inputValue - feeOut.value >= emissionInputBox.value)

      val inGort = emissionInputBox.additionalTokens.apply(1)
      val emissionOutTokens = emissionInputBox.additionalTokens.updated(1, inGort._1 -> (inGort._2 + pay2EmissionInputBox.additionalTokens.apply(0)._2))
      val emissionOutRegs = emissionInputBox.additionalRegisters
      val emissionOut = new ErgoBoxCandidate(inputValue - feeOut.value, emissionInputBox.ergoTree, currentHeight, emissionOutTokens, emissionOutRegs)

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
      val currentHeight = utils.currentHeight()
      val feeOut = utils.feeOut(currentHeight)

      val emissionInputBox = gortDevEmission().get

      //todo: add inputs to pay fee, change outs

      val inGort = emissionInputBox.additionalTokens.apply(1)

      val selectionResult = DefaultBoxSelector.select[ErgoBox](
        utils.fetchWalletInputs().toIterator,
        (_: ErgoBox) => true,
        feeOut.value,
        Map.empty[ModifierId, Long]
      ).right.toOption.get

      val inputBoxes = IndexedSeq(emissionInputBox) ++ selectionResult.boxes

      val inputs = IndexedSeq(new UnsignedInput(emissionInputBox.id, ContextExtension(Map(0.toByte -> ByteConstant(1))))) ++
        selectionResult.boxes.map(b => new UnsignedInput(b.id))

      // R4 (int) - last payment height
      // R5 (SigmaProp) - auth
      val inRegs = emissionInputBox.additionalRegisters
      val prevPaymentHeight = inRegs(R4).value.asInstanceOf[Int]

      val toWithdraw = Math.min(inGort._2 - 1, currentHeight - prevPaymentHeight)

      val outRegs = inRegs.updated(R4, IntConstant(currentHeight))
      val outTokens = emissionInputBox.additionalTokens.updated(1, inGort._1 -> (inGort._2 - toWithdraw))

      val emissionOut = new ErgoBoxCandidate(emissionInputBox.value, emissionInputBox.ergoTree, currentHeight, outTokens, outRegs)

      val outs = IndexedSeq(emissionOut) ++ utils.changeOuts(selectionResult, currentHeight) ++ IndexedSeq(feeOut)

      val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outs)
      val txId = utils.signTransaction("Payout: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty)
      Base16.encode(txId)
    }
    println("Payout result: " + res)
  }

}
