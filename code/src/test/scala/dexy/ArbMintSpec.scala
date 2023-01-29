package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ArbMintSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Arbitrage mint (remove Dexy from and adding Ergs to bank box) should work") {
    val oracleRateXy = 9000L // ToDo: test for ranges of oracleRateXy (very low to very high)

    val feeNum = 5 // implies 0.5 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L // must be a +ve value // ToDo: Test that negative value doesn't work

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    val t_arb = 30

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      // ToDo: add tests for reset
      val resetHeightOut = resetHeightIn

      val remainingDexyIn = 10000000L
      val remainingDexyOut = remainingDexyIn - dexyMinted

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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox),
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
