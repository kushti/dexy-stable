package offchain

import org.ergoplatform.{ErgoBox, ErgoBoxCandidate, ErgoTreePredef, UnsignedErgoLikeTransaction, UnsignedInput}
import scorex.util.encode.Base16
import sigma.Colls
import sigmastate.Values.ErgoTree

/**
 * Pure transaction builders for the emergency bank recovery (see BankRecovery).
 *
 * Kept separate from the BankRecovery App (whose body is only initialized when run) so the
 * builders can be unit-tested without a node.
 */
object RecoveryTxBuilder {

  val feeNanoErgs = 2000000L // 0.002 ERG
  val carrierValue = 1000000L // min value for a box holding one token
  val updateNftUnits = 3L // all three update NFT units move to the carrier

  def tokenId(hexString: String): ErgoBox.TokenId = {
    Colls.fromArray(Base16.decode(hexString).get).asInstanceOf[ErgoBox.TokenId]
  }

  def feeOut(creationHeight: Int): ErgoBoxCandidate =
    new ErgoBoxCandidate(feeNanoErgs, ErgoTreePredef.feeProposition(720), creationHeight)

  /**
   * Carve a carrier box out of the treasury: the update NFT (all units) moved to tokens(0),
   * everything else returns to the treasury script.
   */
  def buildPrepGoldTx(treasury: ErgoBox, updateNFT: String, height: Int): UnsignedErgoLikeTransaction = {
    val treasuryTokens = treasury.additionalTokens.toArray
    require(treasuryTokens.exists { case (id, _) => id == tokenId(updateNFT) },
      s"treasury box ${Base16.encode(treasury.id)} does not contain update NFT $updateNFT " +
        s"(its tokens: ${treasuryTokens.map(t => Base16.encode(t._1.toArray)).mkString(", ")})")

    val carrierOut = new ErgoBoxCandidate(
      carrierValue,
      treasury.ergoTree, // same P2PK; the node wallet must hold the key
      height,
      Colls.fromArray(Array((tokenId(updateNFT), updateNftUnits)))
    )

    val remainingTokens = treasuryTokens.filter { case (id, _) => id != tokenId(updateNFT) }
    val changeOut = new ErgoBoxCandidate(
      treasury.value - feeNanoErgs - carrierValue,
      treasury.ergoTree,
      height,
      Colls.fromArray(remainingTokens)
    )

    UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(treasury.id)),
      IndexedSeq.empty,
      IndexedSeq(carrierOut, changeOut, feeOut(height))
    )
  }

  /**
   * Sweep the bank box to the trusted address. The carrier sits at INPUTS(0) with the update NFT
   * at tokens(0), unlocking the deployed bank contract's validUpdate path (no signature needed on
   * the bank input); the carrier input is signed by the node wallet.
   */
  def buildDrainTx(bankIn: ErgoBox,
                   carrierIn: ErgoBox,
                   trustedScript: ErgoTree,
                   height: Int,
                   updateNFT: String,
                   bankNFT: String): UnsignedErgoLikeTransaction = {
    val bankTokens = bankIn.additionalTokens.toArray
    require(bankTokens.exists { case (id, _) => id == tokenId(bankNFT) },
      s"bank box ${Base16.encode(bankIn.id)} does not contain bank NFT $bankNFT " +
        s"(its tokens: ${bankTokens.map(t => Base16.encode(t._1.toArray)).mkString(", ")})")
    require(carrierIn.additionalTokens.toArray.headOption.exists { case (id, _) => id == tokenId(updateNFT) },
      s"carrier box ${Base16.encode(carrierIn.id)} does not have update NFT $updateNFT at tokens(0)")

    // bank value minus fee goes to the trusted address, with all bank tokens (incl. bankNFT)
    val trustedOut = new ErgoBoxCandidate(
      bankIn.value - feeNanoErgs,
      trustedScript,
      height,
      bankIn.additionalTokens
    )

    // carrier value and update NFTs return to the operator (same P2PK)
    val carrierOut = new ErgoBoxCandidate(
      carrierIn.value,
      carrierIn.ergoTree,
      height,
      carrierIn.additionalTokens
    )

    UnsignedErgoLikeTransaction(
      IndexedSeq(new UnsignedInput(carrierIn.id), new UnsignedInput(bankIn.id)),
      IndexedSeq.empty,
      IndexedSeq(trustedOut, carrierOut, feeOut(height))
    )
  }
}
