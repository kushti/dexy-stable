package dexy.lp

import dexy.Common
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, HttpClientTesting, InputBox}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// Each property exercises one check from the LP hack fix and keeps the other check out of the transaction:
//  - main.es validPosition: the action NFT (swap, extraction, or intervention) sits in a box with a trivially-true
//    script, so no action script runs. extract.es and intervention.es run no checks when INPUTS(0) holds
//    updateNFT. While a P2PK box holds updateNFT, validPosition is the only check for the LP box on those paths.
//  - swap.es validLpBox: no LP box is in the transaction, so main.es does not run.
// Each negative property has a positive control that differs only in the checked condition.
class LpGuardIsolationSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyTokenId = "59e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  val lpBalance = 100000000L
  val reservesXIn = 1000000000000L
  val reservesYIn = 100000000L

  private def box(value: Long, script: String, tokens: Seq[ErgoToken], txId: String)(implicit ctx: BlockchainContext): InputBox = {
    val b = ctx.newTxBuilder().outBoxBuilder.value(value).contract(ctx.compileContract(ConstantsBuilder.empty(), script))
    (if (tokens.isEmpty) b else b.tokens(tokens: _*)).build().convertToInputWith(txId, fakeIndex)
  }

  // Puts `actionNft` in a box with a trivially-true script at `actionIndex` and the LP box at `lpIndex`.
  // The LP successor keeps its reserves, so only the position of the LP box differs between cases.
  private def signLp(actionNft: String, actionIndex: Int, lpIndex: Int)(implicit ctx: BlockchainContext) = {
    val fillerTxIds = Iterator(fakeTxId2, fakeTxId5, fakeTxId6, fakeTxId7)
    val size = math.max(actionIndex, lpIndex) + 1
    val inputs: Array[InputBox] = (0 until size).map { i =>
      if (i == lpIndex) box(reservesXIn, lpScript,
        Seq(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn)), fakeTxId4)
      else if (i == actionIndex) box(minStorageRent, fakeScript, Seq(new ErgoToken(actionNft, 1)), fakeTxId3)
      // main.es reads INPUTS(1).tokens(0) on every path, so fillers carry a dummy token (the bank box does in real flows)
      else box(minStorageRent, fakeScript, Seq(new ErgoToken(dummyTokenId, 1)), fillerTxIds.next())
    }.toArray :+ box(fakeNanoErgs, fakeScript, Nil, fakeTxId1)
    val lpOut = KioskBox(lpAddress, reservesXIn, registers = Array(),
      tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYIn)))
    TxUtil.createTx(inputs, Array(), Array(lpOut), fee = 1000000L, changeAddress, Array[String](), Array[DhtData](), false)
  }

  // (action NFT, index where main.es looks for it, non-zero LP index used in the negative case)
  private val mainEsPaths = Seq(
    ("swap", lpSwapNFT, 1, 2),
    ("extract", extractionNFT, 1, 2),
    ("intervention", interventionNFT, 2, 3)
  )

  mainEsPaths.foreach { case (name, nft, actionIndex, lpIndex) =>
    property(s"main.es rejects the LP box at INPUTS($lpIndex) on the $name path when reserves are unchanged") {
      ergoClient.execute { implicit ctx: BlockchainContext =>
        the[Exception] thrownBy signLp(nft, actionIndex, lpIndex) should have message "Script reduced to false"
      }
    }

    property(s"main.es accepts the same $name transaction with the LP box at INPUTS(0) (control)") {
      ergoClient.execute { implicit ctx: BlockchainContext =>
        noException shouldBe thrownBy(signLp(nft, actionIndex, 0))
      }
    }
  }

  private def signSwapOnly(poolNft: String)(implicit ctx: BlockchainContext) = {
    val funding = box(fakeNanoErgs, fakeScript, Nil, fakeTxId1)
    val pool = box(minStorageRent, fakeScript,
      Seq(new ErgoToken(poolNft, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn)), fakeTxId2)
    val swapBox = box(minStorageRent, lpSwapScript, Seq(new ErgoToken(lpSwapNFT, 1)), fakeTxId3)
    // zero deltas, so validSwap and selfPreserved hold and only validLpBox decides
    val poolOut = KioskBox(changeAddress, minStorageRent, registers = Array(),
      tokens = Array((poolNft, 1), (lpToken, lpBalance), (dexyUSD, reservesYIn)))
    val swapOut = KioskBox(lpSwapAddress, minStorageRent, registers = Array(), tokens = Array((lpSwapNFT, 1)))
    TxUtil.createTx(Array(pool, swapBox, funding), Array(), Array(poolOut, swapOut), fee = 1000000L,
      changeAddress, Array[String](), Array[DhtData](), false)
  }

  property("swap.es rejects a pool box without the LP NFT at INPUTS(0)") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      the[Exception] thrownBy signSwapOnly(dummyTokenId) should have message "Script reduced to false"
    }
  }

  property("swap.es accepts the same transaction when the pool box holds the LP NFT (control)") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      noException shouldBe thrownBy(signSwapOnly(lpNFT))
    }
  }
}
