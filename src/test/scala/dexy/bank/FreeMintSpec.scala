package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskCollByte, KioskInt, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit._
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class FreeMintSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L
  property("Free mint (remove Dexy from and adding Ergs to bank box) should work") {
    val oracleRateXy = 10000L * 1000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
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
    val oracleRateXy = 10000L * 1000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

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
          .tokens(new ErgoToken(dummyTokenId, bankReservesYOut))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
                        (bankNFT, 1),
                        (dummyTokenId, bankReservesYOut) // <-- this value has changed
                      )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if Bank box script changed") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
        changeAddress, // <-- this value has changed
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if FreeMint box script changed") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
        changeAddress, // <-- this value has changed
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((freeMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong LP NFT") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(
            new ErgoToken(dummyTokenId, 1), // <-- this value has changed
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Oracle NFT") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value has changed
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
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Bank NFT in but right Bank NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(new ErgoToken(bankNFT, 1))
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
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
          .tokens(new ErgoToken(dummyTokenId, 1),  // <-- this value has changed
                  new ErgoToken(dexyUSD, bankReservesYIn))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong Bank NFT") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(new ErgoToken(dummyTokenId, 1))
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
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
          .tokens(new ErgoToken(bankNFT, 1),
                  new ErgoToken(dexyUSD, bankReservesYIn))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (dummyTokenId, 1),  // <-- this value has changed
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong FreeMint NFT in but right FreeMint NFT out") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(new ErgoToken(freeMintNFT, 1))
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
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val freeMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value has changed
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), freeMintScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1),
            new ErgoToken(dexyUSD, bankReservesYIn))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail if wrong FreeMint NFT") {
    val oracleRateXy = 10000L * 1000000L
    val bankFeeNum = 3 // implies 0.5 % fee
    val buybackFeeNum = 2 // implies 0.5 % fee
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
          .tokens(new ErgoToken(dummyTokenId, 1))
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
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
          .tokens(new ErgoToken(bankNFT, 1),
            new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validFreeMintOutBox = KioskBox(
        freeMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((dummyTokenId, 1)) // <-- this value has changed
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should fail for negative dexy minted") {
    val oracleRateXy = 10000L * 1000000L
    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    val oracleRateWithFee = bankRate + buybackRate
    assert(lpReservesX / lpReservesY == 10000)
    assert(bankRate == 10030L)
    assert(buybackRate == 20L)
    assert(oracleRateWithFee == 10050)
    val dexyMinted = -35000L // must be a +ve value // <-- this value has changed

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
          .tokens(new ErgoToken(dexyUSD, -dexyMinted)) // add funding for extra dexy
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
          .tokens(
            new ErgoToken(lpNFT, 1),
            new ErgoToken(lpToken, lpBalance),
            new ErgoToken(dexyUSD, lpReservesY))
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
          .tokens(new ErgoToken(bankNFT, 1),
            new ErgoToken(dexyUSD, bankReservesYIn))
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("Free mint should work with precision and rounding edge cases") {
    val oracleRateXy = 9001L * 1000L // Using a rate that will cause division remainders

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val lpBalance = 100000000L
    // Use values that will create precision/rounding issues in integer division
    val lpReservesX = 100000000000007L  // Prime number-like to cause remainders
    val lpReservesY = 10000000013L      // Prime number-like to cause remainders

    val lpRateXy = lpReservesX / lpReservesY
    // Ensure the threshold condition is met so the transaction succeeds
    assert(lpRateXy * 100 > oracleRateXyWithFee * 98 / 1000L)

    // Calculate maxAllowedIfReset which will have integer division precision effects
    val maxAllowedIfReset = lpReservesY / 100 // 1% of LP reserves

    val dexyMinted = if (maxAllowedIfReset > 0) math.min(500L, maxAllowedIfReset) else 0L

    // Ensure we don't try to mint if max allowed is negative or zero
    assume(dexyMinted > 0)

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val resetHeightIn = ctx.getHeight - 1 // Counter is reset if resetHeightIn < HEIGHT
      val resetHeightOut = ctx.getHeight + 365 // Set new reset height (T_free + T_buffer)

      val remainingDexyIn = math.max(1000000L, maxAllowedIfReset) // Use calculated max allowed
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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
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

  property("Free mint should work at exact time boundary conditions") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    assert(lpReservesX / lpReservesY == 10000)
    assert(bankRate == 9027L)
    assert(buybackRate == 18L)
    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_free = 360

    val maxAllowedIfReset = lpReservesY / 100 // 1% of LP reserves
    val remainingDexyIn = math.max(10000000L, maxAllowedIfReset)
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      // Test at the exact boundary: reset height should be exactly t_free blocks before current height
      // For the transaction to succeed, resetHeightIn should be < HEIGHT (so counter resets)
      val resetHeightIn = ctx.getHeight - 1  // This ensures the counter is reset since HEIGHT-1 < HEIGHT
      val resetHeightOut = ctx.getHeight + t_free // New reset height should be HEIGHT + T_free

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
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
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
}
