package offchain

import io.circe.parser.parse
import org.ergoplatform.{DataInput, ErgoBox, ErgoBoxCandidate, ErgoScriptPredef, UnsignedInput}
import org.ergoplatform.ErgoBox.R4
import org.ergoplatform.http.api.ApiCodecs
import org.ergoplatform.modifiers.mempool.{ErgoTransaction, ErgoTransactionSerializer, UnsignedErgoTransaction}
import org.ergoplatform.nodeView.state.{ErgoStateContext, VotingData}
import org.ergoplatform.settings.{ErgoSettings, ErgoValidationSettings, LaunchParameters}
import org.ergoplatform.wallet.Constants.eip3DerivationPath
import org.ergoplatform.wallet.interpreter.{ErgoProvingInterpreter, TransactionHintsBag}
import org.ergoplatform.wallet.secrets.JsonSecretStorage
import org.ergoplatform.wallet.serialization.JsonCodecsWrapper
import org.ergoplatform.wallet.settings.SecretStorageSettings
import scalaj.http.{Http, HttpOptions}
import scorex.util.encode.Base16
import sigmastate.interpreter.ContextExtension
import org.ergoplatform.wallet.interface4j.SecretString


case class DexyScanIds(tracking95ScanId: Int,
                       tracking98ScanId: Int,
                       tracking101ScanId: Int,
                       oraclePoolScanId: Int,
                       lpScanId: Int)

class OffchainUtils(serverUrl: String,
                    apiKey: String,
                    localSecretStoragePath: String,
                    localSecretUnlockPass: String,
                    dexyScanIds: DexyScanIds) extends ApiCodecs {
  val fee = 1000000L

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

  def unspentScanBoxes(scanId: Int): Seq[ErgoBox] = {
    val scanUnspentUrl = s"$serverUrl/scan/unspentBoxes/$scanId?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(scanUnspentUrl)).toOption.get
    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def fetchWalletInputs(): Seq[ErgoBox] = {
    val boxesUnspentUrl = s"$serverUrl/wallet/boxes/unspent?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(boxesUnspentUrl)).toOption.get

    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def tracking95Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking98Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking101Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def oraclePoolBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.oraclePoolScanId).headOption

  def lpBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.lpScanId).headOption

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

    val stateContext = new ErgoStateContext(Seq.empty, None, settings.chainSettings.genesisStateDigest, LaunchParameters, ErgoValidationSettings.initial,
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

  def updateTracker101(alarm: Boolean): Array[Byte] = {
    val feeInputs = fetchWalletInputs().take(3)
    val trackingBox = tracking101Box().head
    val inputBoxes = (IndexedSeq(trackingBox) ++ feeInputs)
    val inputsHeight = inputBoxes.map(_.creationHeight).max

    val inputs = inputBoxes.map(b => new UnsignedInput(b.id, ContextExtension.empty))
    val dataInputBoxes = IndexedSeq(oraclePoolBox().get, lpBox().get)
    val dataInputs = dataInputBoxes.map(b => DataInput.apply(b.id))

    val changeValue = inputBoxes.map(_.value).sum - fee
    val changeBox = new ErgoBoxCandidate(changeValue, feeInputs.head.ergoTree, inputsHeight)
    val outputs = IndexedSeq(trackingBox.toCandidate, changeBox, feeOut(inputsHeight))

    val utx = new UnsignedErgoTransaction(inputs, dataInputs, outputs)
    signTransaction("tracking95 update: ", utx, inputBoxes, dataInputBoxes)
  }
}

object Test extends App {
  val scanIds = DexyScanIds(33, 34, 35, 32, 45)

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/mainnet/.ergo/wallet/keystore",
    localSecretUnlockPass = "",
    dexyScanIds = scanIds)

  val oraclePrice = utils.oraclePoolBox().get.additionalRegisters.apply(R4).value.asInstanceOf[Long]
  val lpBox = utils.lpBox().get
  val lpPrice = lpBox.value / lpBox.additionalTokens.apply(2)._2

  println(oraclePrice / 1000000)
  println(lpPrice)

  val x = oraclePrice * 101
  val y = lpPrice * 100

  println(x > y)

  val currentHeight = utils.currentHeight()

  utils.updateTracker101(alarm = true)

}
