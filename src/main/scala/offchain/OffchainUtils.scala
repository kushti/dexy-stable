package offchain

import io.circe.{Decoder, Json}
import io.circe.parser.parse
import offchain.DexyLpSwap.tokensMapToColl
import org.ergoplatform.{DataInput, ErgoAddressEncoder, ErgoBox, ErgoBoxCandidate, ErgoLikeTransaction, ErgoScriptPredef, ErgoTreePredef, Input, P2PKAddress, UnsignedErgoLikeTransaction, UnsignedInput}
import org.ergoplatform.ErgoBox.{NonMandatoryRegisterId, R4, R7}
import org.ergoplatform.sdk.wallet.Constants.eip3DerivationPath
import org.ergoplatform.sdk.wallet.secrets.ExtendedSecretKey
import org.ergoplatform.sdk.wallet.settings.EncryptionSettings
import org.ergoplatform.wallet.boxes.BoxSelector.BoxSelectionResult
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import org.ergoplatform.wallet.crypto.ErgoSignature
import org.ergoplatform.wallet.interface4j.SecretString
import org.ergoplatform.wallet.secrets.JsonSecretStorage
import org.ergoplatform.wallet.settings.SecretStorageSettings
import scalaj.http.{Http, HttpOptions}
import scorex.util.encode.Base16
import scorex.util.ModifierId
import sigmastate.SType
import sigmastate.Values.{ErgoTree, EvaluatedValue}
import sigmastate.crypto.DLogProtocol
import sigmastate.eval._
import sigmastate.eval.Extensions._
import sigmastate.interpreter.{ContextExtension, ProverResult}
import sigmastate.serialization.{ErgoTreeSerializer, ValueSerializer}
import sigma.{Coll, Colls}

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

/**
 * Minimal JSON decoder for ErgoBox from node box JSON (replaces the ergo-core ApiCodecs).
 * Register values are deserialized with sigmastate's ValueSerializer.
 */
object ErgoBoxCodecs {
  private def decodeTokens(assets: Seq[Json]): Coll[(ErgoBox.TokenId, Long)] = {
    Colls.fromArray(assets.map { a =>
      val id = a.hcursor.downField("tokenId").as[String].getOrElse(throw new Exception(s"no tokenId in $a"))
      val amt = a.hcursor.downField("amount").as[Long].getOrElse(throw new Exception(s"no amount in $a"))
      (Colls.fromArray(Base16.decode(id).get).asInstanceOf[ErgoBox.TokenId], amt)
    }.toArray)
  }

  private def decodeRegisters(regs: Json): Map[NonMandatoryRegisterId, EvaluatedValue[_ <: SType]] = {
    val fields = regs.asObject.map(_.toMap).getOrElse(Map.empty)
    fields.map { case (name, valueJson) =>
      val hex = valueJson.asString.getOrElse(
        valueJson.hcursor.downField("serializedValue").as[String]
          .getOrElse(throw new Exception(s"cannot parse register $name in $valueJson")))
      val regId = ErgoBox.registerByName.get(name)
        .getOrElse(throw new Exception(s"unknown register $name")).asInstanceOf[NonMandatoryRegisterId]
      val v = ValueSerializer.deserialize(Base16.decode(hex).get).asInstanceOf[EvaluatedValue[_ <: SType]]
      (regId, v)
    }
  }

  implicit val decodeErgoBox: Decoder[ErgoBox] = Decoder.instance { c =>
    for {
      value <- c.downField("value").as[Long]
      treeHex <- c.downField("ergoTree").as[String]
      creationHeight <- c.downField("creationHeight").as[Int]
      txId <- c.downField("transactionId").as[String]
      index <- c.downField("index").as[Int]
      assets <- c.downField("assets").as[Seq[Json]]
      registers <- c.downField("additionalRegisters").as[Json]
    } yield new ErgoBox(
      value,
      ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(Base16.decode(treeHex).get),
      decodeTokens(assets),
      decodeRegisters(registers),
      txId.asInstanceOf[ModifierId],
      index.toShort,
      creationHeight
    )
  }
}

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

// identifies protocol boxes by the NFT they hold, via the node's /blockchain extra indices
case class DexyNftIds(tracking95NFT: String,
                      tracking98NFT: String,
                      tracking101NFT: String,
                      oraclePoolNFT: String,
                      lpNFT: String,
                      lpSwapNFT: String)

case class OffchainUtils(serverUrl: String,
                    apiKey: String,
                    localSecretStoragePath: String,
                    localSecretUnlockPass: String,
                    dexyNftIds: DexyNftIds) {
  val defaultFee = 1000000L
  val eae = new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix)
  //todo: get change address via api from server
  val changeAddress = eae.fromString("9gZLYYtsC6EUhj4SK2XySR9duVorTcQxHK8oE4ZTdUEpReTXcAK").get

  def feeOut(creationHeight: Int, providedFeeOpt: Option[Long] = None): ErgoBoxCandidate = {
    new ErgoBoxCandidate(providedFeeOpt.getOrElse(defaultFee), ErgoTreePredef.feeProposition(720), creationHeight) // 0.001 ERG
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

  // the node expects the transaction hex as a JSON string body
  def postTransaction(txBytes: Array[Byte]): String = {
    postString(s"$serverUrl/transactions/bytes", "\"" + Base16.encode(txBytes) + "\"")
  }

  /**
   * Builds a /wallet/transaction/sign request (TransactionSigningRequest in the node openapi).
   * The node wallet signs whichever inputs it holds keys for; inputs whose scripts need no
   * secrets (e.g. the bank box spent via its update path) get empty proofs. inputsRaw carries
   * the full input boxes, so the request is self-contained (no UTXO/extra-index lookups needed
   * at signing time).
   */
  def signRequestJson(utx: UnsignedErgoLikeTransaction, inputBoxes: IndexedSeq[ErgoBox]): String = {
    val inputs = utx.inputs.map { i =>
      Json.obj("boxId" -> Json.fromString(Base16.encode(i.boxId)), "extension" -> Json.obj())
    }
    val outputs = utx.outputCandidates.map { o =>
      Json.obj(
        "value" -> Json.fromLong(o.value),
        "ergoTree" -> Json.fromString(Base16.encode(o.ergoTree.bytes)),
        "creationHeight" -> Json.fromInt(o.creationHeight),
        "assets" -> Json.arr(o.additionalTokens.toArray.map { case (id, amount) =>
          Json.obj("tokenId" -> Json.fromString(Base16.encode(id.toArray)), "amount" -> Json.fromLong(amount))
        }: _*),
        "additionalRegisters" -> Json.obj()
      )
    }
    val tx = Json.obj(
      "inputs" -> Json.arr(inputs: _*),
      "dataInputs" -> Json.arr(),
      "outputs" -> Json.arr(outputs: _*))
    val inputsRaw = inputBoxes.map(b => Json.fromString(Base16.encode(ErgoBoxSerializer.toBytes(b))))
    Json.obj(
      "tx" -> tx,
      "inputsRaw" -> Json.arr(inputsRaw: _*),
      "secrets" -> Json.obj()
    ).spaces2
  }

  // read a keystore password interactively; never commit passwords to source
  def promptPassword(what: String): String = {
    val console = System.console()
    if (console == null)
      throw new IllegalStateException(
        s"no console available to read the $what password safely; run from a plain terminal (not piped or from an IDE)")
    new String(console.readPassword(s"$what password: "))
  }

  def currentHeight(): Int = {
    val infoUrl = s"$serverUrl/info"
    val json = parse(getJsonAsString(infoUrl)).toOption.get
    json.\\("fullHeight").head.asNumber.get.toInt.get
  }

  // the node's extra index serves boxes even after they are spent (with spentTransactionId set)
  def fetchBoxById(boxId: String): ErgoBox = {
    val json = parse(getJsonAsString(s"$serverUrl/blockchain/box/byId/$boxId")).toOption.get
    require(json.hcursor.downField("spentTransactionId").focus.flatMap(_.asString).isEmpty,
      s"box $boxId is already spent (per node extra index)")
    json.as[ErgoBox](ErgoBoxCodecs.decodeErgoBox).toOption.get
  }

  // GET /blockchain/box/unspent/byTokenId/{tokenId} returns a plain array of IndexedErgoBox
  def unspentBoxesByTokenId(tokenId: String): Seq[ErgoBox] = {
    val url = s"$serverUrl/blockchain/box/unspent/byTokenId/$tokenId?offset=0&limit=50"
    val json = parse(getJsonAsString(url)).toOption.get
    json.asArray.getOrElse(throw new Exception(s"unexpected response for unspent boxes by token $tokenId: $json"))
      .map(_.as[ErgoBox](ErgoBoxCodecs.decodeErgoBox).toOption.get)
  }

  def fetchSingleBoxByTokenId(tokenId: String): ErgoBox =  {
    unspentBoxesByTokenId(tokenId).head
  }

  def fetchWalletInputs(): Seq[ErgoBox] = {
    val boxesUnspentUrl = s"$serverUrl/wallet/boxes/unspent?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(boxesUnspentUrl)).toOption.get

    boxesUnspentJson.\\("box").map(_.as[ErgoBox](ErgoBoxCodecs.decodeErgoBox).toOption.get)
  }

  def tracking95Box(): Option[ErgoBox] = unspentBoxesByTokenId(dexyNftIds.tracking95NFT).headOption

  def tracking98Box(): Option[ErgoBox] = unspentBoxesByTokenId(dexyNftIds.tracking98NFT).headOption

  def tracking101Box(): Option[ErgoBox] = unspentBoxesByTokenId(dexyNftIds.tracking101NFT).headOption

  def oraclePoolBox(): Option[ErgoBox] = unspentBoxesByTokenId(dexyNftIds.oraclePoolNFT).headOption

  def lpBox(): Option[ErgoBox] = unspentBoxesByTokenId(dexyNftIds.lpNFT).headOption

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
/*
  def printlnKey() = {
    val settings = ErgoSettings.read()
    val sss = SecretStorageSettings(localSecretStoragePath, settings.walletSettings.secretStorage.encryption)
    val jss = JsonSecretStorage.readFile(sss).get
    jss.unlock(SecretString.create(localSecretUnlockPass))
    val masterKey = jss.secret.get
    val changeKey = masterKey.derive(eip3DerivationPath)
    println(Base16.encode(changeKey.keyBytes))
  } */

  private def p2pkTreeBytes(w: java.math.BigInteger): Array[Byte] = {
    // canonical P2PK contract tree: header 0x00 0x08 0xcd || compressed group element
    ErgoTree.fromSigmaBoolean(new DLogProtocol.DLogProverInput(w).publicImage).bytes
  }

  /**
   * Signs the P2PK inputs of unsignedTransaction whose script matches a key derived from the local keystore
   * (master + EIP-3 change key). Inputs with non-P2PK scripts (e.g. the bank box spent via its update path)
   * get an empty proof — the contract inputs we use require no signature. Set secretStorageOpt to use a
   * keystore other than the default one.
   */
  def signTransaction(txName: String,
                      unsignedTransaction: UnsignedErgoLikeTransaction,
                      boxesToSpend: IndexedSeq[ErgoBox],
                      dataBoxes: IndexedSeq[ErgoBox],
                      secretStorageOpt: Option[(String, String)] = None,
                      printBytes: Boolean = false): Array[Byte] = {
    val (storagePath, storagePass) = secretStorageOpt.getOrElse((localSecretStoragePath, localSecretUnlockPass))
    val sss = SecretStorageSettings(storagePath, EncryptionSettings("HmacSHA256", 128000, 256))
    val jss = JsonSecretStorage.readFile(sss).get
    jss.unlock(SecretString.create(storagePass))
    val masterKey = jss.secret.get
    val changeKey = masterKey.derive(eip3DerivationPath).asInstanceOf[ExtendedSecretKey]
    val keys = Seq(masterKey, changeKey).map(k => new java.math.BigInteger(1, k.keyBytes))

    val boxesById = boxesToSpend.map(b => Base16.encode(b.id) -> b).toMap
    val message = unsignedTransaction.messageToSign

    val inputs = unsignedTransaction.inputs.map { unsignedInput =>
      val boxId = Base16.encode(unsignedInput.boxId)
      val box = boxesById.getOrElse(boxId,
        throw new Exception(s"$txName input box $boxId missing from boxesToSpend"))
      keys.find(k => java.util.Arrays.equals(box.ergoTree.bytes, p2pkTreeBytes(k))) match {
        case Some(w) =>
          Input(unsignedInput.boxId, new ProverResult(ErgoSignature.sign(message, BigInt(w)), ContextExtension.empty))
        case None =>
          Input(unsignedInput.boxId, ProverResult(Array.emptyByteArray, ContextExtension.empty))
      }
    }

    require(inputs.exists(_.spendingProof.proof.nonEmpty),
      s"$txName no secret in keystore $storagePath matches any input script " +
        s"(inputs: ${boxesToSpend.map(b => Base16.encode(b.id)).mkString(", ")})")

    val signed = new ErgoLikeTransaction(inputs, unsignedTransaction.dataInputs, unsignedTransaction.outputCandidates)
    val txBytes = ErgoLikeTransaction.serializer.toBytes(signed)
    println(s"$txName tx id: ${signed.id}")
    if (printBytes) {
      // the signed bytes authorize the spend by themselves — print them only on request
      println(s"$txName tx bytes: ${Base16.encode(txBytes)}")
    }
    txBytes
  }

  private def fetchTrackingBox(trackerType: TrackerType) = {
    (trackerType match {
      case Tracker95 => tracking95Box()
      case Tracker98 => tracking98Box()
      case Tracker101 => tracking101Box()
    }).head
  }

  /* todo: uncomment and fix
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
  } */

/*
todo: uncomment and fix

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
  } */

}

object OffchainUtils {
  import dexy.chainutils.MainnetUseTokenIds

  val nftIds = DexyNftIds(
    tracking95NFT = MainnetUseTokenIds.tracking95NFT,
    tracking98NFT = MainnetUseTokenIds.tracking98NFT,
    tracking101NFT = MainnetUseTokenIds.tracking101NFT,
    oraclePoolNFT = MainnetUseTokenIds.oraclePoolNFT,
    lpNFT = MainnetUseTokenIds.lpNFT,
    lpSwapNFT = MainnetUseTokenIds.lpSwapNFT)
}

object Test extends App {

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyNftIds = OffchainUtils.nftIds)

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
    //   utils.updateTrackers()
    }
    Thread.sleep(60000) // 1 min
  }
}
