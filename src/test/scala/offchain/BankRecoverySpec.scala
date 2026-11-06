package offchain

import io.circe.Json
import io.circe.parser.parse
import org.ergoplatform.ErgoBox.NonMandatoryRegisterId
import org.ergoplatform.wallet.boxes.ErgoBoxSerializer
import org.ergoplatform.ErgoBox
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigma.Colls
import sigma.Coll
import sigmastate.SType
import sigmastate.Values.{ErgoTree, EvaluatedValue}
import sigmastate.crypto.DLogProtocol.DLogProverInput

class BankRecoverySpec extends PropSpec with Matchers {

  val bankNFT = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"
  val updateNFT = "f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883"
  val dexyToken = "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669"

  val bankValue = 292615000000000L
  val dexyAmount = 900000000000000000L
  val bankHeight = 1800000
  val carrierHeight = 1900000

  private def p2pkTree(seed: String): ErgoTree =
    ErgoTree.fromSigmaBoolean(
      new DLogProverInput(new java.math.BigInteger(1, Blake2b256.hash(seed.getBytes("UTF-8")))).publicImage)

  private def box(value: Long, tree: ErgoTree, tokens: Seq[(String, Long)], idSeed: String, creationHeight: Int): ErgoBox = {
    val txId = ModifierId @@ Base16.encode(Blake2b256.hash(idSeed.getBytes("UTF-8")))
    val toks = Colls.fromArray(tokens.map { case (id, amt) => (RecoveryTxBuilder.tokenId(id), amt) }.toArray)
    new ErgoBox(value, tree, toks,
      Map.empty[NonMandatoryRegisterId, EvaluatedValue[_ <: SType]], txId, 0: Short, creationHeight)
  }

  private def tokenPairs(tokens: Coll[(ErgoBox.TokenId, Long)]): Array[(String, Long)] =
    tokens.toArray.map { case (id, amt) => Base16.encode(id.toArray) -> amt }

  private lazy val bankBox =
    box(bankValue, p2pkTree("bank script placeholder"), Seq(bankNFT -> 1L, dexyToken -> dexyAmount), "bank box", bankHeight)
  private lazy val carrierBox =
    box(RecoveryTxBuilder.carrierValue, p2pkTree("operator"), Seq(updateNFT -> 1L), "carrier box", carrierHeight)
  private lazy val trustedTree = p2pkTree("trusted")

  private def buildDrain() = RecoveryTxBuilder.buildDrainTx(
    bankBox, carrierBox, trustedTree, math.max(bankHeight, carrierHeight), updateNFT, bankNFT)

  property("drain tx: carrier at INPUTS(0), bank at INPUTS(1), pinned height, conserved tokens") {
    val utx = buildDrain()
    val height = math.max(bankHeight, carrierHeight)

    Base16.encode(utx.inputs(0).boxId) shouldBe Base16.encode(carrierBox.id)
    Base16.encode(utx.inputs(1).boxId) shouldBe Base16.encode(bankBox.id)
    utx.outputCandidates.foreach(_.creationHeight shouldBe height)

    val trustedOut = utx.outputCandidates(0)
    trustedOut.value shouldBe bankValue - RecoveryTxBuilder.feeNanoErgs
    Base16.encode(trustedOut.ergoTree.bytes) shouldBe Base16.encode(trustedTree.bytes)
    trustedOut.additionalTokens.toArray.map { case (id, amt) => Base16.encode(id.toArray) -> amt } shouldBe
      Array(bankNFT -> 1L, dexyToken -> dexyAmount)

    val carrierOut = utx.outputCandidates(1)
    carrierOut.value shouldBe RecoveryTxBuilder.carrierValue
    Base16.encode(carrierOut.ergoTree.bytes) shouldBe Base16.encode(carrierBox.ergoTree.bytes)
    tokenPairs(carrierOut.additionalTokens) shouldBe tokenPairs(carrierBox.additionalTokens)

    utx.outputCandidates(2).value shouldBe RecoveryTxBuilder.feeNanoErgs
  }

  property("drain tx and sign request are deterministic for a fixed input set") {
    val utils = new OffchainUtils("http://127.0.0.1:9053", "", "", "", OffchainUtils.nftIds)
    val inputs = IndexedSeq(carrierBox, bankBox)

    Base16.encode(buildDrain().messageToSign) shouldBe Base16.encode(buildDrain().messageToSign)
    utils.signRequestJson(buildDrain(), inputs) shouldBe utils.signRequestJson(buildDrain(), inputs)
  }

  property("drain requires the update NFT at carrier tokens(0)") {
    val wrongCarrier = box(RecoveryTxBuilder.carrierValue, p2pkTree("operator"),
      Seq(dexyToken -> 1L, updateNFT -> 1L), "wrong carrier box", carrierHeight)
    an[IllegalArgumentException] shouldBe thrownBy {
      RecoveryTxBuilder.buildDrainTx(bankBox, wrongCarrier, trustedTree, carrierHeight, updateNFT, bankNFT)
    }
  }

  property("drain requires the bank NFT in the bank box") {
    val notBank = box(bankValue, p2pkTree("bank script placeholder"),
      Seq(dexyToken -> dexyAmount), "not a bank box", bankHeight)
    an[IllegalArgumentException] shouldBe thrownBy {
      RecoveryTxBuilder.buildDrainTx(notBank, carrierBox, trustedTree, carrierHeight, updateNFT, bankNFT)
    }
  }

  property("prep-gold carves the carrier with all update NFT units at tokens(0), remainder back to treasury") {
    val treasuryHeight = 1700000
    val treasury = box(1000000000L, p2pkTree("treasury"),
      Seq(dexyToken -> 5L, updateNFT -> 3L), "treasury box", treasuryHeight)

    val utx = RecoveryTxBuilder.buildPrepGoldTx(treasury, updateNFT, treasury.creationHeight)

    Base16.encode(utx.inputs(0).boxId) shouldBe Base16.encode(treasury.id)
    utx.outputCandidates.foreach(_.creationHeight shouldBe treasuryHeight)

    val carrierOut = utx.outputCandidates(0)
    carrierOut.value shouldBe RecoveryTxBuilder.carrierValue
    Base16.encode(carrierOut.ergoTree.bytes) shouldBe Base16.encode(treasury.ergoTree.bytes)
    carrierOut.additionalTokens.toArray.map { case (id, amt) => Base16.encode(id.toArray) -> amt } shouldBe
      Array(updateNFT -> 3L)

    val changeOut = utx.outputCandidates(1)
    changeOut.value shouldBe 1000000000L - RecoveryTxBuilder.feeNanoErgs - RecoveryTxBuilder.carrierValue
    Base16.encode(changeOut.ergoTree.bytes) shouldBe Base16.encode(treasury.ergoTree.bytes)
    changeOut.additionalTokens.toArray.map { case (id, amt) => Base16.encode(id.toArray) -> amt } shouldBe
      Array(dexyToken -> 5L)

    utx.outputCandidates(2).value shouldBe RecoveryTxBuilder.feeNanoErgs
  }

  property("prep-gold fails if the treasury box has no update NFT") {
    val treasury = box(1000000000L, p2pkTree("treasury"), Seq(dexyToken -> 5L), "no-nft treasury box", 1700000)
    an[IllegalArgumentException] shouldBe thrownBy {
      RecoveryTxBuilder.buildPrepGoldTx(treasury, updateNFT, treasury.creationHeight)
    }
  }

  property("sign request matches the node /wallet/transaction/sign schema") {
    val utils = new OffchainUtils("http://127.0.0.1:9053", "", "", "", OffchainUtils.nftIds)
    val inputs = IndexedSeq(carrierBox, bankBox)
    val json = parse(utils.signRequestJson(buildDrain(), inputs)).toOption.get
    val cursor = json.hcursor

    cursor.downField("secrets").succeeded shouldBe true

    val inputsRaw = cursor.downField("inputsRaw").as[List[String]].toOption.get
    inputsRaw.length shouldBe 2
    // round-trip through the same ErgoBoxSerializer the node uses to parse inputsRaw
    inputsRaw.zip(inputs).foreach { case (raw, b) =>
      val parsed = ErgoBoxSerializer.parseBytesTry(Base16.decode(raw).get).get
      Base16.encode(parsed.id) shouldBe Base16.encode(b.id)
    }

    val tx = cursor.downField("tx")
    val txInputs = tx.downField("inputs").as[List[Json]].toOption.get
    txInputs.length shouldBe 2
    txInputs(0).hcursor.downField("boxId").as[String].toOption.get shouldBe Base16.encode(carrierBox.id)
    txInputs(1).hcursor.downField("boxId").as[String].toOption.get shouldBe Base16.encode(bankBox.id)
    txInputs(0).hcursor.downField("extension").succeeded shouldBe true
    tx.downField("dataInputs").as[List[Json]].toOption.get shouldBe empty

    val outputs = tx.downField("outputs").as[List[Json]].toOption.get
    outputs.length shouldBe 3
    val trusted = outputs(0).hcursor
    trusted.downField("value").as[Long].toOption.get shouldBe bankValue - RecoveryTxBuilder.feeNanoErgs
    trusted.downField("ergoTree").as[String].toOption.get shouldBe Base16.encode(trustedTree.bytes)
    trusted.downField("creationHeight").as[Int].toOption.get shouldBe math.max(bankHeight, carrierHeight)
    trusted.downField("additionalRegisters").keys.map(_.toList) shouldBe Some(Nil)
    val assets = trusted.downField("assets").as[List[Json]].toOption.get
    assets.map(_.hcursor.downField("tokenId").as[String].toOption.get) shouldBe List(bankNFT, dexyToken)
  }
}
