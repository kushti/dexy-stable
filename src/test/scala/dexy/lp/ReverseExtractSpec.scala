package dexy.lp

import dexy.chainutils.DexySpec._
import dexy.Common
import dexy.chainutils.DexySpec
import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ReverseExtractSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.TestnetTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Reverse Extract (remove Dexy from extract box and put in Lp box) should work") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val T_delay = 360 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 / 1000000L > lpRateXYOut * 101 && oracleRateXy * 100 / 1000000L < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
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

  property("Reverse Extract should fail if tracking depth is less") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay // <-- this value is different

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
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

  property("Reverse Extract should fail if not enough delay in last extract") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay // <-- this value has changed

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if invalid height set in extract output box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val extractBoxCreationHeightOut = ctx.getHeight - 10 // <-- this value will be used as wrong creation height

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut)),
        creationHeight = Some(extractBoxCreationHeightOut)
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if LP NFT changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value has changed
          (lpToken, lpBalanceOut),
          (dexyUSD, lpReservesYOut)
        )
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if LP token amount changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn - 1 // <-- this value has changed

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if more dexy taken than allowed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -450000000L // -ve that means we are reversing extract. <-- this value has changed (original -350000000L)
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
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

  property("Reverse Extract should fail if LP token id changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpBalanceOut))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array(
          (lpNFT, 1),
          (dummyTokenId, lpBalanceOut), // <-- this value has changed
          (dexyUSD, lpReservesYOut)
        )
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if Dexy token id changed in LP box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpReservesYOut))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalanceOut),
          (dummyTokenId, lpReservesYOut) // <-- this value has changed
        )
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if Dexy token id changed in Extract box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, extractBoxDexyOut))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array(
          (extractionNFT, 1),
          (dummyTokenId, extractBoxDexyOut) // <-- this value has changed
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if extra Dexy tokens in LP box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy + 1 // <-- this value is different (one extra token)

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if less Dexy tokens in LP box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy - 1 // <-- this value is different (one less token)

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(extractBoxDexyOut == 89850000100L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if extra Dexy tokens in Extract box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy + 1 // <-- this value is different (one extra token)

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if less Dexy tokens in Extract box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy - 1 // <-- this value is different (one less token)

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if Extract NFT changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
          (dexyUSD, extractBoxDexyOut)
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if LP script changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        changeAddress, // <-- this value is different
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if Extract script changed") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
        changeAddress, // <-- this value is different
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong LP NFT in and right LP NFT out") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpNFT, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is changed
            new ErgoToken(lpToken, lpBalanceIn),
            new ErgoToken(dexyUSD, lpReservesYIn)
          )
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong LP NFT in and same (wrong) LP NFT out") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value has changed
            new ErgoToken(lpToken, lpBalanceIn),
            new ErgoToken(dexyUSD, lpReservesYIn)
          )
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value has changed
          (lpToken, lpBalanceOut),
          (dexyUSD, lpReservesYOut)
        )
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong Extract NFT in and right Extract NFT out") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(extractionNFT, 1))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(dexyUSD, extractBoxDexyIn)
          )
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong Extract NFT in and same (wrong) Extract NFT out") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(dexyUSD, extractBoxDexyIn)
          )
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
          (dexyUSD, extractBoxDexyOut)
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong Oracle NFT") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_release = 2 // blocks for which the rate is above 101%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L
    // initial ratio of X/Y = 10000
    assert(lpReservesXIn / lpReservesYIn == 10000)

    val deltaDexy = -350000000L // -ve that means we are reversing extract.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 90200000100L
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(oracleRateXy * 100 > lpRateXYOut * 101 && oracleRateXy * 100 < lpRateXYOut * 104)
    assert(lpRateXYOut == 9661)
    assert(extractBoxDexyOut == 89850000100L)
    assert(lpReservesYOut == 10350000000L)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_release - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(
            new ErgoToken(dummyTokenId, 1) // <-- this value is different
          )
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue, // isBelow is false
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking101Box),
          Array(validLpOutBox, validExtractOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reverse Extract should fail if wrong Bank NFT") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_extract = 10 // blocks for which the rate is below 95%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 100000000000L
    // initial ratio of X/Y = 1000
    assert(lpReservesXIn / lpReservesYIn == 1000)

    val deltaDexy = 90200000000L // +ve that means we are extracting.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    assert(lpRateXYOut == 10204)
    assert(oracleRateXy * 100 > lpRateXYOut * 98 && oracleRateXy * 100 < lpRateXYOut * 101)

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(extractBoxDexyOut == 90200000100L)
    assert(lpReservesYOut == 9800000000L)

    val bankReservesY = 100
    val bankReservesX = 10000000000L - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesX)
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(dexyUSD, bankReservesY)
          )
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

      an[Exception] shouldBe thrownBy {
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

  property("Reverse Extract should fail if wrong Tracking NFT") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L
    val T_delay = 20 // delay between any burn/release operation  ("T_burn" in the paper)
    val T_extract = 10 // blocks for which the rate is below 95%

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 100000000000L
    // initial ratio of X/Y = 1000
    assert(lpReservesXIn / lpReservesYIn == 1000)

    val deltaDexy = 90200000000L // +ve that means we are extracting.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    assert(lpRateXYOut == 10204)
    assert(oracleRateXy * 100 > lpRateXYOut * 98 && oracleRateXy * 100 < lpRateXYOut * 101)

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(extractBoxDexyOut == 90200000100L)
    assert(lpReservesYOut == 9800000000L)

    val bankReservesY = 100
    val bankReservesX = 10000000000L - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
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
          .tokens(
            new ErgoToken(dummyTokenId, 1) // <-- this value is different
          )
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

      an[Exception] shouldBe thrownBy {
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
