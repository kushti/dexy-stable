package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec
import dexy.chainutils.UseSpec.{bankAddress, bankScript, buybackAddress, buybackScript, freeMintAddress, freeMintScript, lpScript, payoutAddress, payoutScript}
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskCollByte, KioskInt, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class PayoutSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  property("Payout should work") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 1000  // 0.1% instead of 0.5%
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(0).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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
          .value(minStorageRent)
          .tokens(new ErgoToken(lpNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + (bankReservesXIn - bankReservesXOut),
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )


      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox, lpBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Payout should be ok if less taken") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 1000  // 0.1% instead of 0.5%
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(0).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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
          .value(minStorageRent)
          .tokens(new ErgoToken(lpNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut + 1,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + (bankReservesXIn - bankReservesXOut - 1),
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )


      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox, lpBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Payout should fail if more ergs taken") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 1000 - 1 // <-- this line changed to use 0.1% instead of 0.5%
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(0).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + (bankReservesXIn - bankReservesXOut),
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )


      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Payout should fail if taken too early") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 1000  // changed to use 0.1% instead of 0.5%
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(ctx.getHeight - 5040 + 1).getErgoValue) // <- this line changed
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + (bankReservesXIn - bankReservesXOut),
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )


      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Payout should work with maximum allowed payment amount") {
    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 100000000000000L  // Larger bank reserves to test the 0.1% limit
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000L
    val maxPayment = bankReservesXIn / 1000  // 0.1% of bank reserves
    val bankReservesXOut = bankReservesXIn - maxPayment  // Exactly 0.1% payment
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(ctx.getHeight - 5050).getErgoValue) // Ensure enough time has passed (> 1 week)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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
          .value(minStorageRent)
          .tokens(new ErgoToken(lpNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + maxPayment, // Payment amount goes to buyback
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      // Verify the collateralization is sufficient (> 1200%)
      val dexyInCirculation = UseSpec.initialDexyTokens - bankReservesYOut
      val collateralized = (oracleRateXy / 1000L) * dexyInCirculation * 12 < bankReservesXOut
      assert(collateralized, "Bank should have more than 1200% collateralization")

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox, lpBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Payout should fail with insufficient collateralization") {
    val fakeNanoErgs = 10000000000000L

    // Set up scenario where bank has insufficient reserves compared to circulating Dexy
    val bankReservesXIn = 10000000000L  // Much smaller reserves to ensure insufficient collateralization
    val bankReservesYIn = UseSpec.initialDexyTokens - 100000000000L  // Large circulation (most tokens are out)
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 1000  // 0.1% withdrawal
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000L

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val payoutBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(payoutNFT, 1))
          .registers(KioskInt(ctx.getHeight - 5050).getErgoValue) // Ensuring enough time has passed
          .contract(ctx.compileContract(ConstantsBuilder.empty(), payoutScript))
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
          .value(minStorageRent)
          .tokens(new ErgoToken(lpNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val validPayoutOutBox = KioskBox(
        payoutAddress,
        minStorageRent,
        registers = Array(KioskInt(ctx.getHeight)),
        tokens = Array((payoutNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validBuybackOutBox = KioskBox(
        buybackAddress,
        fakeNanoErgs + (bankReservesXIn - bankReservesXOut),
        registers = Array(KioskCollByte(buybackBox.getId.getBytes)),
        tokens = Array(
          (buybackNFT, 1)
        )
      )

      // Verify this should fail due to insufficient collateralization
      val dexyInCirculation = UseSpec.initialDexyTokens - bankReservesYOut
      val requiredCollateral = (oracleRateXy / 1000L) * dexyInCirculation * 12
      val actualCollateral = bankReservesXOut
      assert(requiredCollateral >= actualCollateral, "Bank should have less than 1200% collateralization")

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(payoutBox, bankBox, buybackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)), fundingBox),
          Array(oracleBox, lpBox),
          Array(validPayoutOutBox, validBankOutBox, validBuybackOutBox),
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
