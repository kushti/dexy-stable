package offchain

import org.ergoplatform.sdk.wallet.secrets.ExtendedSecretKey
import org.ergoplatform.sdk.wallet.settings.EncryptionSettings
import org.ergoplatform.wallet.crypto.ErgoSignature
import org.ergoplatform.wallet.interface4j.SecretString
import org.ergoplatform.wallet.secrets.JsonSecretStorage
import org.ergoplatform.wallet.settings.SecretStorageSettings
import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, ErgoLikeTransaction, ErgoTreePredef, UnsignedErgoLikeTransaction, UnsignedInput}
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.hash.Blake2b256
import scorex.util.ModifierId
import scorex.util.encode.Base16
import sigma.Colls
import sigmastate.{SType, Values}
import sigmastate.Values.{ErgoTree, EvaluatedValue}
import sigmastate.crypto.CryptoConstants
import sigmastate.crypto.DLogProtocol.DLogProverInput

class OffchainUtilsSpec extends PropSpec with Matchers {

  private def emptyTokens = Colls.fromArray(Array.empty[(ErgoBox.TokenId, Long)])

  private def testKeystore(name: String, pass: String): (String, ExtendedSecretKey) = {
    val seed = Blake2b256.hash(name.getBytes("UTF-8"))
    val dir = java.nio.file.Files.createTempDirectory("recovery-keystore-test").toFile.getAbsolutePath
    val settings = SecretStorageSettings(dir, EncryptionSettings("HmacSHA256", 128000, 256))
    // init() zeroes the seed array after writing the file, so derive the master key first
    val masterKey = ExtendedSecretKey.deriveMasterKey(seed, usePre1627KeyDerivation = true)
    JsonSecretStorage.init(seed, SecretString.create(pass), usePre1627KeyDerivation = true)(settings)
    (dir, masterKey)
  }

  private def testBox(tree: ErgoTree, idSeed: String): ErgoBox = {
    val txId = ModifierId @@ Base16.encode(Blake2b256.hash(idSeed.getBytes("UTF-8")))
    new ErgoBox(1000000000L, tree, emptyTokens,
      Map.empty[ErgoBox.NonMandatoryRegisterId, EvaluatedValue[_ <: SType]], txId, 0: Short, 1000000)
  }

  property("signTransaction signs P2PK inputs and leaves contract inputs with empty proof") {
    val pass = "test-pass"
    val (dir, masterKey) = testKeystore("recovery signing test", pass)
    val secret = new java.math.BigInteger(1, masterKey.keyBytes)

    val p2pkTree = ErgoTree.fromSigmaBoolean(new DLogProverInput(secret).publicImage)
    val contractTree = ErgoTreePredef.feeProposition(720)

    val p2pkBox = testBox(p2pkTree, "p2pk box")
    val contractBox = testBox(contractTree, "contract box")
    val out = new ErgoBoxCandidate(1900000000L, contractTree, 1000000)

    val utx = UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(p2pkBox.id), new UnsignedInput(contractBox.id)),
      IndexedSeq.empty,
      IndexedSeq(out)
    )

    val utils = new OffchainUtils("http://127.0.0.1:9053", "", dir, pass, OffchainUtils.nftIds)
    val txBytes = utils.signTransaction("test: ", utx, IndexedSeq(p2pkBox, contractBox), IndexedSeq.empty)
    val signed = ErgoLikeTransaction.serializer.fromBytes(txBytes)

    signed.inputs(0).spendingProof.proof.length shouldBe 56
    signed.inputs(1).spendingProof.proof.length shouldBe 0

    val pk = CryptoConstants.dlogGroup.exponentiate(CryptoConstants.dlogGroup.generator, secret)
    ErgoSignature.verify(signed.messageToSign, signed.inputs(0).spendingProof.proof, pk) shouldBe true
  }

  property("signTransaction fails when no input matches the keystore") {
    val pass = "test-pass"
    val (dir, _) = testKeystore("recovery signing test 2", pass)

    val contractTree = ErgoTreePredef.feeProposition(720)
    val contractBox = testBox(contractTree, "contract-only box")
    val out = new ErgoBoxCandidate(900000000L, contractTree, 1000000)

    val utx = UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(contractBox.id)),
      IndexedSeq.empty,
      IndexedSeq(out)
    )

    val utils = new OffchainUtils("http://127.0.0.1:9053", "", dir, pass, OffchainUtils.nftIds)
    an[Exception] shouldBe thrownBy {
      utils.signTransaction("test: ", utx, IndexedSeq(contractBox), IndexedSeq.empty)
    }
  }
}
