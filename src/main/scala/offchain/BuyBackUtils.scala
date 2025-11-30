package offchain

import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.util.ModifierId

/**
 * Offchain functions to work with GORT buyback contract
 */
object BuyBackUtils extends App {
  val fakeScanIds = DexyScanIds(1, 1, 1, 1, 1, 1)

  val buyBackScanId = 50
  val gortLpScanId = 23

  val utils = new OffchainUtils(
    serverUrl = "http://127.0.0.1:9053",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/local/.ergo/wallet/keystore",
    localSecretUnlockPass = "",
    dexyScanIds = fakeScanIds)

  def buyBackBox(): Option[ErgoBox] = utils.unspentScanBoxes(buyBackScanId).headOption

  def gortLp(): Option[ErgoBox] = utils.unspentScanBoxes(gortLpScanId).headOption

  /* todo: uncomment and fix
  def topUp() = {
    // Top-up:
    //
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0               |                |
    // 1               |                |
    // 2               |  BuyBack       |

    val toAdd = 1 * 1000000000 // in nanoerg, 1 ERG

    val creationHeight = utils.currentHeight()
    val feeOut = utils.feeOut(creationHeight)

    val selectionResult =  new DefaultBoxSelector(None).select[ErgoBox](
        utils.fetchWalletInputs().toIterator,
        (_: ErgoBox) => true,
        toAdd + feeOut.value,
        Map.empty[ModifierId, Long]
      ).right.toOption.get

    val buybackInputBox = buyBackBox().get
    val buyBackInputBoxes = selectionResult.inputBoxes.toIndexedSeq

    val buyBackOutput = new ErgoBoxCandidate(
      buybackInputBox.value + toAdd,
      buybackInputBox.ergoTree,
      creationHeight,
      buybackInputBox.additionalTokens,
      buybackInputBox.additionalRegisters
    )

    val changeBoxes = utils.changeOuts(selectionResult, creationHeight)
    val outs = (if(changeBoxes.size == 1) {
      changeBoxes ++ Seq(feeOut, buyBackOutput)
    } else {
      changeBoxes.take(2) ++ Seq(buybackInputBox) ++ changeBoxes.drop(2) ++ Seq(feeOut)
    }).toIndexedSeq

    val buyBackInput = new UnsignedInput(buybackInputBox.id, ContextExtension(Map((0: Byte) -> IntConstant(1))))
    val inputs = buyBackInput +: buyBackInputBoxes.map(b => new UnsignedInput(b.id))
    val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outs)
    utils.signTransaction("Buyback: ", unsignedSwapTx, buybackInputBox +: buyBackInputBoxes, IndexedSeq.empty)
  } */

/* todo: uncomment and fix
  def buyback() = {
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |
    // 1 BuyBack       |  BuyBack       |

    val lpInput = gortLp().get
    val buyBackInput = buyBackBox().get

    val creationHeight = utils.currentHeight()
    val feeOut = utils.feeOut(creationHeight)

    val selectionResult =  new DefaultBoxSelector(None).select(
      utils.fetchWalletInputs().toIterator,
      (eb: ErgoBox) => eb.additionalTokens.isEmpty,
      feeOut.value,
      Map.empty
    ).right.toOption.get

    val inputBoxes = IndexedSeq(lpInput, buyBackInput) ++ selectionResult.inputBoxes
    println("input boxes: " + inputBoxes.drop(2))
    val inputs = inputBoxes.map(b => new UnsignedInput(b.id)).updated(1, new UnsignedInput(buyBackInput.id, ContextExtension(Map((0: Byte) -> IntConstant(0)))))

    assert(buyBackInput.value >= 1000000000, "Less than 1 ERG in buyback input")
    val initErgAmt =  buyBackInput.value - 10000000
    val gortObtained = lpInput.additionalTokens(2)._2 * initErgAmt * 997 / (lpInput.value * 1000 + initErgAmt * 997) - 1
    val lpPrice = lpInput.value / lpInput.additionalTokens(2)._2
    val ergAmt = (gortObtained * lpPrice * 1.005).toLong

    println("erg spent: " + ergAmt)
    println("gort obtained: " + gortObtained)

    val lpInputGorts = lpInput.additionalTokens(2)
    val lpOutput = new ErgoBoxCandidate(
      lpInput.value + ergAmt,
      lpInput.ergoTree,
      creationHeight,
      lpInput.additionalTokens.updated(2, lpInputGorts._1 -> (lpInputGorts._2 - gortObtained)),
      lpInput.additionalRegisters
    )

    val bbInputGorts = buyBackInput.additionalTokens(1)
    val buyBackOutput: ErgoBoxCandidate = new ErgoBoxCandidate(
      buyBackInput.value - ergAmt,
      buyBackInput.ergoTree,
      creationHeight,
      buyBackInput.additionalTokens.updated(1, bbInputGorts._1 -> (bbInputGorts._2 + gortObtained)),
      buyBackInput.additionalRegisters
    )

    val outputs = IndexedSeq(lpOutput, buyBackOutput) ++ utils.changeOuts(selectionResult, creationHeight) ++ IndexedSeq(feeOut)

    val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outputs)
    utils.signTransaction("Buyback: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty)
  } */

 // buyback()

}
