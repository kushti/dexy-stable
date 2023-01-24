package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ExtractSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L
  // ToDo: other tests (apart from the template)
  //  cannot use different tracker (eg. 98 %)
  //  cannot work without all data inputs present
  //  cannot take less/more Dexy than extracted (i.e., amount reduced in LP must equal amount increased in extract box)
  //  cannot work when tracker height is more than allowed
  //  cannot work when last extraction height is more than allowed
  //  cannot change LP token amount
  //  cannot work when Bank has enough ergs

  property("Redeem Lp (deposit Lp and withdraw Ergs + Dexy) should work") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_extract = 10 // blocks for which the rate is below 95%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val deltaDexy = 1234 // +ve that means we are extracting.

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy
    val lpBalanceOut = lpBalanceIn

    val bankReservesY = 100
    val bankReservesX = 10000000000L - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oracleNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesX)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking95Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking95NFT, 1))
          .registers(
            KioskInt(19).getErgoValue, // numerator for 95%
            KioskInt(20).getErgoValue, // denominator for 95%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeight).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractionNFT, 1), new ErgoToken(dexyUSD, extractBoxDexyIn))
          .creationHeight(extractBoxCreationHeightIn)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      // all ok, extract should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, bankBox, tracking95Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

}
