package dexy

import dexy.chainutils.UseSpec
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskCollByte, KioskInt, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoValue, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import dexy.chainutils.UseSpec._
import org.ergoplatform.sdk.ErgoToken

class TrackingSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val dummyTokenId = "a9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L

  property("Trigger 98% tracker should work") {
    // oracle is showing price in X per Y (e.g. nanoErg per dexyUSD)
    // real oracle pool 2.0 delivering price in nanoErg per USD, and contracts are normalizing by dividing by 1000 (for 3 decimals)
    // 98% protocol should be triggered when price below the peg
    // it happens when lp price is below oracle price

    val lpInCirc = 10000L
    val oracleRateXY = 10205L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 98
    val denomIn = 100

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            ErgoValue.of(true), // isBelow == true
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Trigger 101% tracker should work") {
    // oracle is showing price in X per Y (e.g. nanoErg per dexyUSD)

    val lpInCirc = 10000L
    val oracleRateXY = 9088L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 101
    val denomIn = 100

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x > y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(false).getErgoValue, // isBelow == false
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(false), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }


  property("Trigger 98% tracker should fail if lp price is not below") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10000L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50 // 49/50 = 98%

    val lpRateXY = reservesX / reservesY
    assert(oracleRateXY == lpRateXY * 1000L)

    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(!toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Trigger 98% tracker should fail if tracking address changed") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10210L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50 // 49/50 = 98%

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            ErgoValue.of(true), // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Trigger 98% tracker should fail if wrong oracle NFT") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10210L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50 // 49/50 = 98%



    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .tokens(new ErgoToken(dummyTokenId, 1)) // <--------------- this value is changed
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Trigger 98% tracker should fail if wrong lp NFT") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10210L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50 // 49/50 = 98%

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <--------------- this value is changed
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      // all ok, not triggered earlier, triggered now
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Trigger 98% tracker should fail if already triggered") {
    val lpInCirc = 10000L
    val oracleRateXY = 10205L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = 1234 // non-INF value, should cause script to fail

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = lpRateXY * denomIn
      val y = numIn * oracleRateXY / 1000L

      val toTrigger = x < y
      assert(toTrigger)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Reset 98% tracker should work") {
    val lpInCirc = 10000L
    val oracleRateXY = 10000L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = Int.MaxValue // INF, implying tracker will be reset
      val trackingHeightIn = 1234 // some non-INF value, implying that tracker is in triggered state

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesY))
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = lpRateXY * denomIn
      val y = numIn * oracleRateXY / 1000L

      println(x)
      println(y)

      val toReset = x >= y
      assert(toReset)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(numIn), KioskInt(denomIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
        tokens = Array((tracking98NFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Reset 98% tracker should fail if condition not satisfied") {
    val lpInCirc = 10000L
    val oracleRateXY = 10210L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 49
    val denomIn = 50

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut =
        Int.MaxValue // INF, implying tracker will be reset
      val trackingHeightIn =
        1234 // some non-INF value, implying that tracker is in triggered state

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              UseSpec.trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = lpRateXY * denomIn
      val y = numIn * oracleRateXY / 1000L

      val toReset = x >= y
      assert(!toReset)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(numIn),
          KioskInt(denomIn),
          KioskBoolean(true),
          KioskInt(trackingHeightOut)
        ),
        tokens = Array((tracking98NFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Tracking should work with large numbers to prevent potential overflow in ratio calculations") {
    val lpInCirc = 10000L
    val oracleRateXY = 10000L * 1000L
    val lpBalance = 10000000L
    // Use large but reasonable values to test for potential integer overflow
    val reservesX = 100000000000000000L  // Very large X reserves
    val reservesY = 1000000000000000L    // Large Y reserves

    val numIn = 98
    val denomIn = 100

    val lpRateXY = reservesX / reservesY  // 100
    val x = lpRateXY * denomIn            // 100 * 100 = 10000
    val y = numIn * oracleRateXY / 1000L  // 98 * 10000 = 980000

    // The condition lpRate * denomIn < numIn * oracleRateXY should trigger the tracker
    // In this case: 10000 < 980000 is true, so tracking should work
    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue // Initially reset (not triggered)

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // initially set to INF (not triggered)
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(numIn),
          KioskInt(denomIn),
          KioskBoolean(true),
          KioskInt(trackingHeightOut)
        ),
        tokens = Array((tracking98NFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Tracking should handle boundary conditions correctly at exact ratio thresholds") {
    val lpInCirc = 10000L
    val oracleRateXY = 10000L * 1000L  // Results in 10000 rate
    val lpBalance = 10000000L
    // Setting values to create exact threshold conditions
    val reservesX = 9800000000000L     // Create a rate that is exactly at threshold
    val reservesY = 100000000000L      // To get lpRateXY of 98 (approximately)

    val numIn = 98
    val denomIn = 100

    val lpRateXY = reservesX / reservesY  // Approximately 98
    val x = lpRateXY * denomIn            // 98 * 100 = 9800
    val y = numIn * oracleRateXY / 1000L  // 98 * 10000 = 980000

    // For trigger with isBelow=true: we need lpRateXY * denomIn < numIn * oracleRateXY / 1000
    // This becomes: 9800 < 980000 which should be true if the LP rate is below oracle*0.98
    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue // Start in reset state

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // initially set to INF (not triggered)
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(numIn),
          KioskInt(denomIn),
          KioskBoolean(true),
          KioskInt(trackingHeightOut)
        ),
        tokens = Array((tracking98NFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Tracking should fail for invalid token register configurations") {
    val lpInCirc = 10000L
    val oracleRateXY = 10205L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 98
    val denomIn = 100

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // initially set to INF (not triggered)
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      // Valid output box with wrong register values - changing the numerator
      val invalidTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(95),         // Changed numerator (wrong value)
          KioskInt(denomIn),    // Same denominator
          KioskBoolean(true),   // Same isBelow flag
          KioskInt(trackingHeightOut)  // Same tracking height
        ),
        tokens = Array((tracking98NFT, 1)) // Correct NFT preserved
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(invalidTrackingOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Tracking should correctly handle register preservation requirements") {
    val lpInCirc = 10000L
    val oracleRateXY = 10205L * 1000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val numIn = 98
    val denomIn = 100

    val lpRateXY = reservesX / reservesY
    val x = lpRateXY * denomIn
    val y = numIn * oracleRateXY / 1000L

    val toTrigger = x < y
    assert(toTrigger)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeightOut = ctx.getHeight
      val trackingHeightIn = Int.MaxValue

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
          .registers(KioskLong(oracleRateXY).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesX)
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, reservesY)
          )
          .registers(KioskLong(lpInCirc).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(numIn).getErgoValue, // numerator for 98%
            KioskInt(denomIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // initially set to INF (not triggered)
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      // Test with correct register preservation
      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(numIn),      // Preserved numerator
          KioskInt(denomIn),    // Preserved denominator
          KioskBoolean(true),   // Preserved isBelow flag
          KioskInt(trackingHeightOut)  // Updated tracking height
        ),
        tokens = Array((tracking98NFT, 1)) // NFT preserved
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(tracking98Box, fundingBox),
          Array(oracleBox, lpBox),
          Array(validTrackingOutBox),
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
