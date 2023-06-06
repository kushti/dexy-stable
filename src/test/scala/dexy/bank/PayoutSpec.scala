package dexy.bank

import dexy.{Common, DexySpec}
import dexy.DexySpec.{bankAddress, bankNFT, bankScript, buybackAddress, buybackNFT, buybackScript, freeMintAddress, freeMintNFT, freeMintScript, lpNFT, lpScript, oraclePoolNFT, payoutAddress, payoutNFT, payoutScript}
import kiosk.ergo.{DhtData, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class PayoutSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  property("Payout should work") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = DexySpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 200
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000000L

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
        registers = Array(),
        tokens = Array(
          (buybackNFT, 1)
        )
      )


      noException shouldBe thrownBy {
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
      }
    }
  }

  property("Payout should fail if more ergs taken") {

    val fakeNanoErgs = 10000000000000L

    val bankReservesXIn = 1000000000000L
    val bankReservesYIn = DexySpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 200 - 1 // <-- this line changed
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000000L

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
        registers = Array(),
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
    val bankReservesYIn = DexySpec.initialDexyTokens - 100000L
    val bankReservesXOut = bankReservesXIn - bankReservesXIn / 200
    val bankReservesYOut = bankReservesYIn
    val oracleRateXy = 10000L * 1000000L

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
        registers = Array(),
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

}
