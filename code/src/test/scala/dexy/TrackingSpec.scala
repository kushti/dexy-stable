package dexy

import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import DexySpec._

class TrackingSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val dummyTokenId = "a9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L

  property("Trigger 98% tracker should work") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49 // 49/50 = 98%
    val numIn = 50

    val lpRateXY = reservesX / reservesY
    val x = oracleRateXY * denomIn
    val y = numIn * lpRateXY

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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

  property("Trigger 98% tracker should fail if tracking address changed") {
    // following params will decide if its a valid tracking or not
    val lpInCirc = 10000L
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49 // 49/50 = 98%
    val numIn = 50

    val lpRateXY = reservesX / reservesY
    val x = oracleRateXY * denomIn
    val y = numIn * lpRateXY

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49 // 49/50 = 98%
    val numIn = 50

    val lpRateXY = reservesX / reservesY
    val x = oracleRateXY * denomIn
    val y = numIn * lpRateXY

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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49 // 49/50 = 98%
    val numIn = 50

    val lpRateXY = reservesX / reservesY
    val x = oracleRateXY * denomIn
    val y = numIn * lpRateXY

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49
    val numIn = 50

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = oracleRateXY * denomIn
      val y = numIn * lpRateXY

      val toTrigger = x < y
      assert(toTrigger)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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

  property("Trigger 98% tracker should fail if trigger condition not satisfied") {
    val lpInCirc = 10000L
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 1000000000L
    val reservesY = 1000000L

    val denomIn = 49
    val numIn = 50

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = oracleRateXY * denomIn
      val y = numIn * lpRateXY

      val toTrigger = x < y
      assert(!toTrigger)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 1000000000L
    val reservesY = 1000000L

    val denomIn = 49
    val numIn = 50

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = oracleRateXY * denomIn
      val y = numIn * lpRateXY

      val toReset = x >= y
      assert(toReset)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(denomIn), KioskInt(numIn), KioskBoolean(true), KioskInt(trackingHeightOut)),
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
    val oracleRateXY = 10000L
    val lpBalance = 10000000L
    val reservesX = 10000000000L
    val reservesY = 1000000L

    val denomIn = 49
    val numIn = 50

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
          .tokens(new ErgoToken(oracleNFT, 1))
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
            KioskInt(denomIn).getErgoValue, // numerator for 98%
            KioskInt(numIn).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(
              trackingHeightIn
            ).getErgoValue // currently set to INF (input box state is "notTriggeredEarlier")
          )
          .contract(
            ctx.compileContract(
              ConstantsBuilder.empty(),
              DexySpec.trackingScript
            )
          )
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpRateXY = reservesX / reservesY
      val x = oracleRateXY * denomIn
      val y = numIn * lpRateXY

      val toReset = x >= y
      assert(!toReset)

      val validTrackingOutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(
          KioskInt(denomIn),
          KioskInt(numIn),
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
}
