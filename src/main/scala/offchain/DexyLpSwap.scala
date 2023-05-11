package offchain

import dexy.DexySpec
import org.ergoplatform.ErgoBox.{R4, TokenId}
import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.TokensMap
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoAddressEncoder, ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.crypto.hash.Digest32
import scorex.util.{ModifierId, idToBytes}
import special.collection.Coll
import sigmastate.eval._
import sigmastate.eval.Extensions._

object DexyLpSwap extends App {
  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = OffchainUtils.scanIds)

  val oracleScanId = 32 // oracle box
  val dexyLpScanId = 71  // ERG/dexy LP scan id
  val dexySwapScanId = 67 // swap action scan id

  def oracleBox() = utils.fetchSingleBox(oracleScanId)

  def lpBox() = utils.fetchSingleBox(dexyLpScanId)

  def tokensMapToColl(tokens: TokensMap): Coll[(TokenId, Long)] =
    tokens.toSeq.map {t => (Digest32 @@ idToBytes(t._1)) -> t._2}.toArray.toColl

  def inject(nanoErgs: Long, dexyAmount: Long) = {
    require(nanoErgs == 0 || dexyAmount == 0, "One of nanoErgs, dexyAmount should be 0")
    val lpInput = lpBox()
    val swapInput = utils.fetchSingleBox(dexySwapScanId)

    val creationHeight = utils.currentHeight()

    val feeOut = utils.feeOut(creationHeight)
    val ergNeeded = feeOut.value + nanoErgs

    val targetTokens: Map[ModifierId, Long] = if (dexyAmount > 0) {
      Map(ModifierId @@ DexySpec.dexyTokenId -> dexyAmount)
    } else {
      Map.empty
    }

    val selectionResult = DefaultBoxSelector.select[ErgoBox](
      utils.fetchWalletInputs().toIterator,
      (_: ErgoBox) => true,
      ergNeeded,
      targetTokens
    ).right.toOption.get

    val inputBoxes = IndexedSeq(lpInput, swapInput) ++ selectionResult.boxes
    val inputs = inputBoxes.map(b => new UnsignedInput(b.id))

    val lpOutput: ErgoBoxCandidate = new ErgoBoxCandidate(
      lpInput.value + nanoErgs,
      lpInput.ergoTree,
      creationHeight,
      lpInput.additionalTokens.updated(2, lpInput.additionalTokens(2)._1 -> (lpInput.additionalTokens(2)._2 + dexyAmount)),
      lpInput.additionalRegisters
    )
    val swapOutput = new ErgoBoxCandidate(
      swapInput.value,
      swapInput.ergoTree,
      creationHeight,
      swapInput.additionalTokens,
      swapInput.additionalRegisters
    )

    val outputs = IndexedSeq(lpOutput, swapOutput) ++ utils.changeOuts(selectionResult, creationHeight) ++ IndexedSeq(feeOut)

    val unsignedSwapTx = new UnsignedErgoTransaction(inputs, IndexedSeq.empty, outputs)
    utils.signTransaction("LP swap: ", unsignedSwapTx, inputBoxes, IndexedSeq.empty)
  }

  val lpState = lpBox()
  val dexPrice = lpState.value / lpState.additionalTokens.toArray.last._2

  val oracleState = oracleBox()
  val oraclePrice = oracleState.additionalRegisters(R4).value.asInstanceOf[Long] / 1000000L

  println("DEX price: " + dexPrice)
  println("Oracle price: " + oraclePrice)
  println("Ratio: " + dexPrice.toDouble / oraclePrice)
  inject(500000000000L,0)
}
