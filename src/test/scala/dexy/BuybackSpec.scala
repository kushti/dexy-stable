package dexy

import kiosk.ergo.{DhtData, KioskBox}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, HttpClientTesting, InputBox}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class BuybackSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  property("giveback scenario") {
    ergoClient.execute { implicit ctx: BlockchainContext =>

      val inputs = Array[InputBox]()
      val dataInputs = Array[InputBox]()
      val outputs = Array[KioskBox]()

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

  }

  property("swap scenario") {

  }

}
