package dexy.bank

import dexy.Common
import dexy.DexySpec.{ballotTokenId, bankNFT, bankScript, updateNFT}
import kiosk.ErgoUtil
import kiosk.encoding.ScalaErgoConverters
import kiosk.encoding.ScalaErgoConverters.stringToGroupElement
import kiosk.ergo.{KioskBox, KioskGroupElement}
import oracles.OracleContracts.{ballotAddress, updateAddress}
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class UpdateSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val fakeNanoErgs = 10000000000000L

  property("Update should work") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      object Voters {
        // define voters
        val addresses = Seq(
          "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx", // private key is 37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0
          "9f9q6Hs7vXZSQwhbrptQZLkTx15ApjbEkQwWXJqD2NpaouiigJQ", // private key is 5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d
          "9fGp73EsRQMpFC7xaYD5JFy2abZeKCUffhDBNbQVtBtQyw61Vym", // private key is 3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e
        ).toArray

        val privateKey0 = "37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0"
        val privateKey1 = "5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d"
        val privateKey2 = "3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e"

        val r4voter0 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(0))))
        val r4voter1 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(1))))
        val r4voter2 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(2))))

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 3L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 4L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }


      // dummy custom input box for funding various transactions
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // current update box
      val updateOutBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(updateAddress).script))
        .build()

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, fakeNanoErgs))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)
    }
  }

}
