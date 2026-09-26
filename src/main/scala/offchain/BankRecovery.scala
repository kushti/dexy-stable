package offchain

import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, P2PKAddress, UnsignedErgoLikeTransaction, UnsignedInput}
import org.ergoplatform.wallet.boxes.DefaultBoxSelector
import scorex.crypto.hash.Blake2b256
import scorex.util.encode.Base16
import sigmastate.eval._
import sigmastate.eval.Extensions._
import sigma.Colls

import scala.util.Try

/**
 * Emergency bank recovery: sweep the USE and DexyGold bank boxes to a trusted address.
 *
 * The bank contract (contracts/bank/bank.es) passes unconditionally when INPUTS(0).tokens(0) is the
 * deployment's update NFT (validUpdate path), so a box carrying the update NFT — a "carrier" — can be
 * spent together with the bank box and the bank's successor is completely unconstrained.
 *
 * Every run is a dry run by default: it prints the inputs, outputs and the unsigned tx id, without
 * signing or broadcasting. Review them, then re-run with `--broadcast --txid <unsigned tx id>` to sign
 * and send; the live run refuses to proceed if the rebuilt unsigned tx id differs from the reviewed one.
 *
 * Usage (fill the config vals below first; do not commit secrets — passwords are prompted):
 *   prep-gold [--broadcast] [--txid <id>]
 *     carve a carrier box out of the DexyGold treasury (update NFT at tokens(0))
 *   drain <bankBoxId> <carrierBoxId> <trustedP2pkAddress> <updateNFT> <bankNFT> [--alt-keystore] [--broadcast] [--txid <id>]
 *     --alt-keystore: sign the carrier with the second keystore (needed for the gold carrier)
 *
 * USE:   bank   e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b
 *        carrier 1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688
 * Gold:  bank   fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616
 *        (carrier produced by prep-gold)
 */
object BankRecovery extends App {

  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "",
    localSecretUnlockPass = "",
    dexyNftIds = OffchainUtils.nftIds)

  // second keystore, used for prep-gold (the DexyGold treasury P2PK key may live in a different keystore)
  val localSecretStoragePath2 = ""
  val localSecretUnlockPass2 = ""

  // mainnet token ids (see spec/deployment-usd.md, spec/deployment-gold.md)
  val useBankNFT = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"
  val useUpdateNFT = "f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883"
  val goldBankNFT = "75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"
  val goldUpdateNFT = "7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09"
  val goldTreasuryBoxId = "3b02f44febdb3b858dff84a4f2580841bcdab1ec768f409771569741bc5cb63c"
  val useBankBoxId = "e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b"
  val useCarrierBoxId = "1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688"
  val goldBankBoxId = "fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616"

  val broadcast = args.contains("--broadcast")
  val altKeystore = args.contains("--alt-keystore")

  val (positionalArgs, reviewedTxIdOpt) = {
    val txidIdx = args.indexOf("--txid")
    val (txid, rest) =
      if (txidIdx >= 0) (Some(args(txidIdx + 1)), args.patch(txidIdx, Nil, 2))
      else (None, args)
    (rest.filterNot(a => a == "--broadcast" || a == "--alt-keystore" || a == "--dry"), txid)
  }

  def tokenId(hexString: String): ErgoBox.TokenId = {
    Colls.fromArray(Base16.decode(hexString).get).asInstanceOf[ErgoBox.TokenId]
  }

  def requireBoxHasToken(box: ErgoBox, tokenHex: String, what: String): Unit = {
    val tid = tokenId(tokenHex)
    require(box.additionalTokens.toArray.exists { case (id, _) => id == tid },
      s"$what box ${box.id} does not contain token $tokenHex (its tokens: ${box.additionalTokens.toArray.map(_._1).mkString(", ")})")
  }

  def tokensToString(tokens: Iterable[(ErgoBox.TokenId, Long)]): String =
    tokens.map { case (id, amount) => Base16.encode(id.toArray) + " x" + amount }.mkString(", ")

  // print the transaction for review; on a live run require the --txid of the reviewed dry run
  def printAndGate(txName: String, utx: UnsignedErgoLikeTransaction, inputBoxes: IndexedSeq[ErgoBox]): Unit = {
    println(s"$txName inputs:")
    inputBoxes.foreach { b =>
      println(s"  ${Base16.encode(b.id)} value=${b.value} tokens=[${tokensToString(b.additionalTokens.toArray)}]")
    }
    println(s"$txName outputs:")
    utx.outputCandidates.foreach { o =>
      val addr = utils.eae.fromProposition(o.ergoTree).map(_.toString).getOrElse("?")
      println(s"  $addr value=${o.value} tokens=[${tokensToString(o.additionalTokens.toArray)}]")
    }
    val unsignedTxId = Base16.encode(Blake2b256(utx.messageToSign))
    println(s"$txName unsigned tx id: $unsignedTxId")
    if (broadcast) {
      require(reviewedTxIdOpt.contains(unsignedTxId),
        s"$txName live run requires the --txid of the reviewed dry run (pass --txid $unsignedTxId)")
    }
  }

  // password comes from the source constant if set, otherwise from a prompt
  def keystoreOpt(useAlt: Boolean): Option[(String, String)] = {
    if (useAlt) {
      val pass = if (localSecretUnlockPass2.nonEmpty) localSecretUnlockPass2 else utils.promptPassword("second keystore")
      Some((localSecretStoragePath2, pass))
    } else if (utils.localSecretUnlockPass.nonEmpty) {
      None // default credentials configured in OffchainUtils above
    } else {
      Some((utils.localSecretStoragePath, utils.promptPassword("keystore")))
    }
  }

  def prepGold(): Unit = {
    val treasury = utils.fetchBoxById(goldTreasuryBoxId)
    requireBoxHasToken(treasury, goldUpdateNFT, "treasury")

    val creationHeight = utils.currentHeight()
    val feeOut = utils.feeOut(creationHeight, Some(2000000L)) // 0.002 ERG

    val carrierValue = 1000000L // min value for a box holding one token
    val carrierOut = new ErgoBoxCandidate(
      carrierValue,
      treasury.ergoTree, // same P2PK; operator key must be in the keystore
      creationHeight,
      Colls.fromArray(Array((tokenId(goldUpdateNFT), 3L)))
    )

    val remainingTokens = treasury.additionalTokens.toArray.filter { case (id, _) => id != tokenId(goldUpdateNFT) }
    val changeOut = new ErgoBoxCandidate(
      treasury.value - feeOut.value - carrierValue,
      treasury.ergoTree,
      creationHeight,
      Colls.fromArray(remainingTokens)
    )

    val utx = UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(treasury.id)),
      IndexedSeq.empty,
      IndexedSeq(carrierOut, changeOut, feeOut)
    )

    printAndGate("prep-gold", utx, IndexedSeq(treasury))
    if (broadcast) {
      val txBytes = utils.signTransaction("prep-gold: ", utx, IndexedSeq(treasury), IndexedSeq.empty,
        secretStorageOpt = keystoreOpt(useAlt = true))
      val resp = utils.postTransaction(txBytes)
      println(s"prep-gold tx id: $resp")
    } else {
      println("dry run (default): not signing or broadcasting; re-run with --broadcast --txid <unsigned tx id> to send")
    }
  }

  def drain(bankBoxId: String, carrierBoxId: String, trustedAddress: String, updateNFT: String, bankNFT: String): Unit = {
    val bankIn = utils.fetchBoxById(bankBoxId)
    val carrierIn = utils.fetchBoxById(carrierBoxId)
    requireBoxHasToken(bankIn, bankNFT, "bank")
    require(carrierIn.additionalTokens.toArray.headOption.exists { case (id, _) => id == tokenId(updateNFT) },
      s"carrier box ${carrierIn.id} does not have update NFT $updateNFT at tokens(0)")

    val trusted = utils.eae.fromString(trustedAddress).get.asInstanceOf[P2PKAddress]

    val creationHeight = utils.currentHeight()
    val feeOut = utils.feeOut(creationHeight, Some(2000000L)) // 0.002 ERG

    // bank value minus fee goes to the trusted address, with all bank tokens (incl. bankNFT)
    val trustedOut = new ErgoBoxCandidate(
      bankIn.value - feeOut.value,
      trusted.script,
      creationHeight,
      bankIn.additionalTokens
    )

    // carrier value and update NFTs return to the operator (same P2PK)
    val carrierOut = new ErgoBoxCandidate(
      carrierIn.value,
      carrierIn.ergoTree,
      creationHeight,
      carrierIn.additionalTokens
    )

    val utx = UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(carrierIn.id), new UnsignedInput(bankIn.id)),
      IndexedSeq.empty,
      IndexedSeq(trustedOut, carrierOut, feeOut)
    )

    val inputBoxes = IndexedSeq(carrierIn, bankIn)
    printAndGate("drain", utx, inputBoxes)
    if (broadcast) {
      val txBytes = utils.signTransaction("drain: ", utx, inputBoxes, IndexedSeq.empty,
        secretStorageOpt = keystoreOpt(altKeystore))
      val resp = utils.postTransaction(txBytes)
      println(s"drain tx id: $resp")
    } else {
      println("dry run (default): not signing or broadcasting; re-run with --broadcast --txid <unsigned tx id> to send")
    }
  }

  positionalArgs match {
    case Array("prep-gold") =>
      prepGold()
    case Array("drain", bankBoxId, carrierBoxId, trustedAddress, updateNFT, bankNFT) =>
      drain(bankBoxId, carrierBoxId, trustedAddress, updateNFT, bankNFT)
    case _ =>
      println(
        """Usage:
          |  BankRecovery prep-gold [--broadcast] [--txid <unsigned tx id>]
          |  BankRecovery drain <bankBoxId> <carrierBoxId> <trustedP2pkAddress> <updateNFT> <bankNFT> [--alt-keystore] [--broadcast] [--txid <unsigned tx id>]
          |
          |Convenience:
          |  drain USE:  BankRecovery drain e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b 1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688 <trusted> f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883 78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae
          |  drain gold: BankRecovery drain fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616 <carrier from prep-gold> <trusted> 7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09 75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f --alt-keystore""".stripMargin)
  }
}
