package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks


// Test Lp contract for redeem Lp tokens
class LpRedeemSpec  extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L
  val fakeScript = "sigmaProp(true)"
  val fakeTxId1 = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"
  val fakeTxId2 = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b808"
  val fakeTxId3 = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b807"
  val fakeTxId4 = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b806"
  val fakeTxId5 = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b105"
  val fakeIndex = 1.toShort
  val changeAddress = "9gQqZyxyjAptMbfW1Gydm3qaap11zd6X9DrABwgEE9eRdRvd27p"

  property("Redeem Lp (deposit Lp and withdraw Ergs + Dexy) should work") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L
    val withdrawY = 50L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      // all ok, redeem should work
      noException shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L
    val withdrawY = 50L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        changeAddress,  // <--------------- this value is changed
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L - 1 // one less than needed
    val withdrawX = 500000L
    val withdrawY = 50L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L + 1 // one more than needed
    val withdrawY = 50L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L
    val withdrawY = 50L + 1 // one more than needed

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 0L // 0 Ergs
    val withdrawY = 50L + 1 // one more than needed

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L + 1 // 1 more than needed
    val withdrawY = 0 // 0 Dexy

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
    val oracleRateXy = 10000L * 98 / 100 // should not work if its <= 98 % of the lp Rate
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 49950L
    val withdrawX = 500000L
    val withdrawY = 50L

    val reservesXOut = reservesXIn - withdrawX
    val reservesYOut = reservesYIn - withdrawY

    val lpBalanceOut = lpBalanceIn + lpRedeemed

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpToken, lpRedeemed))
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
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
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
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, withdrawY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, redeemBox, fundingBox), Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, dummyOutputBox),
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
