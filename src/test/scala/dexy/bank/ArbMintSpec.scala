package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskInt, KioskLong}
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
    val oracleRateXy = 9000L * 1000000L // ToDo: test for ranges of oracleRateXy (very low to very high)

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L
    // initial ratio of X/Y = 10000  // ToDo: test for wide ranges of initial ratio (very low to very high)

    val lpRateXy = lpReservesX / lpReservesY
    assert(lpRateXy == 10000)
    assert(lpRateXy * 100 > thresholdPercent * oracleRateXyWithFee / 1000000L)

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
    val oracleRateXy = 10000L * 1000000L // this line has changed

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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

      println(lpBox.getTokens)

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
    val oracleRateXy = 9000L * 1000000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
    val oracleRateXy = 9000L * 1000000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
    val oracleRateXy = 9000L * 1000000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
    val oracleRateXy = 9000L * 1000000L

    // implies 0.5 % fee in total
    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dummyTokenId, bankReservesYOut) // <-- this value is different
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (dummyTokenId, 1), // <-- this value is different
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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
        registers = Array(),
        tokens = Array(
          (bankNFT, 1),
          (dexyUSD, bankReservesYOut)
        )
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + buybackErgsAdded,
        registers = Array(),
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

  property("Arb mint with very low oracleRateXy should work") {
    val oracleRateXy = 1L * 1000000L // Very low oracle rate

    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    val dexyMinted = 1000L // Small amount for low rate

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .registers(KioskLong(lpBalance).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .registers(KioskLong(bankReservesXIn).getErgoValue, KioskLong(bankReservesYIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintNFT))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId6, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId7, fakeIndex)

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskLong(bankReservesXOut), KioskLong(bankReservesYOut)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesX,
        registers = Array(KioskLong(lpBalance)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, lpReservesY))
      )

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((buybackNFT, 1))
      )

      val validTracking101OutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(101), KioskInt(100), KioskBoolean(false), KioskInt(ctx.getHeight)),
        tokens = Array((tracking101NFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox, validTracking101OutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Arb mint with very high oracleRateXy should work") {
    val oracleRateXy = 1000000L * 1000000L // Very high oracle rate

    val bankFeeNum = 3
    val buybackFeeNum = 2
    val feeDenom = 1000

    val bankRate = oracleRateXy * (bankFeeNum + feeDenom) / feeDenom / 1000000L
    val buybackRate = oracleRateXy * buybackFeeNum / feeDenom / 1000000L

    val oracleRateXyWithFee = bankRate + buybackRate

    val thresholdPercent = 101
    val lpBalance = 100000000L
    val lpReservesX = 100000000000000L
    val lpReservesY = 10000000000L

    val lpRateXy = lpReservesX / lpReservesY
    val dexyMinted = 100L // Small amount for high rate

    val bankErgsAdded = bankRate * dexyMinted
    val buybackErgsAdded = buybackRate * dexyMinted

    val bankReservesXIn = 100000000000000L
    val bankReservesYIn = 90200000100L
    val bankReservesYOut = bankReservesYIn - dexyMinted
    val bankReservesXOut = bankReservesXIn + bankErgsAdded

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
          .value(lpReservesX)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, lpReservesY))
          .registers(KioskLong(lpBalance).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .registers(KioskLong(bankReservesXIn).getErgoValue, KioskLong(bankReservesYIn).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val arbMintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(arbitrageMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), arbitrageMintScript))
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val buybackBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(buybackNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), buybackScript))
          .build()
          .convertToInputWith(fakeTxId6, fakeIndex)

      val tracking101Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking101NFT, 1))
          .registers(
            KioskInt(101).getErgoValue,
            KioskInt(100).getErgoValue,
            KioskBoolean(false).getErgoValue,
            KioskInt(Int.MaxValue).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), UseSpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId7, fakeIndex)

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(KioskLong(bankReservesXOut), KioskLong(bankReservesYOut)),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesX,
        registers = Array(KioskLong(lpBalance)),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, lpReservesY))
      )

      val validArbMintOutBox = KioskBox(
        arbitrageMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((arbitrageMintNFT, 1))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((buybackNFT, 1))
      )

      val validTracking101OutBox = KioskBox(
        trackingAddress,
        minStorageRent,
        registers = Array(KioskInt(101), KioskInt(100), KioskBoolean(false), KioskInt(ctx.getHeight)),
        tokens = Array((tracking101NFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(arbMintBox, bankBox, buybackBox, fundingBox),
          Array(oracleBox, lpBox, tracking101Box),
          Array(validArbMintOutBox, validBankOutBox, validBuybackOutBox, validTracking101OutBox),
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
