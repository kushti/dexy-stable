package offchain

import dexy.{DexySpec, TestnetTokenIds}
import dexy.DexySpec.feeDenomLp
import org.ergoplatform.ErgoBox.{R4, TokenId}
import org.ergoplatform.modifiers.mempool.UnsignedErgoTransaction
import org.ergoplatform.wallet.TokensMap
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, UnsignedInput}
import scorex.crypto.hash.Digest32
import scorex.util.{ModifierId, idToBytes}
import special.collection.Coll
import sigmastate.eval._
import sigmastate.eval.Extensions._
import sigmastate.eval.OrderingOps.BigIntOrdering

import scala.math.Ordered.orderingToOrdered

object DexyLpSwap extends App {
  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "ergopass",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "123",
    dexyScanIds = OffchainUtils.scanIds)

  val oracleScanId = utils.dexyScanIds.oraclePoolScanId // oracle box
  val dexyLpScanId = utils.dexyScanIds.lpScanId  // ERG/dexy LP scan id
  val dexySwapScanId = utils.dexyScanIds.lpSwapScanId // swap action scan id

  def oracleBox() = utils.fetchSingleBox(oracleScanId)

  def lpBox() = utils.fetchSingleBox(dexyLpScanId)

  def tokensMapToColl(tokens: TokensMap): Coll[(TokenId, Long)] =
    tokens.toSeq.map {t => (Digest32 @@ idToBytes(t._1)) -> t._2}.toArray.toColl

  def inject(nanoErgs: Long, dexyAmount: Long): Array[Byte] = {
    require(nanoErgs == 0 || dexyAmount == 0, "One of nanoErgs, dexyAmount should be 0")
    val lpInput = lpBox()
    val swapInput = utils.fetchSingleBox(dexySwapScanId)

    val creationHeight = utils.currentHeight()

    val feeOut = utils.feeOut(creationHeight)
    val ergNeeded = feeOut.value + nanoErgs

    val targetTokens: Map[ModifierId, Long] = if (dexyAmount > 0) {
      Map(ModifierId @@ TestnetTokenIds.dexyTokenId -> dexyAmount)
    } else {
      Map.empty
    }

    val selectionResult = DefaultBoxSelector.select[ErgoBox](
      utils.fetchWalletInputs().toIterator,
      (_: ErgoBox) => true,
      ergNeeded,
      targetTokens
    ).right.toOption.get

    val inputErg = lpInput.value
    val inputDexy = lpInput.additionalTokens(2)._2

    val outputErg = if (nanoErgs > 0) {
      inputErg + nanoErgs
    } else {
      ???
    }

    val outputDexy  = if (dexyAmount > 0) {
      inputDexy + dexyAmount
    } else {
      val feeNum = 3
      val feeDenom = 1000
      val deltaDexy = (outputErg - inputErg).toBigInt * (feeNum - feeDenom).toBigInt * inputDexy.toBigInt / (inputErg * feeDenom).toBigInt
      // deltaDexy is negative
      inputDexy + deltaDexy.toLong
    }

    println(s"In ERG: $inputErg, dexy: $inputDexy")
    println(s"Out ERG: $outputErg, dexy: $outputDexy")
    println(s"check: ${(inputDexy - outputDexy) * utils.dexPrice} : ${outputErg - inputErg}")

    val rate = inputDexy.toDouble / inputErg
    val sellX = nanoErgs
    val buyY = (sellX * rate * (feeDenomLp - 3) / feeDenomLp).toLong
    println(s"Double-check: $buyY vs ${inputDexy - outputDexy}")

    val outputDexy2 = ((outputErg - inputErg).toBigInt * (3 - 1000).toBigInt * inputDexy.toBigInt * 1000.toBigInt / (inputErg.toBigInt)) + inputDexy.toBigInt * 1000000L.toBigInt
    println("2: " + (outputDexy2 / 1000000.toBigInt).toLong)

    val outputErg2 = (outputDexy2 - inputDexy.toBigInt) * inputErg.toBigInt * 1000.toBigInt / (inputDexy.toBigInt * (3 - 1000).toBigInt) + inputErg.toBigInt
    println(outputErg2)
    println("left: " + (outputDexy2 - inputDexy.toBigInt) * inputErg.toBigInt * 1000.toBigInt)
    println("right: " + (outputErg2 - inputErg.toBigInt) * (3 - 1000).toBigInt * inputDexy.toBigInt)
    assert((outputDexy2 - inputDexy.toBigInt) * inputErg.toBigInt * 1000.toBigInt >= (outputErg - inputErg).toBigInt * inputDexy.toBigInt * (3 - 1000).toBigInt)

    val inputBoxes = IndexedSeq(lpInput, swapInput) ++ selectionResult.boxes
    val inputs = inputBoxes.map(b => new UnsignedInput(b.id))

    val lpOutput: ErgoBoxCandidate = new ErgoBoxCandidate(
      outputErg,
      lpInput.ergoTree,
      creationHeight,
      lpInput.additionalTokens.updated(2, lpInput.additionalTokens(2)._1 -> outputDexy),
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


  println("DEX price: " + utils.dexPrice)
  println("Oracle price: " + utils.oraclePrice)
  println("Ratio: " + utils.dexPrice.toDouble / utils.oraclePrice)
  println("Pool size: " + lpBox().value / 1000000000L + " ERG")
  inject(200000000000000L,0)
}
