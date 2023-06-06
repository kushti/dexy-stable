package dexy.bank

import dexy.Common
import kiosk.ergo.{DhtData, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import oracles.OracleHelpers
import org.ergoplatform.appkit._
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class BuybackSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common with OracleHelpers {

  import gort.OrdinaryLp._
  import oracles.OracleContracts._
  import dexy.DexySpec._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  private val gortLpToken = "872B4B6250655368566D597133743677397A24432646294A404D635166546A87"

  def createBuyback(gortAmt: Long, ergAmount: Long = dummyNanoErgs)(implicit ctx: BlockchainContext): InputBox = {
    TxUtil
      .createTx(
        Array(
          ctx // for funding transactions
            .newTxBuilder()
            .outBoxBuilder
            .value(ergAmount + 1000000L)
            .tokens(
              new ErgoToken(buybackNFT, 1),
              new ErgoToken(gort, gortAmt))
            .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
            .build()
            .convertToInputWith(dummyTxId, dummyIndex)),
        Array[InputBox](),
        Array(
          KioskBox(
            buybackAddress,
            value = ergAmount,
            registers = Array(),
            tokens = Array(
              (buybackNFT, 1),
              (gort, gortAmt)
            )
          )),
        fee = 1000000L,
        changeAddress,
        Array[String](),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)
  }

  def createGortLpBox(gortLpErg: Long, gortLpGort: Long)(implicit ctx: BlockchainContext): InputBox = {
    TxUtil
      .createTx(
        Array(
          ctx // for funding transactions
            .newTxBuilder()
            .outBoxBuilder
            .value(gortLpErg + dummyNanoErgs)
            .tokens(
              new ErgoToken(gortLpNFT, 1),
              new ErgoToken(gortLpToken, 1000000000L),
              new ErgoToken(gort, gortLpGort))
            .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
            .build()
            .convertToInputWith(dummyTxId, dummyIndex)),
        Array[InputBox](),
        Array(
          KioskBox(
            ordinaryLpAddress,
            value = gortLpErg,
            registers = Array(KioskInt(995)),
            tokens = Array(
              (gortLpNFT, 1),
              (gortLpToken, 1000000000L),
              (gort, gortLpGort)
            )
          )),
        fee = 1000000L,
        changeAddress,
        Array[String](),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)
  }

  property("giveback scenario") {
    ergoClient.execute { implicit ctx: BlockchainContext =>

      val refreshBox = bootstrapRefreshBox()
      val poolBox = bootstrapPoolBox(ctx.getHeight - config.epochLength - 1, rate = 1)

      val oracleBox1 = bootstrapOracleBox(pubKey1, rewardTokenQty = 10)
      val oracleBox2 = bootstrapOracleBox(pubKey2, rewardTokenQty = 20)
      val oracleBox3 = bootstrapOracleBox(pubKey3, rewardTokenQty = 30)
      val oracleBox4 = bootstrapOracleBox(pubKey4, rewardTokenQty = 40)
      val oracleBox5 = bootstrapOracleBox(pubKey5, rewardTokenQty = 50)

      val dataPoint1 = createDataPoint(1000, 0, oracleAddress, minStorageRent, oracleBox1, privKey1, 0, 10)
      val dataPoint2 = createDataPoint(1001, 0, oracleAddress, minStorageRent, oracleBox2, privKey2, 0, 20)
      val dataPoint3 = createDataPoint(1002, 0, oracleAddress, minStorageRent, oracleBox3, privKey3, 0, 30)
      val dataPoint4 = createDataPoint(1003, 0, oracleAddress, minStorageRent, oracleBox4, privKey4, 0, 40)
      val dataPoint5 = createDataPoint(1004, 0, oracleAddress, minStorageRent, oracleBox5, privKey5, 0, 50)

      val buyBackBox = createBuyback(500, minStorageRent)

      val inputs = Array[InputBox](
        poolBox,
        refreshBox.withContextVars(new ContextVar(0, KioskInt(0).getErgoValue)), // 1st dataPoint box (dataPoint1) is spender
        buyBackBox.withContextVars(new ContextVar(0, KioskInt(2).getErgoValue)),
        dataPoint1.withContextVars(new ContextVar(0, KioskInt(3).getErgoValue)), // output index 2 corresponds to dataPoint1
        dataPoint2.withContextVars(new ContextVar(0, KioskInt(4).getErgoValue)), // output index 3 corresponds to dataPoint2
        dataPoint3.withContextVars(new ContextVar(0, KioskInt(5).getErgoValue)), // output index 4 corresponds to dataPoint3
        dataPoint4.withContextVars(new ContextVar(0, KioskInt(6).getErgoValue)), // output index 5 corresponds to dataPoint4
        dataPoint5.withContextVars(new ContextVar(0, KioskInt(7).getErgoValue)), // output index 6 corresponds to dataPoint5
        dummyFundingBox
      )
      val dataInputs = Array[InputBox]()
      val outputs = Array[KioskBox](
        KioskBox(oraclePoolAddress, minStorageRent, Array(KioskLong(1002), KioskInt(1)), Array((config.poolNFT, 1), (rewardTokenId, defaultGortSupply - 10 + 500))),
        KioskBox(refreshAddress, minStorageRent, Array.empty, Array((config.refreshNFT, 1))),
        KioskBox(buybackAddress, minStorageRent, Array.empty, Array((buybackNFT, 1))),
        KioskBox(oracleAddress, minStorageRent, Array(pubKey1), Array((config.oracleTokenId, 1), (rewardTokenId, 16))),
        KioskBox(oracleAddress, minStorageRent, Array(pubKey2), Array((config.oracleTokenId, 1), (rewardTokenId, 21))),
        KioskBox(oracleAddress, minStorageRent, Array(pubKey3), Array((config.oracleTokenId, 1), (rewardTokenId, 31))),
        KioskBox(oracleAddress, minStorageRent, Array(pubKey4), Array((config.oracleTokenId, 1), (rewardTokenId, 41))),
        KioskBox(oracleAddress, minStorageRent, Array(pubKey5), Array((config.oracleTokenId, 1), (rewardTokenId, 51)))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          inputs,
          dataInputs,
          outputs,
          fee = 1000000L,
          changeAddress,
          Array[String](privKey1.toString),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("swap scenario") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      val gortLpErg = 1000 * 1000000000L
      val gortLpGort = 1000000L

      val buybackErg = 500 * 1000000000L

      val gortLpBox = createGortLpBox(gortLpErg, gortLpGort)
      val buyBackBox = createBuyback(gortAmt = 500, ergAmount = buybackErg)

      val inputs = Array[InputBox](
        gortLpBox,
        buyBackBox.withContextVars(new ContextVar(0, KioskInt(0).getErgoValue)),
        dummyFundingBox
      )
      val dataInputs = Array[InputBox]()

      val price = gortLpErg / gortLpGort
      val ergDelta = price * 500 / 100 * 105

      val outputs = Array[KioskBox](
        KioskBox(ordinaryLpAddress, gortLpErg + ergDelta, Array(KioskInt(995)), Array((gortLpNFT, 1), (gortLpToken, 1000000000L), (gort, gortLpGort - 500))),
        KioskBox(buybackAddress, buybackErg - ergDelta, Array.empty, Array((buybackNFT, 1), (gort, 1000)))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          inputs,
          dataInputs,
          outputs,
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("top-up scenario") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      val buyBackBox = createBuyback(gortAmt = 500)

      val inputs = Array[InputBox](
        buyBackBox.withContextVars(new ContextVar(0, KioskInt(1).getErgoValue)),
        dummyFundingBox
      )
      val dataInputs = Array[InputBox]()
      val outputs = Array[KioskBox](
        KioskBox(junkAddress, minStorageRent, Array.empty, Array.empty),
        KioskBox(junkAddress, minStorageRent, Array.empty, Array.empty),
        KioskBox(buybackAddress, buyBackBox.getValue + 1000000, Array.empty, Array((buybackNFT, 1), (gort, 500)))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          inputs,
          dataInputs,
          outputs,
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


object Calc extends App {
  val gortLpErg = 1000 * 1000000000L
  val gortLpGort = 1000000L
  val gortDelta = 500

  val ergDelta = gortLpErg * gortLpGort / (gortLpGort - gortDelta) - gortLpErg
  println(ergDelta)
}
