package hodl

import dexy.{Common, ContractUtils}
import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo.{DhtData, KioskBox, KioskLong}
import kiosk.script.ScriptUtil
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting, InputBox}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.crypto.hash.Blake2b256
import scorex.util.encode.{Base16, Base64}
import sigmastate.Values
import sigmastate.serialization.ErgoTreeSerializer.DefaultSerializer

class PhoenixSpecification extends PropSpec with Matchers
  with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common with ContractUtils {

  val userAddress = "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx"

  def feeSubstitutionMap: Map[String, String] = Map(
    "minerTree" -> Base64.encode(Base16.decode("1005040004000e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798ea02d192a39a8cc7a701730073011001020402d19683030193a38cc7b2a57300000193c2b2a57301007473027303830108cdeeac93b1a57304").get)
  )

  val feeScript = readContract("hodlcoin/phoenix/fee.es", Map.empty)
  val feeErgoTree: Values.ErgoTree = ScriptUtil.compile(Map(), feeScript)
  val feeScriptBytesHash = Blake2b256.apply(DefaultSerializer.serializeErgoTree(feeErgoTree))
  val feeAddress: String = getStringFromAddress(getAddressFromErgoTree(feeErgoTree))

  private val fundingBoxValue = 50000000 * 1000000000L

  override def defaultSubstitutionMap: Map[String, String] = Map(
    "phoenixFeeContractBytesHash" -> Base64.encode(feeScriptBytesHash)
  )

  val phoenixScript = readContract("hodlcoin/phoenix/phoenix.es")
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
    val totalTokenSupply = hodlBoxIn.getRegisters.get(0).getValue.asInstanceOf[Long] // R4
    val hodlCoinsIn: Long       = hodlBoxIn.getTokens.get(1).getValue
    val hodlCoinsCircIn: Long   = totalTokenSupply - hodlCoinsIn
    val precisionFactor = extractPrecisionFactor(hodlBoxIn)
    ((BigInt(reserveIn) * BigInt(precisionFactor)) / BigInt(hodlCoinsCircIn)).toLong
  }

  // amount of (nano) ERGs needed to mint given amount of hodlcoins against given hodl bank
  def mintAmount(hodlBoxIn: InputBox, hodlMintAmt: Long): Long = {
    val price = hodlPrice(hodlBoxIn)
    val precisionFactor = extractPrecisionFactor(hodlBoxIn)
    hodlMintAmt * price / precisionFactor
  }

  // amount of (nano) ERGs which can be released to when given amount of hodlcoins burnt

  /**
   * @return amount of (nano) ERGs which can be released to when given amount of hodlcoins burnt to user,
   *         and also dev fee
   */
  def burnAmount(hodlBoxIn: InputBox, hodlBurnAmt: Long): (Long, Long) = {
    val feeDenom = 1000

    val bankFee = hodlBoxIn.getRegisters.get(3).getValue.asInstanceOf[Long] // R7
    val devFee = hodlBoxIn.getRegisters.get(3).getValue.asInstanceOf[Long] // R8

    val price = hodlPrice(hodlBoxIn)
    val precisionFactor = extractPrecisionFactor(hodlBoxIn)
    val beforeFees = hodlBurnAmt * price / precisionFactor
    val bankFeeAmount: Long = (beforeFees * bankFee) / feeDenom
    val devFeeAmount: Long = (beforeFees * devFee) / feeDenom
    val expectedAmountWithdrawn: Long = beforeFees - bankFeeAmount - devFeeAmount
    (expectedAmountWithdrawn, devFeeAmount)
  }


  property("phoenix mint works if all the conditions satisfied") {
    val ergAmount = 10000000 * 1000000000L
    val hodlErgAmount = totalSupply / 10 * 9

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

      require(hodlBox.getValue >= totalSupply - hodlErgAmount, "never-decreasing theorem does not hold")
      require(price == 2000000, "Price does not correspond to manually calculated value")

      val ergMintAmount = mintAmount(hodlBox, hodlMintAmount)
      require(ergMintAmount == 40, "Erg delta does not correspond to manually calculated value ")

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount + ergMintAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount - hodlMintAmount))
      )

      val userBox = KioskBox(
        userAddress,
        ergAmount,
        registers = Array(),
        tokens = Array((hodlTokenId, hodlMintAmount))
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

  property("phoenix mint fails if less ERGs provided") {
    val ergAmount = 10000000 * 1000000000L
    val hodlErgAmount = totalSupply / 10 * 9

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

      val ergMintAmount = mintAmount(hodlBox, hodlMintAmount) - 1 // this line changed - 1 nanoERG less to the bank

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount + ergMintAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount - hodlMintAmount))
      )

      val userBox = KioskBox(
        userAddress,
        ergAmount,
        registers = Array(),
        tokens = Array((hodlTokenId, hodlMintAmount))
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("phoenix mint fails if more hodl taken") {
    val ergAmount = 10000000 * 1000000000L
    val hodlErgAmount = totalSupply / 10 * 9

    val hodlMintAmount = Long.MaxValue - 1  // this line changed - 1 hodl more from the bank

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

      val ergMintAmount = mintAmount(hodlBox, hodlMintAmount - 1) // this line changed - still old amount of ERG paid

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount + ergMintAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount - hodlMintAmount))
      )

      val userBox = KioskBox(
        userAddress,
        ergAmount,
        registers = Array(),
        tokens = Array((hodlTokenId, hodlMintAmount))
      )

      the[Exception] thrownBy {
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
      } should have message "Script reduced to false"
    }
  }

  property("phoenix burn works if all the conditions satisfied") {
    val ergAmount = 1000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    val hodlBurnAmount = 20

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

      val (userBoxAmount, devFeeAmount) = burnAmount(hodlBox, hodlBurnAmount)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .tokens(new ErgoToken(hodlTokenId, hodlBurnAmount))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount - userBoxAmount - devFeeAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount + hodlBurnAmount))
      )

      val userBox = KioskBox(
        userAddress,
        userBoxAmount,
        registers = Array(),
        tokens = Array()
      )

      val devFeeBox = KioskBox(
        feeAddress,
        devFeeAmount,
        registers = Array(),
        tokens = Array()
      )


      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, userBox, devFeeBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          broadcast = false
        )
      }
    }
  }


  property("phoenix burn fails if user box takes more erg") {
    val ergAmount = 1000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    val hodlBurnAmount = 20

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

      val (userBoxAmount, devFeeAmount) = burnAmount(hodlBox, hodlBurnAmount)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .tokens(new ErgoToken(hodlTokenId, hodlBurnAmount))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount - userBoxAmount - devFeeAmount - 1, // <-- this line changed
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount + hodlBurnAmount))
      )

      val userBox = KioskBox(
        userAddress,
        userBoxAmount + 1, // <-- this line changed
        registers = Array(),
        tokens = Array()
      )

      val devFeeBox = KioskBox(
        feeAddress,
        devFeeAmount,
        registers = Array(),
        tokens = Array()
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, userBox, devFeeBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          broadcast = false
        )
      } should have message "Script reduced to false"
    }
  }

  property("phoenix burn fails if dev box takes more erg") {
    val ergAmount = 1000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    val hodlBurnAmount = 20

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

      val (userBoxAmount, devFeeAmount) = burnAmount(hodlBox, hodlBurnAmount)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .tokens(new ErgoToken(hodlTokenId, hodlBurnAmount))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount - userBoxAmount - devFeeAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount + hodlBurnAmount))
      )

      val userBox = KioskBox(
        userAddress,
        userBoxAmount,
        registers = Array(),
        tokens = Array()
      )

      val devFeeBox = KioskBox(
        feeAddress,
        devFeeAmount + 1, // <-- this line changed
        registers = Array(),
        tokens = Array()
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, userBox, devFeeBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          broadcast = false
        )
      } should have message "Script reduced to false"
    }
  }

  property("phoenix burn fails if dev box has improper script") {
    val ergAmount = 1000 * 1000000000L
    val hodlErgAmount = 100 * 1000000000L

    val hodlBurnAmount = 20

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

      val (userBoxAmount, devFeeAmount) = burnAmount(hodlBox, hodlBurnAmount)

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fundingBoxValue)
          .tokens(new ErgoToken(hodlTokenId, hodlBurnAmount))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      val hodlOutBox = KioskBox(
        phoenixAddress,
        ergAmount - userBoxAmount - devFeeAmount,
        registers = Array(KioskLong(totalSupply), KioskLong(precisionFactor), KioskLong(minBankValue),
          KioskLong(bankFee), KioskLong(devFee)),
        tokens = Array((hodlBankNft, 1), (hodlTokenId, hodlErgAmount + hodlBurnAmount))
      )

      val userBox = KioskBox(
        userAddress,
        userBoxAmount,
        registers = Array(),
        tokens = Array()
      )

      val devFeeBox = KioskBox(
        userAddress,
        devFeeAmount,
        registers = Array(),
        tokens = Array()
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(hodlBox, fundingBox),
          Array(),
          Array(hodlOutBox, userBox, devFeeBox),
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
