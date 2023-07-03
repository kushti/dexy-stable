package hodl

import dexy.Common
import dexy.DexySpec.readContract
import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.script.ScriptUtil
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.Values

class HodlCoinSpecification extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  val hodlScript = readContract("hodlcoin.es")

  val hodlErgoTree: Values.ErgoTree = ScriptUtil.compile(Map(), hodlScript)
  println(hodlErgoTree.root)
  val hodlAddress: String = getStringFromAddress(getAddressFromErgoTree(hodlErgoTree))

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val hodlTokenId = "2cbabc2be7292e2e857a1f2c34a8b0c090de2f30fa44c68ab71454e5586bd45e"
  val hodlContractNft = "2bbabc2be7292e2e857a1f2c34a8b0c090de2f30fa44c68ab71454e5586bd45e"

  val fakeNanoErgs = 10000000000000L

  val dev1Address = "9hHondX3uZMY2wQsXuCGjbgZUqunQyZCNNuwGu6rL7AJC8dhRGa"
  val dev2Address = "9gnBtmSRBMaNTkLQUABoAqmU2wzn27hgqVvezAC9SU1VqFKZCp8"
  val dev3Address = "9iE2MadGSrn1ivHmRZJWRxzHffuAk6bPmEv6uJmPHuadBY8td5u"

  property("hodlhack scenario") {

    val ergAmount = 1000 * 1000000000L

    val hodlErgAmount = 100 * 1000000000L

    val devFeesCollected = 30 * 1000000000L

    val hackAmount = ergAmount / 2

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val hodlBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(ergAmount)
          .tokens(new ErgoToken(hodlTokenId, hodlErgAmount), new ErgoToken(hodlContractNft, 1))
          .registers(KioskLong(devFeesCollected).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), hodlScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(1000000L)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        hodlAddress,
        ergAmount - devFeesCollected - hackAmount,
        registers = Array(KioskLong(0L)),
        tokens = Array((hodlTokenId, hodlErgAmount), (hodlContractNft, 1))
      )

      val dev1OutBox = KioskBox(
        dev1Address,
        devFeesCollected / 3,
        registers = Array(),
        tokens = Array()
      )

      val dev2OutBox = KioskBox(
        dev2Address,
        devFeesCollected / 3,
        registers = Array(),
        tokens = Array()
      )

      val dev3OutBox = KioskBox(
        dev3Address,
        devFeesCollected / 3,
        registers = Array(),
        tokens = Array()
      )

      val hackOutBox = KioskBox(
        dev3Address,
        hackAmount,
        registers = Array(),
        tokens = Array()
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, dev1OutBox, dev2OutBox, dev3OutBox, hackOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          broadcast = false
        )
      } should have message "Script reduced to false"
    }
  }

}
