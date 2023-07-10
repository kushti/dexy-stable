package hodl

import dexy.{Common, ContractUtils}
import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.script.ScriptUtil
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting, InputBox}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.util.encode.Base64
import sigmastate.Values

class PhoenixSpecification extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common with ContractUtils {

  val phoenixFeeContractBytesHash = Array.fill(32)(0: Byte)

  override val substitutionMap: Map[String, String] = Map(
    "phoenixFeeContractBytesHash" -> Base64.encode(phoenixFeeContractBytesHash)
  )

  val phoenixScript = readContract("hodlcoin/phoenix.es")

  val phoenixErgoTree: Values.ErgoTree = ScriptUtil.compile(Map(), phoenixScript)
  val phoenixAddress: String = getStringFromAddress(getAddressFromErgoTree(phoenixErgoTree))

  val hodlTokenId = "2cbabc2be7292e2e857a1f2c34a8b0c090de2f30fa44c68ab71454e5586bd45e"
  val hodlBankNft = "2bbabc2be7292e2e857a1f2c34a8b0c090de2f30fa44c68ab71454e5586bd45e"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  // R4: Long             TotalTokenSupply
  // R5: Long             PrecisionFactor
  // R6: Long             MinBankValue
  // R7: Long             BankFee
  // R8: Long             DevFee

  val totalSupply = 50000000 * 1000000000L
  val precisionFactor = 1000000L
  val minBankValue = 1000000L
  val bankFee = 30L
  val devFee = 3L

  def extractPrecisionFactor(hodlBoxIn: InputBox): Long = {
    val precisionFactor = hodlBoxIn.getRegisters.get(1).getValue.asInstanceOf[Long] // R5
    precisionFactor
  }

  def hodlPrice(hodlBoxIn: InputBox): Long = {
    // preserving terminology from the contract
    val reserveIn = hodlBoxIn.getValue
    val totalTokenSupply = hodlBoxIn.getRegisters.get(0).getValue.asInstanceOf[Long] // R5
    val hodlCoinsIn: Long       = hodlBoxIn.getTokens.get(1).getValue
    val hodlCoinsCircIn: Long   = totalTokenSupply - hodlCoinsIn
    (reserveIn * extractPrecisionFactor(hodlBoxIn)) / hodlCoinsCircIn
  }

  // amount of (nano) ERGs needed to mint given amount of hodlcoins against given hodl bank
  def mintAmount(hodlBoxIn: InputBox, hodlMintAmt: Long): Long = {
    val price = hodlPrice(hodlBoxIn)
    val precisionFactor = extractPrecisionFactor(hodlBoxIn)
    (hodlMintAmt * price) / precisionFactor
  }


  property("phoenix mint works if all the conditions satisfied") {
    val ergAmount = 100000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    val hodlMintAmount = 20

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val hodlBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(ergAmount)
          .tokens(new ErgoToken(hodlBankNft, 1), new ErgoToken(hodlTokenId, hodlErgAmount))
          .registers(
            KioskLong(totalSupply).getErgoValue,
            KioskLong(precisionFactor).getErgoValue,
            KioskLong(minBankValue).getErgoValue,
            KioskLong(bankFee).getErgoValue,
            KioskLong(devFee).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), phoenixScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val price = hodlPrice(hodlBox)

      println("hodl price: " + price)

      val ergMintAmount = mintAmount(hodlBox, hodlMintAmount)
      println("ea: " + ergMintAmount)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(10000000000000L)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount - ergMintAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount))
      )

      val userBox = KioskBox(
        phoenixAddress,
        ergAmount,
        registers = Array(),
        tokens = Array((hodlTokenId, hodlErgAmount))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, userBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          broadcast = false
        )
      }
    }
  }

  property("phoenix burn works if all the conditions satisfied") {
    val ergAmount = 1000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val hodlBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(ergAmount)
          .tokens(new ErgoToken(hodlBankNft, 1), new ErgoToken(hodlTokenId, hodlErgAmount))
          .registers(
            KioskLong(totalSupply).getErgoValue,
            KioskLong(precisionFactor).getErgoValue,
            KioskLong(minBankValue).getErgoValue,
            KioskLong(bankFee).getErgoValue,
            KioskLong(devFee).getErgoValue,
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), phoenixScript))
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
    }
  }
}
