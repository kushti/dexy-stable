package dexy.lp

import dexy.chainutils.DexySpec._
import dexy.Common
import dexy.chainutils.DexySpec
import kiosk.ErgoUtil
import kiosk.encoding.ScalaErgoConverters
import kiosk.encoding.ScalaErgoConverters.stringToGroupElement
import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskCollByte, KioskGroupElement, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.crypto.hash.Blake2b256

class ExtractSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.TestnetTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val minBankNanoErgs = 10000 * 1000000000L // 10K ERG

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  val T_delay = 360 // delay between any burn/release operation  ("T_burn" in the paper)
  val T_extract = 720 // blocks for which the rate is below 95%

  property("Extract to future (extract Dexy from Lp and store in extract box) should work") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 9708
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    assert(lpRateXYOut == 9708)
    assert(oracleRateXy * 97 / 1000000L < lpRateXYOut * 100 && oracleRateXy * 98 / 1000000L > lpRateXYOut * 100)

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    assert(extractBoxDexyOut == 250000100L)
    assert(lpReservesYOut == 10300000000L)

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if bank has enough Ergs") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs + 1 // <-- this value has changed
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if tracking depth is less") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.
    // There is a certain value of deltaDexy above/below which it should fail. To test this

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract // <-- this value has changed

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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if not enough delay in last extract") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if invalid height set in extract output box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

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
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut)),
        creationHeight = Some(extractBoxCreationHeightOut) // <-- this value has changed
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if LP NFT changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1)) // add funding for dummy token
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if LP token amount changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn - 1 // <-- this value is different

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
        tokens = Array(
          (lpNFT, 1),
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if more dexy taken than allowed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 350000000L // +ve that means we are extracting. // <-- this value has changed (original 250000000L)

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
        tokens = Array(
          (lpNFT, 1),
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

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if LP token id changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpBalanceOut)) // add funding for dummy token
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
        tokens = Array(
          (lpNFT, 1),
          (dummyTokenId, lpBalanceOut), // <-- this value is different
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if Dexy token id changed in LP box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut
    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpReservesYOut)) // add funding for dummy token
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
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalanceOut),
          (dummyTokenId, lpReservesYOut) // <-- this value is different
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if Dexy token id changed in Extract box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, extractBoxDexyOut)) // add funding for dummy token
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
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalanceOut),
          (dexyUSD, lpReservesYOut)
        )
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array(
          (extractionNFT, 1),
          (dummyTokenId, extractBoxDexyOut) // <-- this value is different
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if extra Dexy tokens in LP box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy + 1 // <-- this value is different (one extra token)

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, 1)) // add funding for extra dexyUSD
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
        tokens = Array(
          (lpNFT, 1),
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if less Dexy tokens in LP box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy - 1 // <-- this value is different (one less token)

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy


    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
        tokens = Array(
          (lpNFT, 1),
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if extra Dexy tokens in Extract box") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy + 1 // <-- this value is different (one extra token)

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, 1)) // add funding for extra dexyUSD
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
        tokens = Array(
          (lpNFT, 1),
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if less Dexy tokens in Extract box") {
    val oracleRateXy = 10000L
    val lpBalanceIn = 100000000L

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
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy - 1 // <-- this value is different (one less token)

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
        tokens = Array(
          (lpNFT, 1),
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if Extract NFT changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1)) // add funding for dummy token
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
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
          (dexyUSD, extractBoxDexyOut)
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if LP script changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if Extract script changed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
        changeAddress, // <-- this value is different
        minStorageRent,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, extractBoxDexyOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong LP NFT in and right LP NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpNFT, 1)) // add funding for lpNFT in output
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong LP NFT in and same (wrong) LP NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
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
          (dummyTokenId, 1), // <-- this value is different
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong Extract NFT in and right Extract NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
    // anything more than above should fail

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - T_extract - 1 // any bigger value should fail

      val extractBoxCreationHeightIn = ctx.getHeight - T_delay - 1 // any bigger value should fail

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(extractionNFT, 1)) // add funding for extract NFT in output
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong Extract NFT in and same (wrong) Extract NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong Oracle NFT") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    // final ratio of X/Y = 10204
    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          .tokens(
            new ErgoToken(dummyTokenId, 1) // <-- this value is different
          )
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, fundingBox),
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong Bank NFT") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Extract to future should fail if wrong Tracking NFT") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn =  10550000000L
    assert(lpReservesXIn / lpReservesYIn == 9478)

    val deltaDexy = 250000000L // +ve that means we are extracting.

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    val lpRateXYOut = lpReservesXOut / lpReservesYOut

    val lpBalanceOut = lpBalanceIn

    val extractBoxDexyIn = 100
    val extractBoxDexyOut = extractBoxDexyIn + deltaDexy

    val bankReservesY = 100
    val bankReservesX = minBankNanoErgs - 1 // if Bank nanoErgs less than this number in bank box, then bank is considered "empty"
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
          Array(oracleBox, tracking95Box, bankBox),
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

  property("Successful update when enough votes collected") {
    val fee = 1500000

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

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 1L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 1L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }

      // value to vote for; hash of new bank box script
      val valueVotedFor = KioskCollByte(Blake2b256.hash(bankErgoTree.bytes)) //todo: real update

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
      val updateBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(extractUpdateAddress).script))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

      val ballot0InputToCreate = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot1InputToCreate = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot2InputToCreate = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      // create ballots
      val ballot0 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot0Box.toInBox(fakeTxId5, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot0InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey0),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot1 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot1Box.toInBox(fakeTxId6, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot1InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey1),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot2 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot2Box.toInBox(fakeTxId7, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot2InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey2),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(extractionNFT, 1), new ErgoToken(dexyUSD, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validUpdateOutBox = KioskBox(
        extractUpdateAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((updateNFT, 1))
      )

      val validExtractOutBox = KioskBox(
        bankAddress, // different address!
        fakeNanoErgs,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, 1))
      )

      val validBallot0Output = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0)
        )
      )

      val validBallot1Output = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0)
        )
      )

      val validBallot2Output = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0)
        )
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(updateBox, extractBox, ballot0, ballot1, ballot2, fundingBox),
          Array(),
          Array(validUpdateOutBox, validExtractOutBox, validBallot0Output, validBallot1Output, validBallot2Output),
          fee,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Failed update when not enough votes collected") {
    val fee = 1500000

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

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 1L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 1L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }

      // value to vote for; hash of new bank box script
      val valueVotedFor = KioskCollByte(Blake2b256.hash(bankErgoTree.bytes)) //todo: real update

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
      val updateBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(extractUpdateAddress).script))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

      val ballot0InputToCreate = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot1InputToCreate = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      // create ballots
      val ballot0 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot0Box.toInBox(fakeTxId5, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot0InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey0),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot1 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot1Box.toInBox(fakeTxId6, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot1InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey1),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(extractionNFT, 1), new ErgoToken(dexyUSD, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validUpdateOutBox = KioskBox(
        extractUpdateAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((updateNFT, 1))
      )

      val validExtractOutBox = KioskBox(
        bankAddress, // different address!
        fakeNanoErgs,
        registers = Array(),
        tokens = Array((extractionNFT, 1), (dexyUSD, 1))
      )

      val validBallot0Output = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0)
        )
      )

      val validBallot1Output = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0)
        )
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(updateBox, extractBox, ballot0, ballot1, fundingBox),
          Array(),
          Array(validUpdateOutBox, validExtractOutBox, validBallot0Output, validBallot1Output),
          fee,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Cannot use different tracker (eg. 98%)") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L

    val deltaDexy = 250000000L

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .registers(KioskLong(lpBalanceIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val trackingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1)) // Wrong tracker - should be 95%
          .registers(
            KioskInt(49).getErgoValue,
            KioskInt(50).getErgoValue,
            KioskBoolean(true).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(KioskLong(lpBalanceIn)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn), (dexyUSD, lpReservesYOut))
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(KioskLong(deltaDexy), KioskInt(ctx.getHeight)),
        tokens = Array((extractNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, trackingBox, fundingBox),
          Array(oracleBox),
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

  property("Cannot work without all data inputs present") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L

    val deltaDexy = 250000000L

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // Missing oracle box - should fail

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .registers(KioskLong(lpBalanceIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val trackingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking95NFT, 1))
          .registers(
            KioskInt(19).getErgoValue,
            KioskInt(20).getErgoValue,
            KioskBoolean(true).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(KioskLong(lpBalanceIn)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn), (dexyUSD, lpReservesYOut))
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(KioskLong(deltaDexy), KioskInt(ctx.getHeight)),
        tokens = Array((extractNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, trackingBox, fundingBox),
          Array(), // No oracle box
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

  property("Cannot take less Dexy than extracted") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L

    val deltaDexy = 250000000L

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .registers(KioskLong(lpBalanceIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val trackingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking95NFT, 1))
          .registers(
            KioskInt(19).getErgoValue,
            KioskInt(20).getErgoValue,
            KioskBoolean(true).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(KioskLong(lpBalanceIn)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn), (dexyUSD, lpReservesYOut))
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(KioskLong(deltaDexy - 1), KioskInt(ctx.getHeight)), // Taking less Dexy
        tokens = Array((extractNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, trackingBox, fundingBox),
          Array(oracleBox),
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

  property("Cannot work when tracker height is more than allowed") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L

    val deltaDexy = 250000000L

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .registers(KioskLong(lpBalanceIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val trackingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking95NFT, 1))
          .registers(
            KioskInt(19).getErgoValue,
            KioskInt(20).getErgoValue,
            KioskBoolean(true).getErgoValue,
            KioskInt(ctx.getHeight - T_extract - 1).getErgoValue // Height too old
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(KioskLong(lpBalanceIn)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn), (dexyUSD, lpReservesYOut))
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(KioskLong(deltaDexy), KioskInt(ctx.getHeight)),
        tokens = Array((extractNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, trackingBox, fundingBox),
          Array(oracleBox),
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

  property("Cannot change LP token amount") {
    val oracleRateXy = 10000L * 1000000L
    val lpBalanceIn = 100000000L

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10550000000L

    val deltaDexy = 250000000L

    val lpReservesXOut = lpReservesXIn
    val lpReservesYOut = lpReservesYIn - deltaDexy

    ergoClient.execute { implicit ctx: BlockchainContext =>
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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .registers(KioskLong(lpBalanceIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val extractBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(extractNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), extractScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val trackingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking95NFT, 1))
          .registers(
            KioskInt(19).getErgoValue,
            KioskInt(20).getErgoValue,
            KioskBoolean(true).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(KioskLong(lpBalanceIn)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn - 1), (dexyUSD, lpReservesYOut)) // Changed LP token amount
      )

      val validExtractOutBox = KioskBox(
        extractAddress,
        minStorageRent,
        registers = Array(KioskLong(deltaDexy), KioskInt(ctx.getHeight)),
        tokens = Array((extractNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, extractBox, trackingBox, fundingBox),
          Array(oracleBox),
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
}
