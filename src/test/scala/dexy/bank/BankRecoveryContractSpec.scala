package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec.bankScript
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// The recovery tool (offchain.BankRecovery) spends the deployed bank box via the validUpdate path:
// a carrier box holding the update NFT at tokens(0) sits at INPUTS(0) (fakeScript stands in for the
// operator's P2PK carrier; the tool leaves signing to the node wallet), the bank box at INPUTS(1),
// and the bank contents go to an arbitrary address. These properties pin that transaction shape
// against the two bank script variants that matter for the sweep:
//   - the currently DEPLOYED script (no INPUTS(1) binding): the carrier drain works;
//   - the current template (`INPUTS(1).id == SELF.id` binding): the carrier drain still works —
//     the sweep stays valid after an on-chain bank update to the current template. Only a future
//     update that pins INPUTS(0) to the update.es script hash would end the carrier path.
class BankRecoveryContractSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val fee = 2000000L

  // the `val validUpdate = ...` statement of the current template (single line, ends with SELF.id)
  val validUpdateStmt = "(?s)val validUpdate = [^;]*?SELF\\.id".r

  // the script as deployed on-chain today (pre-fix)
  val deployedBankScript = validUpdateStmt.replaceFirstIn(bankScript,
    "val validUpdate = INPUTS(0).tokens(0)._1 == updateNFT")

  require(deployedBankScript != bankScript, "the validUpdate patch did not apply to bank.es")

  private def recoveryDrain(bankSource: String, bankAtIndexTwo: Boolean)(implicit ctx: BlockchainContext) = {
    // carrier: operator-controlled box with the update NFT at tokens(0)
    val carrierBox =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

    val bankBox =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(fakeNanoErgs)
        .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, fakeNanoErgs))
        .contract(ctx.compileContract(ConstantsBuilder.empty(), bankSource))
        .build()
        .convertToInputWith(fakeTxId4, fakeIndex)

    val fundingBox =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(fakeNanoErgs)
        .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
        .build()
        .convertToInputWith(fakeTxId1, fakeIndex)

    // trusted output: all bank tokens and the bank value minus the fee, to an arbitrary address
    val trustedOut = KioskBox(changeAddress, fakeNanoErgs - fee, registers = Array(),
      tokens = Array((bankNFT, 1), (dexyUSD, fakeNanoErgs)))
    val carrierOut = KioskBox(changeAddress, minStorageRent, registers = Array(), tokens = Array((updateNFT, 1)))

    val inputs =
      if (bankAtIndexTwo) Array(carrierBox, fundingBox, bankBox)
      else Array(carrierBox, bankBox, fundingBox)

    TxUtil.createTx(inputs, Array(), Array(trustedOut, carrierOut), fee, changeAddress,
      Array[String](), Array[DhtData](), false)
  }

  property("recovery drain is accepted by the currently deployed bank script") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      noException shouldBe thrownBy {
        recoveryDrain(deployedBankScript, bankAtIndexTwo = false)
      }
    }
  }

  property("deployed bank script accepts the bank box at any input index (the hijack the binding fixes)") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      noException shouldBe thrownBy {
        recoveryDrain(deployedBankScript, bankAtIndexTwo = true)
      }
    }
  }

  property("recovery drain stays valid under the current template's INPUTS(1).id == SELF.id binding") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      noException shouldBe thrownBy {
        recoveryDrain(bankScript, bankAtIndexTwo = false)
      }
    }
  }

  property("the binding rejects the bank box at a different input index") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      the[Exception] thrownBy {
        recoveryDrain(bankScript, bankAtIndexTwo = true)
      } should have message "Script reduced to false"
    }
  }
}
