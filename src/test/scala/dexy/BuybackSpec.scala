package dexy

import kiosk.ergo.{DhtData, KioskBox, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import oracles.OracleHelpers
import org.ergoplatform.appkit.{BlockchainContext, ContextVar, HttpClientTesting, InputBox}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class BuybackSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common with OracleHelpers {

  import oracles.OracleContracts._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  property("giveback scenario") {
    ergoClient.execute { implicit ctx: BlockchainContext =>

      val refreshBox = bootstrapRefreshBox()
      val poolBox = bootstrapPoolBox(ctx.getHeight - config.epochLength - 1, rate = 1)

      val oracleBox1 = bootstrapOracleBox(pubKey1, rewardTokenQty = 10)
      val oracleBox2 = bootstrapOracleBox(pubKey2, 20)
      val oracleBox3 = bootstrapOracleBox(pubKey3, 30)
      val oracleBox4 = bootstrapOracleBox(pubKey4, 40)
      val oracleBox5 = bootstrapOracleBox(pubKey5, 50)

      val dataPoint1 = createDataPoint(1000, 0, oracleAddress, minStorageRent, oracleBox1, privKey1, 0, 10)
      val dataPoint2 = createDataPoint(1001, 0, oracleAddress, minStorageRent, oracleBox2, privKey2, 0, 20)
      val dataPoint3 = createDataPoint(1002, 0, oracleAddress, minStorageRent, oracleBox3, privKey3, 0, 30)
      val dataPoint4 = createDataPoint(1003, 0, oracleAddress, minStorageRent, oracleBox4, privKey4, 0, 40)
      val dataPoint5 = createDataPoint(1004, 0, oracleAddress, minStorageRent, oracleBox5, privKey5, 0, 50)

      val inputs = Array[InputBox](
        poolBox,
        refreshBox.withContextVars(new ContextVar(0, KioskInt(0).getErgoValue)), // 1st dataPoint box (dataPoint1) is spender
        dataPoint1.withContextVars(new ContextVar(0, KioskInt(2).getErgoValue)), // output index 2 corresponds to dataPoint1
        dataPoint2.withContextVars(new ContextVar(0, KioskInt(3).getErgoValue)), // output index 3 corresponds to dataPoint2
        dataPoint3.withContextVars(new ContextVar(0, KioskInt(4).getErgoValue)), // output index 4 corresponds to dataPoint3
        dataPoint4.withContextVars(new ContextVar(0, KioskInt(5).getErgoValue)), // output index 5 corresponds to dataPoint4
        dataPoint5.withContextVars(new ContextVar(0, KioskInt(6).getErgoValue)), // output index 6 corresponds to dataPoint5
        dummyFundingBox
      )
      val dataInputs = Array[InputBox]()
      val outputs = Array[KioskBox](
        KioskBox(poolAddress, minStorageRent, Array(KioskLong(1002), KioskInt(1)), Array((config.poolNFT, 1), (rewardTokenId, 1000000L - 10))),
        KioskBox(refreshAddress, minStorageRent, Array.empty, Array((config.refreshNFT, 1))),
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

  property("top-up scenario") {

  }

  property("swap scenario") {

  }

}
