package dexy.bank

import dexy.Common
import dexy.chainutils.DexySpec
import dexy.chainutils.DexySpec._
import kiosk.ErgoUtil
import kiosk.encoding.ScalaErgoConverters
import kiosk.encoding.ScalaErgoConverters.stringToGroupElement
import kiosk.ergo.{DhtData, KioskBoolean, KioskBox, KioskCollByte, KioskGroupElement, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoToken, HttpClientTesting}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.crypto.hash.Blake2b256

class InterventionSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
    with HttpClientTesting with Common {

  import dexy.chainutils.TestnetTokenIds._

  val dummyTokenId = "a1e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b801"

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  val T = 360

  // todo: check 1% max spending rule

  property("Intervention (transfer Ergs from Bank to Lp and Dexy from Lp to Bank) should work") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Intervention fails if not enough Dexy tokens taken from the LP") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val preWithdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong
    val withdrawY = preWithdrawY * 98 / 100

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention fails if too many Dexy tokens taken from the LP") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val preWithdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong
    val withdrawY = preWithdrawY * 1001 / 1000

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    // condition opposite to one in LP contract
    val deltaReservesY = lpReservesYIn - lpReservesYOut
    val deltaReservesX = lpReservesXOut - lpReservesXIn
    assert(BigInt(deltaReservesY) * lpReservesXIn > BigInt(deltaReservesX) * lpReservesYIn)

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceIn), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if LP tokens reduced") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn - 1 // one LP token reduced

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Intervention should fail if LP tokens increased") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn + 1 // one LP token increased

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Intervention should fail if bank script changed") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        changeAddress, // <--------------- this address is changed
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if Lp script changed") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        changeAddress, // <--------------- this address is changed
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if Intervention script changed") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        changeAddress,  // <--------------- this address is changed
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if tracking height is less") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int // <--------------- this value is changed

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if last intervention height is less") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T // <--------------- this value is changed

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if tracker NFT is different") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1))  // <--------------- this value is changed
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if oracle NFT is different") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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
          .tokens(new ErgoToken(dummyTokenId, 1))  // <--------------- this value is changed
          .registers(KioskLong(oracleRateXy).getErgoValue)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if tracker is not triggered") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(Int.MaxValue).getErgoValue  // <--------------- this value is changed
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should work if more tokens deposited to bank than taken from Lp") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(dexyUSD, 1))
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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (
          dexyUSD,
          bankReservesYOut + 1 // one extra deposited
        ))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Intervention should work if more Ergs deposited to Lp than taken from Bank") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut + 1, // one extra deposited
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

  property("Intervention should not work if less Ergs deposited to Lp than taken from Bank") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut - 1,  // one extra erg taken from bank
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should not work if less Tokens deposited to Bank than taken from Lp") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn
    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (
          dexyUSD,
          bankReservesYOut - 1  // one less Dexy deposited to bank
        ))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if ergs reduced in Lp box") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = -1L // ergs reduced
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if nothing changed in Lp box") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 0L // nothing changed
    val withdrawY = 0L // nothing changed

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy) * lpReservesYOut
    assert(a * 1000 <= b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if more ergs deposited to Lp box") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = (lpRateXyIn * 100 / thresholdPercent + 1) * 1000000L

    val depositX = 6000000000000L // more ergs deposited
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val a = BigInt(lpReservesXOut)
    val b = BigInt(oracleRateXy / 1000000L) * lpReservesYOut
    assert(a * 1000 > b * 995)

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Intervention should fail if oracle rate is lower than needed") {
    val lpBalanceIn = 100000000L

    val thresholdPercent = 98

    val bankReservesXIn = 1000000000000000L // Nano Ergs
    val bankReservesYIn = 10000000000L // Dexy

    val lpReservesXIn = 100000000000000L
    val lpReservesYIn = 10000000000L

    val lpRateXyIn = lpReservesXIn / lpReservesYIn
    val oracleRateXy = lpRateXyIn * 100 / thresholdPercent * 1000000L // oracle rate is one less than needed

    val depositX = 50000000000L
    val withdrawY = (BigInt(depositX) * lpReservesYIn / lpReservesXIn).toLong

    val lpReservesXOut = lpReservesXIn + depositX
    val lpReservesYOut = lpReservesYIn - withdrawY

    val bankReservesXOut = bankReservesXIn - depositX // Nano Ergs
    val bankReservesYOut = bankReservesYIn + withdrawY // Dexy

    val lpBalanceOut = lpBalanceIn

    val lpRateXyOut = lpReservesXOut / lpReservesYOut

    ergoClient.execute { implicit ctx: BlockchainContext =>

      val T_int = 20

      val trackingHeightIn = ctx.getHeight - T_int - 1

      val lastInterventionHeight = ctx.getHeight - T - 1

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

      val tracking98Box =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(tracking98NFT, 1))
          .registers(
            KioskInt(49).getErgoValue, // numerator for 98%
            KioskInt(50).getErgoValue, // denominator for 98%
            KioskBoolean(true).getErgoValue, // isBelow
            KioskInt(trackingHeightIn).getErgoValue
          )
          .contract(ctx.compileContract(ConstantsBuilder.empty(), DexySpec.trackingScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(lpReservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalanceIn), new ErgoToken(dexyUSD, lpReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(bankReservesXIn)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, bankReservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(interventionNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .creationHeight(lastInterventionHeight)
          .build()
          .convertToInputWith(fakeTxId5, fakeIndex)

      val validLpOutBox = KioskBox(
        lpAddress,
        lpReservesXOut,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalanceOut), (dexyUSD, lpReservesYOut))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        bankReservesXOut,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, bankReservesYOut))
      )

      val validInterventionOutBox = KioskBox(
        interventionAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((interventionNFT, 1))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(lpBox, bankBox, interventionBox, fundingBox),
          Array(oracleBox, tracking98Box),
          Array(validLpOutBox, validBankOutBox, validInterventionOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Successful update when enough votes collected") {
    val fee = 1500000

    ergoClient.execute { implicit ctx: BlockchainContext =>
      object Voters {
        // define voters
        val addresses = Seq(
          "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx", // private key is 37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0
          "9f9q6Hs7vXZSQwhbrptQZLkTx15ApjbEkQwWXJqD2NpaouiigJQ", // private key is 5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d
          "9fGp73EsRQMpFC7xaYD5JFy2abZeKCUffhDBNbQVtBtQyw61Vym", // private key is 3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e
        ).toArray

        val privateKey0 = "37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0"
        val privateKey1 = "5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d"
        val privateKey2 = "3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e"

        val r4voter0 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(0))))
        val r4voter1 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(1))))
        val r4voter2 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(2))))

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 1L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 1L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }

      // value to vote for; hash of new bank box script
      val valueVotedFor = KioskCollByte(Blake2b256.hash(bankErgoTree.bytes)) //todo: real update

      // dummy custom input box for funding various transactions
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // current update box
      val updateBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(updateAddress).script))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

      val ballot0InputToCreate = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot1InputToCreate = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot2InputToCreate = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      // create ballots
      val ballot0 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot0Box.toInBox(fakeTxId5, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot0InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey0),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot1 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot1Box.toInBox(fakeTxId6, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot1InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey1),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot2 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot2Box.toInBox(fakeTxId7, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot2InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey2),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val interventionBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(interventionNFT, 1), new ErgoToken(dexyUSD, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), interventionScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validUpdateOutBox = KioskBox(
        updateAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((updateNFT, 1))
      )

      val validInterventionOutBox = KioskBox(
        bankAddress, // different address!
        fakeNanoErgs,
        registers = Array(),
        tokens = Array((interventionNFT, 1), (dexyUSD, 1))
      )

      val validBallot0Output = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0)
        )
      )

      val validBallot1Output = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0)
        )
      )

      val validBallot2Output = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0)
        )
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(updateBox, interventionBox, ballot0, ballot1, ballot2, fundingBox),
          Array(),
          Array(validUpdateOutBox, validInterventionOutBox, validBallot0Output, validBallot1Output, validBallot2Output),
          fee,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }


}
