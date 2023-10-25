package offchain

import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.util.ModifierId
import sigmastate.Values.{ByteConstant, IntConstant}
import sigmastate.interpreter.ContextExtension

object BuyBackUtils extends App {
  val fakeScanIds = DexyScanIds(1, 1, 1, 1, 1, 1, 22)

  val utils = new OffchainUtils(
    serverUrl = "http://127.0.0.1:9053",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/local/.ergo/wallet/keystore",
    localSecretUnlockPass = "",
    dexyScanIds = fakeScanIds)

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

    val selectionResult = DefaultBoxSelector.select[ErgoBox](
        utils.fetchWalletInputs().toIterator,
        (_: ErgoBox) => true,
        toAdd + feeOut.value,
        Map.empty[ModifierId, Long]
      ).right.toOption.get

    val buybackInputBox = utils.buyBackBox().get
    val buyBackInputBoxes = (selectionResult.boxes).toIndexedSeq

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
  }


  def buyback() = {
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |
    // 1 BuyBack       |  BuyBack       |

    val lpInput = utils.lpBox().get
    val buyBackInput = utils.buyBackBox().get

    val creationHeight = utils.currentHeight()
    val feeOut = utils.feeOut(creationHeight)

    val selectionResult = DefaultBoxSelector.select(
      utils.fetchWalletInputs().toIterator,
      (_: ErgoBox) => true,
      feeOut.value,
      Map.empty
    ).right.toOption.get

    val inputBoxes = IndexedSeq(lpInput, buyBackInput) ++ selectionResult.boxes
    val inputs = inputBoxes.map(b => new UnsignedInput(b.id))

    assert(buyBackInput.value >= 1000000000, "Less than 1 ERG in buyback input")
    val ergAmt = buyBackInput.value - 100000000
    val gortObtained = lpInput.additionalTokens(2)._2 * ergAmt * 997 / (lpInput.value * 1000 + ergAmt * 997)

    println("gort obtained: " + gortObtained)

    val lpOutput: ErgoBoxCandidate = new ErgoBoxCandidate(
      buyBackInput.value - ergAmt,
      buyBackInput.ergoTree,
      creationHeight,
      buyBackInput.additionalTokens, // todo: change
      buyBackInput.additionalRegisters
    )
    val buyBackOutput = new ErgoBoxCandidate(
      lpInput.value + ergAmt,
      lpInput.ergoTree,
      creationHeight,
      lpInput.additionalTokens, // todo: change
      lpInput.additionalRegisters
    )
    val outputs = IndexedSeq(lpOutput, buyBackOutput) ++ utils.changeOuts(selectionResult, creationHeight) ++ IndexedSeq(feeOut)

    val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outputs)
    utils.signTransaction("Buyback: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty)
  }

  buyback()

}
