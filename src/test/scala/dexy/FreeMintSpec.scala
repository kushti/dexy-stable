package dexy

import dexy.DexySpec._
import kiosk.ergo.{DhtData, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class FreeMintSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L
  // ToDo: test for ranges of oracleRateXy (very low to very high)
  // ToDo: test for wide ranges of initial ratio in LP for x/y (very low to very high)
  property("Free mint (remove Dexy from and adding Ergs to bank box) should work") {
    val oracleRateXy = 10000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(bankRate == 10030L)
    assert(buybackRate == 20L)
    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Free mint should fail if Bank Dexy token id changed") {
    val oracleRateXy = 10000L
    val feeNum = 5 // implies 0.5 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
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
          .tokens(new ErgoToken(dummyTokenId, bankReservesYOut))
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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dummyTokenId, bankReservesYOut) // <-- this value has changed
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if Bank box script changed") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
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

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        changeAddress, // <-- this value is different
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if FreeMint box script changed") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
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

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        changeAddress, // <-- this value is different
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong LP NFT") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY)
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Oracle NFT") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
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

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Bank NFT in but right Bank NFT out") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

      val remainingDexyIn = 10000000L
      val remainingDexyOut = remainingDexyIn - dexyMinted

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(bankNFT, 1))
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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(dexyUSD, bankReservesYIn)
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Bank NFT") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
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

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value is different
            new ErgoToken(dexyUSD, bankReservesYIn)
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
          (dexyUSD, bankReservesYOut)
        )
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong FreeMint NFT in but right FreeMint NFT out") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

      val remainingDexyIn = 10000000L
      val remainingDexyOut = remainingDexyIn - dexyMinted

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(freeMintNFT, 1))
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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong FreeMint NFT") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = 35000L // must be a +ve value

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
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
          .tokens(new ErgoToken(oraclePoolNFT, 1))
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

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((dummyTokenId, 1)) // <-- this value is different
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Free mint should fail for negative dexy minted") {
    val oracleRateXy = 10000L
    val feeNum = 10 // implies 1 % fee
    val feeDenom = 1000

    val oracleRateXyWithFee = oracleRateXy * (feeNum + feeDenom) / feeDenom

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    assert(lpReservesX / lpReservesY == 10000)
    assert(oracleRateXyWithFee == 10100L)
    val dexyMinted = -35000L // must be a +ve value // <-- this value is different

    val ergsAdded = oracleRateXyWithFee * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + ergsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

      val remainingDexyIn = 10000000L
      val remainingDexyOut = remainingDexyIn - dexyMinted

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, -dexyMinted)) // add funding for extra dexy
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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(freeMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
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

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(freeMintBox, bankBox, fundingBox),
          Array(oracleBox, lpBox),
          Array(validFreeMintOutBox, validBankOutBox),
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
