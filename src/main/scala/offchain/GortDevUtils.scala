package offchain

import org.ergoplatform.ErgoBox.R4
import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoAddressEncoder, ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.util.ModifierId
import scorex.util.encode.Base16
import scala.util.Try

/**
 * Offchain functions to work with GORT dev emission contract
 */
object GortDevUtils extends App {

  val utils = new OffchainUtils(
    serverUrl = "http://127.0.0.1:9053",
    apiKey = "hello",
    localSecretStoragePath = "/home/kushti/ergo/backup/gortkeystore",
    localSecretUnlockPass = "wpass",
    dexyScanIds = OffchainUtils.scanIds)

  val devEmissionScanId: Int = 45

  def gortDevEmission(): Option[ErgoBox] = utils.unspentScanBoxes(devEmissionScanId).headOption

  /*
todo: uncomment and fix

  def payout(): Unit = {
    val res = Try {
      val eae = new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix)

      val currentHeight = utils.currentHeight()
      val feeOut = utils.feeOut(currentHeight)

      val emissionInputBox = gortDevEmission().get

      val inGort = emissionInputBox.additionalTokens.apply(1)

      val selectionResult = new DefaultBoxSelector(None).select[ErgoBox](
        utils.fetchWalletInputs().toIterator,
        (_: ErgoBox) => true,
        2 * feeOut.value,
        Map.empty[ModifierId, Long]
      ).right.toOption.get

      val inputBoxes = IndexedSeq(emissionInputBox) ++ selectionResult.inputBoxes

      val ce = ContextExtension(Map(0.toByte -> ShortConstant(0), 1.toByte -> ByteConstant(1)))
      val inputs = IndexedSeq(new UnsignedInput(emissionInputBox.id, ce)) ++
        selectionResult.inputBoxes.map(b => new UnsignedInput(b.id))

      // R4 (int) - last payment height
      // R5 (SigmaProp) - auth
      val inRegs = emissionInputBox.additionalRegisters
      val prevPaymentHeight = inRegs(R4).value.asInstanceOf[Int]

      val toWithdraw = Math.min(inGort._2 - 1, currentHeight - prevPaymentHeight)

      val outRegs = inRegs.toMap.updated(R4, IntConstant(currentHeight))
      val outTokens = emissionInputBox.additionalTokens.updated(1, inGort._1 -> (inGort._2 - toWithdraw))

      val emissionOut = new ErgoBoxCandidate(emissionInputBox.value, emissionInputBox.ergoTree, currentHeight, outTokens, outRegs)

      val receiver = eae.fromString("9iMthnqRkMD3ECUuxs29XmAktbPSvt1LpAJmonmxg2JvMQ8XAdP").get.script
      val receiverTokens = emissionInputBox.additionalTokens.slice(1,2).updated(0, inGort._1 -> toWithdraw)
      val rewardOut = new ErgoBoxCandidate(feeOut.value, receiver, currentHeight, receiverTokens)

      val outs = IndexedSeq(emissionOut) ++ utils.changeOuts(selectionResult, currentHeight) ++ IndexedSeq(rewardOut, feeOut)

      println("ib: " + emissionInputBox.additionalRegisters)
      println("ob: " + emissionOut.additionalRegisters)

      val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outs)
      val txId = utils.signTransaction("Payout: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty, Some("/home/kushti/ergo/backup/localkeystore"))
      Base16.encode(txId)
    }
    println("Payout result: " + res)
  } */

  // payout()
}
