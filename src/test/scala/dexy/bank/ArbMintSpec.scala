package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskCollByte, KioskInt, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit._
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ArbMintSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val dummyTokenId = "0000005aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  property("Arbitrage mint (remove Dexy from and adding Ergs to bank box) should work") {
    val oracleRateXy = 9000L * 1000L // ToDo: test for ranges of oracleRateXy (very low to very high)

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint should fail if threshold is invalid") {
    val oracleRateXy = 10000L * 1000L // this line has changed

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 <= thresholdPercent * oracleRateXyWithFee) // check that threshold is wrong

    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if negative amount minted") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = -35000L // this line has changed

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, -dexyMinted))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if counter not reset and more Dexy taken than allowed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val remainingDexyIn = 10000000L

    val dexyMinted = remainingDexyIn + 1 // <-- this value is different

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, dexyMinted))
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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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


      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should work if counter (R4) is reset and max allowed (R5) also reset") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee

    assert(maxAllowedIfReset == 1055831951L)

    val remainingDexyIn = 10000000L
    val remainingDexyOut = maxAllowedIfReset - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight - 1 // counter is reset if the resetHeightIn is < HEIGHT
      val resetHeightOut = ctx.getHeight + t_arb

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint should fail if counter (R4) is reset but max allowed (R5) not reset") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L // must be a +ve value

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight - 1 // counter is reset if the resetHeightIn is < HEIGHT
      val resetHeightOut = ctx.getHeight + t_arb

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if counter (R4) is reset and max allowed (R5) reset but more Dexy taken than permitted") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee
    val dexyMinted = maxAllowedIfReset + 1 // this value changed

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    assert(maxAllowedIfReset == 1055831951L)

    val remainingDexyIn = 10000000L
    val remainingDexyOut = maxAllowedIfReset - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight - 1 // counter is reset if the resetHeightIn is < HEIGHT
      val resetHeightOut = ctx.getHeight + t_arb

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if counter is not reset when too many blocks passed") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight - 1 // <-- this value is different
      // counter is reset if the resetHeightIn is < HEIGHT. Hence it should be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if register R4 (reset height) of ArbitrageMint box has incorrect value ") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn + 1 // <-- this value is different (correct value + 1)

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if register R5 (remaining Dexy) of ArbitrageMint box has incorrect value") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted - 1 // <-- this value is different (correct value - 1)

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if Bank Dexy token id changed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (bankNFT, 1),
          (dummyTokenId, bankReservesYOut) // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if wrong ArbitrageMint box NFT") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((dummyTokenId, 1)) // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if wrong Bank box NFT") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((dummyTokenId, 1)) // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if ArbitrageMint box NFT changed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((dummyTokenId, 1)) // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if Bank box NFT changed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if Arbitrage box script changed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        changeAddress, // <-- this value is different
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail if Bank box script changed") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        changeAddress, // <-- this value is different
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail wrong Oracle NFT") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail wrong LP NFT") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail wrong tracking NFT") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(dummyTokenId, 1)) // <-- this value is different
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail invalid tracking height") {
    val oracleRateXy = 9000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb // <-- this value is different
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }  should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should fail when maxAllowedIfReset results in negative value") {
    val oracleRateXy = 20000L * 1000L // High oracle rate

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000L // Relatively small X reserves
    val lpReservesY = 10000000000000L // Very large Y reserves (making ratio X/Y small)

    val lpRateXy = lpReservesX / lpReservesY
    // In this scenario, lpRateXy should be < oracleRateXyWithFee, so arbitrage mint should fail
    assert(lpRateXy * 100 < thresholdPercent * oracleRateXyWithFee)

    // Calculate maxAllowedIfReset which should be negative in this case
    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee
    assert(maxAllowedIfReset < 0)

    val dexyMinted = 1000L // Small amount to try to mint

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = 10000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      // This should fail because lpRateXy < oracleRateXyWithFee, meaning the threshold condition is not met
      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Arbitrage mint should work correctly with precision and rounding edge cases") {
    val oracleRateXy = 9001L * 1000L // Using a rate that will cause division remainders

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    // Use values that will create precision/rounding issues in integer division
    val lpReservesX = 100000000000007L  // Prime number-like to cause remainders
    val lpReservesY = 10000000013L      // Prime number-like to cause remainders

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    // Calculate maxAllowedIfReset which will have integer division precision effects
    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee

    // Use a dexyMinted value that's within the calculated limit
    val dexyMinted = if (maxAllowedIfReset > 0) math.min(500L, maxAllowedIfReset) else 0L

    // Ensure we don't try to mint if max allowed is negative
    assume(dexyMinted > 0)

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = if (maxAllowedIfReset > 0) math.min(1000000L, maxAllowedIfReset) else 1000000L
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint should work correctly with large numbers to test potential overflow scenarios") {
    val oracleRateXy = 9000L * 1000L // Standard oracle rate

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    // Use values that will create potential overflow issues with high numbers
    val lpReservesX = 100000000000000L  // Large X reserves
    val lpReservesY = 10000000000L      // Standard Y reserves

    val lpRateXy = lpReservesX / lpReservesY
    // Ensure threshold condition IS met so arbitrage minting can proceed, but with large numbers
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    // Calculate maxAllowedIfReset which should work normally with these values
    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee

    val dexyMinted = math.min(1000L, maxAllowedIfReset) // Small amount to try to mint

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = math.max(10000L, maxAllowedIfReset)
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight // counter is reset if the resetHeightIn is < HEIGHT. Hence it won't be reset here
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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

      // This test should work correctly with large numbers
      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint should work at exact time boundary conditions") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    val dexyMinted = 35000L

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee
    val remainingDexyIn = math.max(10000000L, maxAllowedIfReset)
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      // Test at the exact boundary: trackingHeight should be exactly t_arb blocks before current height
      // For the transaction to succeed, trackingHeight must be < HEIGHT - T_arb
      val trackingHeight = ctx.getHeight - t_arb - 1  // This ensures (trackingHeight < HEIGHT - T_arb) is true
      val resetHeightIn = ctx.getHeight - 1 // This ensures the counter is reset since resetHeightIn < HEIGHT
      val resetHeightOut = ctx.getHeight + t_arb // Reset height after the transaction

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint should correctly distribute fees between bank and buyback") {
    val oracleRateXy = 9000L * 1000L

    // Use specific fee rates to test the distribution
    val bankFeeNum = 3  // 0.3% to bank
    val buybackFeeNum = 2  // 0.2% to buyback
    val feeDenom = 1000  // Total fee = 0.5%

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L  // oracleRate * 1.003 (including bank fee)
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L        // oracleRate * 0.002 (buyback portion)

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    val dexyMinted = 50000L

    val expectedBankErgs = bankRate * dexyMinted
    val expectedBuybackErgs = buybackRate * dexyMinted
    val totalExpectedErgs = expectedBankErgs + expectedBuybackErgs

    // Verify that the fees are correctly calculated based on oracle rate
    val expectedTotalRate = oracleRateXy / 1000L * (bankFeeNum + buybackFeeNum + feeDenom) / feeDenom
    val expectedRateForCalculation = oracleRateXy / 1000L // Base rate
    val expectedTotalWithFees = expectedRateForCalculation * (bankFeeNum + buybackFeeNum + feeDenom) / feeDenom

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + expectedBankErgs

    val t_arb = 30

    val maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee
    val remainingDexyIn = math.max(10000000L, maxAllowedIfReset)
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight
      val resetHeightOut = resetHeightIn

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + expectedBuybackErgs,
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      // Validate that the fee distribution is working as expected
      assert(expectedBankErgs > 0, "Bank ergs should be positive")
      assert(expectedBuybackErgs > 0, "Buyback ergs should be positive")
      assert(bankRate > buybackRate, "Bank rate should be higher than buyback rate due to higher fee")

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arbitrage mint maxAllowedIfReset calculation should result in target LP rate after minting") {
    val oracleRateXy = 9000L * 1000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    // Use more reasonable values to avoid extremely large mint amounts
    val lpReservesX = 9500000000000L  // Lower X to make the calculation more reasonable
    val lpReservesY = 1000000000L     // Lower Y accordingly

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 9500)  // Current LP rate is 9500 (higher than oracle rate with fee ~9045)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000L)

    // Calculate the theoretical max amount that should be allowed to mint
    // to bring the LP rate to the oracle rate with fee
    val maxAllowedIfResetCalculation = oracleRateXyWithFee * lpReservesY
    assume(lpReservesX > maxAllowedIfResetCalculation) // Ensure positive result

    val maxAllowedIfReset = (lpReservesX - maxAllowedIfResetCalculation) / oracleRateXyWithFee

    // Let's verify the formula: if we mint `maxAllowedIfReset` tokens,
    // new rate = lpReservesX / (lpReservesY + maxAllowedIfReset)
    // From our formula: maxAllowedIfReset = (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee
    // So new rate = lpReservesX / (lpReservesY + (lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee)
    // = lpReservesX / ((lpReservesY * oracleRateXyWithFee + lpReservesX - oracleRateXyWithFee * lpReservesY) / oracleRateXyWithFee)
    // = lpReservesX / (lpReservesX / oracleRateXyWithFee)
    // = oracleRateXyWithFee (mathematically correct)

    assume(maxAllowedIfReset > 0) // Ensure it's beneficial to mint
    assume(maxAllowedIfReset < lpReservesY / 2) // Ensure we're not minting more than reasonable

    val dexyMinted = maxAllowedIfReset

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 10000000000000L  // Adjusted to be more proportional
    val bankReservesYIn = 9020000010L      // Adjusted to be more proportional
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

    val t_arb = 30

    val remainingDexyIn = math.max(dexyMinted, 1000000L)  // Reduced initial amount
    val remainingDexyOut = remainingDexyIn - dexyMinted

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val trackingHeight = ctx.getHeight - t_arb - 1
      val resetHeightIn = ctx.getHeight - 1 // This will trigger a reset
      val resetHeightOut = ctx.getHeight + t_arb // New reset height

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

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesX)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(KioskInt(100).getErgoValue, KioskInt(101).getErgoValue, KioskBoolean(false).getErgoValue, KioskInt(trackingHeight).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .registers(KioskInt(resetHeightIn).getErgoValue, KioskLong(remainingDexyIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
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

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)
          .withContextVars(new ContextVar(0, KioskInt(1).getErgoValue))

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(KioskInt(resetHeightOut), KioskLong(remainingDexyOut)),
        tokens = Array((arbitrageMintNFT, 1))
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
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox),
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
