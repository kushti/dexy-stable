package offchain

import io.circe.parser.parse
import offchain.DexyLpSwap.tokensMapToColl
import org.ergoplatform.{DataInput, ErgoAddressEncoder, ErgoBox, ErgoBoxCandidate, ErgoScriptPredef, P2PKAddress, UnsignedInput}
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

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

sealed trait TrackerType {
  val name: String

  override def toString: String = name
}

object TrackerType {
  val all = Seq(Tracker95, Tracker98, Tracker101)
}

object Tracker95 extends TrackerType {
  override val name: String = "95% tracker"
}

object Tracker98 extends TrackerType {
  override val name: String = "98% tracker"
}

object Tracker101 extends TrackerType {
  override val name: String = "101% tracker"
}

case class DexyScanIds(tracking95ScanId: Int,
                       tracking98ScanId: Int,
                       tracking101ScanId: Int,
                       oraclePoolScanId: Int,
                       lpScanId: Int,
                       lpSwapScanId: Int)

case class OffchainUtils(serverUrl: String,
                    apiKey: String,
                    localSecretStoragePath: String,
                    localSecretUnlockPass: String,
                    dexyScanIds: DexyScanIds) extends ApiCodecs {
  val defaultFee = 1000000L
  val eae = new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix)
  //todo: get change address via api from server
  val changeAddress = eae.fromString("9gZLYYtsC6EUhj4SK2XySR9duVorTcQxHK8oE4ZTdUEpReTXcAK").get

  def feeOut(creationHeight: Int, providedFeeOpt: Option[Long] = None): ErgoBoxCandidate = {
    new ErgoBoxCandidate(providedFeeOpt.getOrElse(defaultFee), ErgoScriptPredef.feeProposition(720), creationHeight) // 0.001 ERG
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

  def postString(url: String, data: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .header("api_key", apiKey)
      .option(HttpOptions.readTimeout(10000))
      .postData(data)
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

  def dexPrice = {
    val lpState = lpBox().get
    lpState.value / lpState.additionalTokens.toArray.last._2
  }

  def oraclePrice = {
    val oracleState = oraclePoolBox().get
    oracleState.additionalRegisters(R4).value.asInstanceOf[Long] / 1000000L
  }

  def changeOuts(selectionResult: BoxSelectionResult[ErgoBox], creationHeight: Int): IndexedSeq[ErgoBoxCandidate] ={
    selectionResult.changeBoxes.toIndexedSeq.map{ba =>
      val tokensMap = tokensMapToColl(ba.tokens)
      new ErgoBoxCandidate(ba.value, changeAddress.script, creationHeight, tokensMap)
    }
  }

  def printlnKey() = {
    val settings = ErgoSettings.read()
    val sss = SecretStorageSettings(localSecretStoragePath, settings.walletSettings.secretStorage.encryption)
    val jss = JsonSecretStorage.readFile(sss).get
    jss.unlock(SecretString.create(localSecretUnlockPass))
    val masterKey = jss.secret.get
    val changeKey = masterKey.derive(eip3DerivationPath)
    println(Base16.encode(changeKey.keyBytes))
  }

  def signTransaction(txName: String,
                      unsignedTransaction: UnsignedErgoTransaction,
                      boxesToSpend: IndexedSeq[ErgoBox],
                      dataBoxes: IndexedSeq[ErgoBox],
                      additionalLocalSecretStoragePath: Option[String] = None): Array[Byte] = {
    val settings = ErgoSettings.read()
    val sss = SecretStorageSettings(localSecretStoragePath, settings.walletSettings.secretStorage.encryption)
    val jss = JsonSecretStorage.readFile(sss).get
    jss.unlock(SecretString.create(localSecretUnlockPass))
    val masterKey = jss.secret.get
    val changeKey = masterKey.derive(eip3DerivationPath)

    val additionalKeys = additionalLocalSecretStoragePath match {
      case Some(localSecretStoragePath) =>
        val sss = SecretStorageSettings(localSecretStoragePath, settings.walletSettings.secretStorage.encryption)
        val jss = JsonSecretStorage.readFile(sss).get
        jss.unlock(SecretString.create(localSecretUnlockPass))
        val masterKey = jss.secret.get
        val changeKey = masterKey.derive(eip3DerivationPath)
        IndexedSeq(masterKey, changeKey)
      case None => IndexedSeq.empty
    }


    val secretKeys = IndexedSeq(masterKey, changeKey) ++ additionalKeys

    val prover = ErgoProvingInterpreter(secretKeys, LaunchParameters)

    implicit val eae = new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix)

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

  private def fetchTrackingBox(trackerType: TrackerType) = {
    (trackerType match {
      case Tracker95 => tracking95Box()
      case Tracker98 => tracking98Box()
      case Tracker101 => tracking101Box()
    }).head
  }

  def updateTracker(alarmHeight: Option[Int], trackerType: TrackerType): String = {

    val creationHeight = currentHeight()

    val feeOutput = feeOut(creationHeight)

    val selectionResultEither = DefaultBoxSelector.select[ErgoBox](
      fetchWalletInputs().toIterator,
      (_: ErgoBox) => true,
      feeOutput.value,
      Map.empty[ModifierId, Long]
    )
    val selectionResult = selectionResultEither.right.toOption.get

    val trackingBox = fetchTrackingBox(trackerType)
    println("tb: " + trackingBox)
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

    val outputs = IndexedSeq(updTracking) ++ changeOuts(selectionResult, creationHeight) ++ IndexedSeq(feeOutput)

    val utx = new UnsignedErgoTransaction(inputs, dataInputs, outputs)
    val txbytes = signTransaction(trackerType.name + " update: ", utx, inputBoxes, dataInputBoxes)
    val resp = postString(s"$serverUrl/transactions/bytes", Base16.encode(txbytes))
    println(s"$trackerType update tx id: $resp")
    resp
  }

  def trackersActions(): (Seq[TrackerType], Seq[TrackerType]) = {
    val trackersToSet = ArrayBuffer[TrackerType]()
    val trackersToReset = ArrayBuffer[TrackerType]()

    val lpPrice = Test.lpPrice

    println("Oracle price in tracker: " + oraclePrice)
    println("LP price in tracker: " + lpPrice)
    TrackerType.all.foreach { trackerType =>
      val coeff = trackerType match {
        case Tracker95 => 95
        case Tracker98 => 98
        case Tracker101 => 101
      }
      val shouldBeSet = if (coeff < 100) {
        coeff * oraclePrice > lpPrice * 100
      } else {
        coeff * oraclePrice < lpPrice * 100
      }
      val isSet = fetchTrackingBox(trackerType).additionalRegisters.get(R7).get.value.asInstanceOf[Int] != Int.MaxValue
      println(s"$trackerType should be set: " + shouldBeSet + " is set: " + isSet)
      if(shouldBeSet && !isSet) {
        trackersToSet += trackerType
      }
      if(!shouldBeSet && isSet) {
        trackersToReset += trackerType
      }
    }
    trackersToSet -> trackersToReset
  }

  def updateTrackers() = {
    val (trackersToSet, trackersToReset) = trackersActions()
    if (trackersToSet.nonEmpty) {
      val height = currentHeight()
      trackersToSet.foreach { trackerType =>
        updateTracker(Some(height), trackerType)
        Thread.sleep(200)
      }
    }
    trackersToReset.foreach { trackerType =>
      updateTracker(None, trackerType)
      Thread.sleep(200)
    }
  }

}

object OffchainUtils {
  val scanIds = DexyScanIds(126, 127, 128, 85, 138, 134)
}

object Test extends App {

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = OffchainUtils.scanIds)

  def lpBox = utils.lpBox().get
  def lpPrice = lpBox.value / lpBox.additionalTokens.apply(2)._2

  while (true) {
    Try {
      val oraclePrice = utils.oraclePoolBox().get.additionalRegisters.apply(R4).value.asInstanceOf[Long]

      println("oracle price: " + oraclePrice / 1000000)
      println("lp price: " + lpPrice)

      val x = oraclePrice * 101
      val y = lpPrice * 100

      println(x > y)
      utils.updateTrackers()
    }
    Thread.sleep(60000) // 1 min
  }
}
