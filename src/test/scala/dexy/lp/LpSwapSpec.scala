package dexy.lp

import dexy.Common
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskInt, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// Test Lp contracts for following path
// Swap Erg and Dexy tokens using constant product formula after taking fee into account
class LpSwapSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._



  val dummyTokenId = "59e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Swap (sell Ergs) should work - w. simple input") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * feeNumLp / feeDenomLp).toLong - 1 // 1 token less to prevent rounding issues
    assert(buyY == 996)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(reservesYIn) * deltaReservesX * feeNumLp >= -deltaReservesY * (BigInt(reservesXIn) * feeDenomLp + deltaReservesX * feeNumLp))

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Taking too many dexy should fail") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * feeNumLp / feeDenomLp).toLong * 101 / 100 // <-- taking +1%
    println("by: " + buyY)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    // condition reverse to normal one used in LP contract
    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp < BigInt(deltaReservesX) * feeNumLp * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      // all ok, swap should work
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Lp address changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if LpSwap address changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        changeAddress, // <--------------- this value is changed
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Lp NFT changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1), // <--------------- this value is changed
          (lpToken, lpBalance),
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if LpSwap NFT changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalance),
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1) // <--------------- this value is changed
        )
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Lp token changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, lpBalance))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (dummyTokenId, lpBalance), // <--------------- this value is changed
          (dexyUSD, reservesYOut)
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if Dexy token changed") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong
    assert(buyY == 997)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp == BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dummyTokenId, reservesYOut))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array(
          (lpNFT, 1),
          (lpToken, lpBalance),
          (dummyTokenId, reservesYOut) // <--------------- this value is changed
        )
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap (sell Ergs) should fail if more Dexy taken") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong + 1 // taken 1 more dexy token than allowed
    assert(buyY == 998)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesY) * reservesXIn * feeDenomLp < BigInt(deltaReservesX) * (-feeNumLp) * reservesYIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (sell Dexy) should work - w. simple input") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesXIn.toDouble / reservesYIn
    val sellY = 1000L
    val buyX = (sellY * rate * (feeNumLp) / feeDenomLp).toLong - 100 // -100 to avoid rounding errors

    val reservesXOut = reservesXIn - buyX
    val reservesYOut = reservesYIn + sellY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(reservesXIn) * deltaReservesY * feeNumLp >= -deltaReservesX * (BigInt(reservesYIn) * feeDenomLp + deltaReservesY * feeNumLp))

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, sellY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs - deltaReservesX,
        registers = Array(),
        tokens = Array()
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (sell Dexy) should fail if more Ergs taken") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesXIn.toDouble / reservesYIn
    val sellY = 1000L
    val buyX = (sellY * rate * (feeNumLp) / feeDenomLp).toLong + 1 // take one NanoErg extra
    assert(buyX == 9970001)

    val reservesXOut = reservesXIn - buyX
    val reservesYOut = reservesYIn + sellY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(deltaReservesX) * reservesYIn * feeDenomLp < BigInt(deltaReservesY) * (-feeNumLp) * reservesXIn)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, sellY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs - deltaReservesX,
        registers = Array(),
        tokens = Array()
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, dummyOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("No change should work") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val reservesXOut = reservesXIn
    val reservesYOut = reservesYIn

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Swap (user sells Ergs to buy Dexy) should work - w. proxy input v1") {
    import dexy.chainutils.UseSpec._

    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * (feeNumLp) / feeDenomLp).toLong - 1
    assert(buyY == 996)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(reservesYIn) * deltaReservesX * feeNumLp >= -deltaReservesY * (BigInt(reservesXIn) * feeDenomLp + deltaReservesX * feeNumLp))

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val proxyBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapSellV1Script))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val userOutputBox = KioskBox(
        changeAddress,
        proxyBox.getValue - buyY * rate.toLong,
        registers = Array(),
        tokens = Array((dexyUSD, buyY))
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, proxyBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, userOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }

  }

  property("Swap (user sells Dexy to buy Ergs) should work - w. proxy") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesXIn.toDouble / reservesYIn

    val sellY = 1000L
    val buyX = (sellY * rate * (feeNumLp) / feeDenomLp).toLong - 100 // - 100 to avoid rounding errors
    assert(buyX == 9969900)

    val DexFeePerTokenNum   = 1L
    val DexFeePerTokenDenom = 1000L
    println("rate: " + rate)
    val quoteAmount   = buyX * DexFeePerTokenDenom / (DexFeePerTokenDenom - DexFeePerTokenNum)
    println("qa: " + quoteAmount)

    val reservesXOut = reservesXIn - buyX
    val reservesYOut = reservesYIn + sellY

    val deltaReservesX = reservesXOut - reservesXIn
    val deltaReservesY = reservesYOut - reservesYIn

    assert(BigInt(reservesXIn) * deltaReservesY * feeNumLp >= -deltaReservesX * (BigInt(reservesYIn) * feeDenomLp + deltaReservesY * feeNumLp))

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val proxyBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(dummyNanoErgs)
          .tokens(new ErgoToken(dexyUSD, sellY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapBuyV1Script))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val userOutputBox = KioskBox(
        changeAddress,
        dummyNanoErgs + buyX,
        registers = Array(),
        tokens = Array()
      )

      // all ok, swap should work
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, swapBox, proxyBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, userOutputBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  // Composable-positioning swap transaction builder (sell Ergs, buy Dexy; same amounts as the
  // first positive test). The LP box sits at input and output index lpIndex, the swap action box
  // stays at the fixed action slot INPUTS(1); remaining slots are filled with dummy boxes.
  // lpVar / swapLpVar / swapOutVar are the context extension var values attached to the LP and
  // swap inputs; they may differ from the real positions to exercise failure paths. The genuine
  // swap successor box is always placed at OUTPUTS(1) (so the swap NFT is conserved even when
  // swapOutVar points elsewhere).
  private def shiftedSwapTx(lpIndex: Int,
                            lpVar: Int,
                            swapLpVar: Int,
                            swapOutVar: Int,
                            lpOutAddress: String = lpAddress)(implicit ctx: BlockchainContext) = {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val rate = reservesYIn.toDouble / reservesXIn
    val sellX = 10000000L
    val buyY = (sellX * rate * feeNumLp / feeDenomLp).toLong - 1 // 1 token less to prevent rounding issues
    assert(buyY == 996)

    val reservesXOut = reservesXIn + sellX
    val reservesYOut = reservesYIn - buyY

    val dummyTxIds = Array(fakeTxId1, fakeTxId4, fakeTxId5, fakeTxId6, fakeTxId7)

    def dummyInput(txId: String) =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(fakeNanoErgs)
        .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
        .build()
        .convertToInputWith(txId, fakeIndex)

    val lpBox =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(reservesXIn)
        .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
        .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
        .build()
        .convertToInputWith(fakeTxId2, fakeIndex)
        .withContextVars(new ContextVar(0, KioskInt(lpVar).getErgoValue))

    val swapBox =
      ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(lpSwapNFT, 1))
        .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)
        .withContextVars(
          new ContextVar(0, KioskInt(swapLpVar).getErgoValue),
          new ContextVar(1, KioskInt(swapOutVar).getErgoValue)
        )

    val inputs = Array.tabulate(Math.max(lpIndex, 1) + 1) { i =>
      if (i == 1) swapBox else if (i == lpIndex) lpBox else dummyInput(dummyTxIds(i))
    }

    val validLpOutBox = KioskBox(
      lpOutAddress,
      reservesXOut,
      registers = Array(),
      tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYOut))
    )

    val validSwapOutBox = KioskBox(
      lpSwapAddress,
      minStorageRent,
      registers = Array(),
      tokens = Array((lpSwapNFT, 1))
    )

    val userOutputBox = KioskBox(
      changeAddress,
      dummyNanoErgs,
      registers = Array(),
      tokens = Array((dexyUSD, buyY))
    )

    val dummyOutBox = KioskBox(
      changeAddress,
      dummyNanoErgs,
      registers = Array(),
      tokens = Array()
    )

    val outputs = Array.tabulate(Math.max(Math.max(lpIndex, swapOutVar), 1) + 1) { i =>
      if (i == lpIndex) validLpOutBox else if (i == 1) validSwapOutBox else if (i == 0) userOutputBox else dummyOutBox
    }

    TxUtil.createTx(
      inputs,
      Array(),
      outputs,
      fee = 1000000L,
      changeAddress,
      Array[String](),
      Array[DhtData](),
      false
    )
  }

  property("Swap should work with the LP box at a var-selected position (composable layout)") {
    // LP box at INPUTS(3)/OUTPUTS(3), swap successor at OUTPUTS(1), dummies elsewhere
    ergoClient.execute { implicit ctx: BlockchainContext =>
      noException shouldBe thrownBy {
        shiftedSwapTx(lpIndex = 3, lpVar = 3, swapLpVar = 3, swapOutVar = 1)
      }
    }
  }

  property("Swap should fail when a fake LP box sits at the var-pointed input (shifted LP hack replay)") {
    // same exploit shape as LpHackSpec, but with the fake pool box at the var-selected position
    // (INPUTS(3)/OUTPUTS(3)) and the real LP box further down at INPUTS(4)/OUTPUTS(4)
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    ergoClient.execute { implicit ctx: BlockchainContext =>
      def dummyInput(txId: String) =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(txId, fakeIndex)

      val dummyIn0 = dummyInput(fakeTxId1)
      val dummyIn2 = dummyInput(fakeTxId5)

      // fake LP box: trivially-true script, no lpNFT at tokens(0)
      val fakeLpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId7, fakeIndex)

      // swap box points its LP position var at the fake box
      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)
          .withContextVars(
            new ContextVar(0, KioskInt(3).getErgoValue),
            new ContextVar(1, KioskInt(1).getErgoValue)
          )

      // real LP box at INPUTS(4), honestly announcing its position
      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(4).getErgoValue))

      // the drained reserves go to the attacker
      val attackerOutBox = KioskBox(
        changeAddress,
        reservesXIn - minStorageRent,
        registers = Array(),
        tokens = Array()
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      val dummyOutBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array()
      )

      // fake "LP" successor at the pointed OUTPUTS(3): zero deltas, so the swap math would pass
      val fakeLpOutBox = KioskBox(
        changeAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((dummyTokenId, 1), (lpToken, lpBalance), (dexyUSD, reservesYIn))
      )

      // reserve-stripped real LP successor at OUTPUTS(4): script and token ids preserved
      val strippedLpOutBox = KioskBox(
        lpAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYIn))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(dummyIn0, swapBox, dummyIn2, fakeLpBox, lpBox),
          Array(),
          Array(attackerOutBox, validSwapOutBox, dummyOutBox, fakeLpOutBox, strippedLpOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Swap should fail if the var-pointed LP output has the wrong script") {
    // pointed output carries the LP NFT and tokens but sits at the wrong address:
    // swap.es's checks pass, main.es's preservedScript rejects it
    ergoClient.execute { implicit ctx: BlockchainContext =>
      the[Exception] thrownBy {
        shiftedSwapTx(lpIndex = 3, lpVar = 3, swapLpVar = 3, swapOutVar = 1, lpOutAddress = changeAddress)
      } should have message "Script reduced to false"
    }
  }

  property("Swap should fail if a position var is out of range") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      an[Exception] shouldBe thrownBy {
        shiftedSwapTx(lpIndex = 3, lpVar = 10, swapLpVar = 10, swapOutVar = 1)
      }
    }
  }

  property("Swap should fail if the swap successor var points at a dummy output") {
    // the genuine swap successor sits at OUTPUTS(1), but the var points at a dummy at OUTPUTS(4)
    ergoClient.execute { implicit ctx: BlockchainContext =>
      the[Exception] thrownBy {
        shiftedSwapTx(lpIndex = 3, lpVar = 3, swapLpVar = 3, swapOutVar = 4)
      } should have message "Script reduced to false"
    }
  }

  property("Swap should fail if the swap successor var equals the LP index") {
    // successor and LP output would have to be the same box, which cannot carry both NFTs
    ergoClient.execute { implicit ctx: BlockchainContext =>
      the[Exception] thrownBy {
        shiftedSwapTx(lpIndex = 3, lpVar = 3, swapLpVar = 3, swapOutVar = 3)
      } should have message "Script reduced to false"
    }
  }

}
