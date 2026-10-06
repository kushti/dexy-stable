package offchain

import org.ergoplatform.{ErgoBox, P2PKAddress, UnsignedErgoLikeTransaction}
import scorex.crypto.hash.Blake2b256
import scorex.util.encode.Base16

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/**
 * Emergency bank recovery: sweep the USE and DexyGold bank boxes to a trusted address.
 *
 * The bank contract (contracts/bank/bank.es, as deployed) passes unconditionally when
 * INPUTS(0).tokens(0) is the deployment's update NFT (the validUpdate path), so a box carrying
 * the update NFT — a "carrier" — can be spent together with the bank box and the bank's
 * successor is completely unconstrained.
 *
 * This tool only BUILDS the transactions: it validates the input boxes, prints the transaction
 * for review, and writes a /wallet/transaction/sign request JSON (TransactionSigningRequest in
 * the node's openapi.yaml). Signing happens in the operator's own node wallet, which must hold
 * the carrier key and be unlocked; the signed transaction is then submitted with
 * POST /transactions. No secrets are handled by this tool.
 *
 * Compatibility with the contract fixes: the tool spends the deployed, immutable bank boxes and
 * never reads contract sources from contracts/, so template changes cannot invalidate it. The
 * drain shape (carrier at INPUTS(0), bank at INPUTS(1)) also passes the current template's
 * `INPUTS(1).id == SELF.id` binding; it would break only under a future bank script that pins
 * INPUTS(0) to the update.es script hash or removes the validUpdate path.
 *
 * Usage:
 *   prep-gold
 *     carve a carrier box out of the DexyGold treasury (update NFT moved to tokens(0))
 *   drain <bankBoxId> <carrierBoxId> <trustedP2pkAddress> <updateNFT> <bankNFT>
 *
 * Convenience:
 *   drain USE:  BankRecovery drain e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b 1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688 <trusted> f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883 78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae
 *   drain gold: BankRecovery drain fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616 <carrier from prep-gold> <trusted> 7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09 75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f
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

  // mainnet token ids (see spec/deployment-usd.md, spec/deployment-gold.md)
  val useBankNFT = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"
  val useUpdateNFT = "f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883"
  val goldBankNFT = "75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"
  val goldUpdateNFT = "7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09"
  val goldTreasuryBoxId = "3b02f44febdb3b858dff84a4f2580841bcdab1ec768f409771569741bc5cb63c"

  def tokensToString(tokens: Iterable[(ErgoBox.TokenId, Long)]): String =
    tokens.map { case (id, amount) => Base16.encode(id.toArray) + " x" + amount }.mkString(", ")

  // print the transaction for review and write the /wallet/transaction/sign request
  def printAndWrite(txName: String, utx: UnsignedErgoLikeTransaction, inputBoxes: IndexedSeq[ErgoBox]): Unit = {
    println(s"$txName inputs:")
    inputBoxes.foreach { b =>
      println(s"  ${Base16.encode(b.id)} value=${b.value} tokens=[${tokensToString(b.additionalTokens.toArray)}]")
    }
    println(s"$txName outputs:")
    utx.outputCandidates.foreach { o =>
      val addr = utils.eae.fromProposition(o.ergoTree).map(_.toString).getOrElse("?")
      println(s"  $addr value=${o.value} tokens=[${tokensToString(o.additionalTokens.toArray)}]")
    }
    println(s"$txName unsigned tx id: ${Base16.encode(Blake2b256(utx.messageToSign))}")

    val file = s"$txName-sign-request.json"
    Files.write(Paths.get(file), utils.signRequestJson(utx, inputBoxes).getBytes(StandardCharsets.UTF_8))
    println(s"$txName sign request written to $file")
    println("to sign (the node wallet must hold the carrier key and be unlocked):")
    println(s"""  curl -X POST -H "api_key: <apiKey>" -H "Content-Type: application/json" -d @$file ${utils.serverUrl}/wallet/transaction/sign > $txName-signed.json""")
    println("review the signed transaction, then submit:")
    println(s"""  curl -X POST -H "api_key: <apiKey>" -H "Content-Type: application/json" -d @$txName-signed.json ${utils.serverUrl}/transactions""")
  }

  def prepGold(): Unit = {
    val treasury = utils.fetchBoxById(goldTreasuryBoxId)
    // height pinned to the input's creation height: the request is identical on every re-run
    // as long as the treasury box is unspent
    val utx = RecoveryTxBuilder.buildPrepGoldTx(treasury, goldUpdateNFT, treasury.creationHeight)
    printAndWrite("prep-gold", utx, IndexedSeq(treasury))
  }

  def drain(bankBoxId: String, carrierBoxId: String, trustedAddress: String, updateNFT: String, bankNFT: String): Unit = {
    val bankIn = utils.fetchBoxById(bankBoxId)
    val carrierIn = utils.fetchBoxById(carrierBoxId)

    val trusted = utils.eae.fromString(trustedAddress).get match {
      case p2pk: P2PKAddress => p2pk
      case other => throw new IllegalArgumentException(s"destination must be a mainnet P2PK address, got: $other")
    }

    val height = math.max(bankIn.creationHeight, carrierIn.creationHeight)
    val utx = RecoveryTxBuilder.buildDrainTx(bankIn, carrierIn, trusted.script, height, updateNFT, bankNFT)
    printAndWrite("drain", utx, IndexedSeq(carrierIn, bankIn))
  }

  args.toSeq match {
    case Seq("prep-gold") =>
      prepGold()
    case Seq("drain", bankBoxId, carrierBoxId, trustedAddress, updateNFT, bankNFT) =>
      drain(bankBoxId, carrierBoxId, trustedAddress, updateNFT, bankNFT)
    case _ =>
      println(
        """Usage:
          |  BankRecovery prep-gold
          |  BankRecovery drain <bankBoxId> <carrierBoxId> <trustedP2pkAddress> <updateNFT> <bankNFT>
          |
          |Convenience:
          |  drain USE:  BankRecovery drain e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b 1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688 <trusted> f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883 78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae
          |  drain gold: BankRecovery drain fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616 <carrier from prep-gold> <trusted> 7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09 75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f""".stripMargin)
  }
}
