package dexy.lp

import dexy.Common
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// Test Lp contract for redeem Lp tokens
class LpRedeemSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Redeem Lp (deposit Lp and withdraw Ergs + Dexy) should work") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Set lpBalanceIn to be very close to initialLp, so supplyLpIn is small

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 4995000L  // 100x larger than previous value (49950L)
    val withdrawX = 4895000L   // Significantly larger withdrawal
    val withdrawY = 489L       // Significantly larger withdrawal

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      // all ok, redeem should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should fail if Lp address changed") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 4995000L  // Using scaled up value
    val withdrawX = 4895000L    // Using scaled up value
    val withdrawY = 489L        // Using scaled up value

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Redeem Lp should not work if less LP deposited") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 50L  // Very small amount to make it fail - not enough LP deposited
    val withdrawX = 4895000L  // Still taking substantial amount
    val withdrawY = 489L      // Still taking substantial amount

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should not work if more Ergs taken") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L  // Normal amount of LP tokens deposited
    val withdrawX = 5000000L  // Much more than should be allowed
    val withdrawY = 49L       // Normal amount

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should not work if more Dexy taken") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L  // Normal amount of LP tokens deposited
    val withdrawX = 49000L    // Normal amount
    val withdrawY = 5000L     // Much more than should be allowed

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should not work if more Dexy and 0 Ergs taken") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 4995000L
    val withdrawX = 0L // 0 Ergs, using scaled up value
    val withdrawY = 489L + 1 // one more than needed, using scaled up value

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should not work if 0 Dexy and more Ergs taken") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 4995000L
    val withdrawX = 4895000L + 1 // one more than needed, using scaled up value
    val withdrawY = 0L // 0 Dexy, using scaled up value

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Redeem Lp should not work if oracle rate is below threshold") {
    val oracleRateXy = 10206L  * 1000L // should not work if its <= 98 % of the lp Rate
    val lpBalanceIn = initialLp - 100000000000L // Compensation for new initialLp

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRateXY = reservesXIn / reservesYIn
    assert(lpRateXY <= (oracleRateXy / 1000L) * 98 / 100) // condition opposite to the contract

    val lpRedeemed = 4995000L
    val withdrawX = 4895000L
    val withdrawY = 489L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val oracleBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, redeemBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox),
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
