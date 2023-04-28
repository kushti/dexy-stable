package offchain

import io.circe.parser.parse
import offchain.DexyLpSwap.tokensMapToColl
import org.ergoplatform.{DataInput, ErgoAddressEncoder, ErgoBox, ErgoBoxCandidate, ErgoScriptPredef, UnsignedInput}
import org.ergoplatform.ErgoBox.{R4, R7}
import org.ergoplatform.http.api.ApiCodecs
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.modifiers.mempool.{ErgoTransaction, ErgoTransactionSerializer, UnsignedErgoTransaction}
import org.ergoplatform.nodeView.state.{ErgoStateContext, VotingData}
import org.ergoplatform.settings.{ErgoSettings, ErgoValidationSettings, LaunchParameters}
import org.ergoplatform.wallet.Constants.eip3DerivationPath
import org.ergoplatform.wallet.boxes.BoxSelector.BoxSelectionResult
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.wallet.interpreter.{ErgoProvingInterpreter, TransactionHintsBag}
import org.ergoplatform.wallet.secrets.JsonSecretStorage
import org.ergoplatform.wallet.settings.SecretStorageSettings
import scalaj.http.{Http, HttpOptions}
import scorex.util.encode.Base16
import sigmastate.interpreter.ContextExtension
import org.ergoplatform.wallet.interface4j.SecretString
import scorex.util.ModifierId
import sigmastate.Values.IntConstant


case class DexyScanIds(tracking95ScanId: Int,
                       tracking98ScanId: Int,
                       tracking101ScanId: Int,
                       oraclePoolScanId: Int,
                       lpScanId: Int,
                       lpSwapScanId: Int)

class OffchainUtils(serverUrl: String,
                    apiKey: String,
                    localSecretStoragePath: String,
                    localSecretUnlockPass: String,
                    dexyScanIds: DexyScanIds) extends ApiCodecs {
  val fee = 1000000L
  val eae = new ErgoAddressEncoder(ErgoAddressEncoder.TestnetNetworkPrefix)
  //todo: get change address via api from server
  val changeAddress = eae.fromString("3WwhifgHTu7ib5ggKKVFaN1J6jFim3u9siPspDRq9JnwcKfLuuxc").get

  def feeOut(creationHeight: Int): ErgoBoxCandidate = {
    new ErgoBoxCandidate(fee, ErgoScriptPredef.feeProposition(720), creationHeight) // 0.002 ERG
  }

  def getJsonAsString(url: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .header("api_key", apiKey)
      .option(HttpOptions.readTimeout(10000))
      .asString
      .body
  }

  def currentHeight(): Int = {
    val infoUrl = s"$serverUrl/info"
    val json = parse(getJsonAsString(infoUrl)).toOption.get
    json.\\("fullHeight").head.asNumber.get.toInt.get
  }

  def lastHeader(): Header = {
    val infoUrl = s"$serverUrl/blocks/lastHeaders/1"
    val json = parse(getJsonAsString(infoUrl)).toOption.get
    json.as[Seq[Header]].toOption.get.head
  }

  def unspentScanBoxes(scanId: Int): Seq[ErgoBox] = {
    val scanUnspentUrl = s"$serverUrl/scan/unspentBoxes/$scanId?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(scanUnspentUrl)).toOption.get
    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def fetchSingleBox(scanId: Int): ErgoBox =  {
    unspentScanBoxes(scanId).head
  }

  def fetchWalletInputs(): Seq[ErgoBox] = {
    val boxesUnspentUrl = s"$serverUrl/wallet/boxes/unspent?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(boxesUnspentUrl)).toOption.get

    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def tracking95Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking98Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking98ScanId).headOption

  def tracking101Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking101ScanId).headOption

  def oraclePoolBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.oraclePoolScanId).headOption

  def lpBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.lpScanId).headOption

  def changeOuts(selectionResult: BoxSelectionResult[ErgoBox], creationHeight: Int): IndexedSeq[ErgoBoxCandidate] ={
    selectionResult.changeBoxes.toIndexedSeq.map{ba =>
      val tokensMap = tokensMapToColl(ba.tokens)
      new ErgoBoxCandidate(ba.value, changeAddress.script, creationHeight, tokensMap)
    }
  }

  def signTransaction(txName: String,
                      unsignedTransaction: UnsignedErgoTransaction,
                      boxesToSpend: IndexedSeq[ErgoBox],
                      dataBoxes: IndexedSeq[ErgoBox]): Array[Byte] = {
    val settings = ErgoSettings.read()
    val sss = SecretStorageSettings(localSecretStoragePath, settings.walletSettings.secretStorage.encryption)
    val jss = JsonSecretStorage.readFile(sss).get
    jss.unlock(SecretString.create(localSecretUnlockPass))
    val masterKey = jss.secret.get
    val changeKey = masterKey.derive(eip3DerivationPath)

    val prover = ErgoProvingInterpreter(IndexedSeq(masterKey, changeKey), LaunchParameters)

    val bestHeader = lastHeader()

    val stateContext = new ErgoStateContext(Seq(bestHeader), None, settings.chainSettings.genesisStateDigest, LaunchParameters, ErgoValidationSettings.initial,
      VotingData.empty)(settings) {
      override val blockVersion = 2: Byte
    }

    val matchingTx = ErgoTransaction(prover
      .sign(unsignedTransaction, boxesToSpend, dataBoxes, stateContext, TransactionHintsBag.empty)
      .get)

    val txBytes = ErgoTransactionSerializer.toBytes(matchingTx)
    println(s"$txName tx bytes: " + Base16.encode(txBytes))
    txBytes
  }

  def updateTracker101(alarmHeight: Option[Int]): Array[Byte] = {

    val creationHeight = currentHeight()

    val feeOutput = feeOut(creationHeight)

    val selectionResult = DefaultBoxSelector.select[ErgoBox](
      fetchWalletInputs().toIterator,
      (_: ErgoBox) => true,
      feeOutput.value,
      Map.empty[ModifierId, Long]
    ).right.toOption.get

    val trackingBox = tracking101Box().head
    val inputBoxes = IndexedSeq(trackingBox) ++ selectionResult.boxes
    val inputsHeight = inputBoxes.map(_.creationHeight).max

    val inputs = inputBoxes.map(b => new UnsignedInput(b.id, ContextExtension.empty))
    val dataInputBoxes = IndexedSeq(oraclePoolBox().get, lpBox().get)
    val dataInputs = dataInputBoxes.map(b => DataInput.apply(b.id))

    val updRegisters = trackingBox.additionalRegisters.updated(R7, IntConstant(alarmHeight.getOrElse(Int.MaxValue)))
    val updTracking = new ErgoBoxCandidate(trackingBox.value,
                                           trackingBox.ergoTree,
                                           inputsHeight,
                                           trackingBox.additionalTokens,
                                           updRegisters)

    println("t: " + trackingBox)
    println("ut: " + updTracking)

    val outputs = IndexedSeq(updTracking) ++ changeOuts(selectionResult, creationHeight) ++ IndexedSeq(feeOutput)

    val utx = new UnsignedErgoTransaction(inputs, dataInputs, outputs)
    signTransaction("tracking101 update: ", utx, inputBoxes, dataInputBoxes)
  }
}

object OffchainUtils {
  val scanIds = DexyScanIds(59, 60, 61, 32, 71, 67)
}

object Test extends App {
  // tracking95ScanId
  // tracking98ScanId
  // tracking101ScanId
  // oraclePoolScanId
  // lpScanId

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = OffchainUtils.scanIds)

  val oraclePrice = utils.oraclePoolBox().get.additionalRegisters.apply(R4).value.asInstanceOf[Long]
  val lpBox = utils.lpBox().get
  val lpPrice = lpBox.value / lpBox.additionalTokens.apply(2)._2

  println("oracle price: " + oraclePrice / 1000000)
  println("lp price: " + lpPrice)

  val x = oraclePrice * 101
  val y = lpPrice * 100

  println(x > y)

  val currentHeight = utils.currentHeight()

  utils.updateTracker101(Some(currentHeight))

}
