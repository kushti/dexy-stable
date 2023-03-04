package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks


// Test Lp contract for mint Lp tokens
class LpMintSpec  extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L


  property("Mint Lp (deposit Ergs and Dexy) should work") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY)

    assert(sharesUnlocked == 49950)

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      // all ok, mint should work
      noException shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Mint Lp should fail if more LP taken") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY) + 1 // one extra LP unlocked

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      // all ok, mint should work
      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Mint Lp should fail if Lp address changed") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY)

    assert(sharesUnlocked == 49950)

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        changeAddress,  // <--------------- this value is changed
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      // all ok, mint should work
      the[Exception] thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Mint Lp should not work if more LP taken") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY) + 1 // unlocking one more share than allowed

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Can deposit less amount of Dexy (Y tokens)") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 1L // 1 dexy instead of 50

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY)

    assert(sharesUnlocked == 999) // much less than 49950

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    assert(lpBalanceOut == 99999001)

    assert(reservesXOut > reservesXIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Taking more LP should fail (when depositing less Dexy)") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 1L // 1 dexy instead of 50

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY) + 1 // one extra LP taken

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    assert(reservesXOut > reservesXIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Can deposit less amount of Ergs (X tokens)") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 10000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY)

    assert(sharesUnlocked == 999)

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    assert(lpBalanceOut == 99999001)

    assert(reservesYOut > reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Taking more LP should fail (when depositing less Ergs)") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 10000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY) + 1 // one extra LP unlocked

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    assert(reservesYOut > reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Can take less LP tokens") {
    val lpBalanceIn = 100000000L

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val depositX = 500000L
    val depositY = 50L

    val reservesXOut = reservesXIn + depositX
    val reservesYOut = reservesYIn + depositY

    val deltaReservesX = depositX
    val deltaReservesY = depositY

    val supplyLpIn = initialLp - lpBalanceIn

    val sharesUnlockedX = BigInt(deltaReservesX) * supplyLpIn / reservesXIn
    val sharesUnlockedY = BigInt(deltaReservesY) * supplyLpIn / reservesYIn
    val sharesUnlocked = sharesUnlockedX.min(sharesUnlockedY) - 1 // one less LP taken

    val lpBalanceOut = lpBalanceIn - sharesUnlocked.toLong

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, depositY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val lpMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, reservesYOut))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((lpToken, sharesUnlocked.toLong))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(Array(lpBox, lpMintBox, fundingBox), Array(),
          Array(validLpOutBox, validLpMintOutBox, dummyOutputBox),
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
