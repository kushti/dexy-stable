package dexy.lp

import dexy.Common
import dexy.chainutils.UseSpec._
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskLong}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

// Regression tests for the September 2026 LP hack (see spec/postmortem-lp-hack.md).
// Each test replays the exploit shape against one LP action: a fake LP box (trivially-true script,
// no lpNFT) at INPUTS(0) whose math the action contract validates, the real action box at INPUTS(1),
// the real LP box at the unexpected INPUTS(2) position, and a manipulated LP successor at
// OUTPUTS(0) (reserve-stripped for swap/redeem; LP-token-inflating for mint — the pool gains ERG
// there while the attacker takes extra LP tokens). Pre-fix these transactions succeed (that is how
// the pools were drained); post-fix the action-side LP NFT check (swap/mint/redeem .es validLpBox)
// and main.es validPosition reject them.
class LpHackSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
  val fakeNanoErgs = 10000000000000L
  val dummyNanoErgs = 100000L

  val dummyTokenId = "59e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0fad80a"

  property("Swap should fail when the LP box is replaced by a fake box (LP hack)") {
    val lpBalance = 100000000L
    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // fake LP box: trivially-true script, no lpNFT at tokens(0)
      val fakeLpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(dummyTokenId, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val swapBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpSwapNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpSwapScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      // real LP box placed at the unexpected INPUTS(2) position
      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      // reserve-stripped LP successor at OUTPUTS(0): script and token ids preserved, value drained
      val validLpOutBox = KioskBox(
        lpAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, lpBalance), (dexyUSD, reservesYIn))
      )

      val validSwapOutBox = KioskBox(
        lpSwapAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpSwapNFT, 1))
      )

      // the drained reserves go to the attacker
      val hackerOutBox = KioskBox(
        changeAddress,
        reservesXIn - minStorageRent,
        registers = Array(),
        tokens = Array()
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(fakeLpBox, swapBox, lpBox, fundingBox),
          Array(),
          Array(validLpOutBox, validSwapOutBox, hackerOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Mint should fail when the LP box is replaced by a fake box (LP hack)") {
    val lpBalance = 100000000L
    val fakeLpBalance = 1000000000L
    val fakeDexyBalance = 1000000000L

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // fake LP box: trivially-true script, no lpNFT at tokens(0)
      val fakeLpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(1000000000000L)
          .tokens(new ErgoToken(dummyTokenId, 1), new ErgoToken(lpToken, fakeLpBalance), new ErgoToken(dexyUSD, fakeDexyBalance))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val mintBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpMintNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpMintScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      // real LP box placed at the unexpected INPUTS(2) position
      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(1000000000000L)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpToken, lpBalance), new ErgoToken(dexyUSD, 2000000000L))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      // fake mint math holds between fakeLpBox and this output; the pool is not drained here —
      // it absorbs the fake box's nanoErgs while the attacker walks away with extra LP tokens
      // minted against it (redeemable against real reserves later, diluting honest LP holders)
      val validLpOutBox = KioskBox(
        lpAddress,
        2000000000000L,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpToken, fakeLpBalance - 1000), (dexyUSD, 2000000000L))
      )

      val validLpMintOutBox = KioskBox(
        lpMintAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpMintNFT, 1))
      )

      // tokens and value drained from the real LP box
      val hackerOutBox = KioskBox(
        changeAddress,
        dummyNanoErgs,
        registers = Array(),
        tokens = Array((dummyTokenId, 1), (lpToken, lpBalance + 1000), (dexyUSD, 1000000000L))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(fakeLpBox, mintBox, lpBox, fundingBox),
          Array(),
          Array(validLpOutBox, validLpMintOutBox, hackerOutBox),
          fee = 1000000L,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      } should have message "Script reduced to false"
    }
  }

  property("Redeem should fail when the LP box is replaced by a fake box (LP hack)") {
    val oracleRateXy = 10000L * 1000L
    val lpBalanceIn = initialLp - 100000000000L // same supply setup as the positive redeem test

    val reservesXIn = 1000000000000L
    val reservesYIn = 100000000L

    val lpRedeemed = 4995000L
    val withdrawX = 4895000L
    val withdrawY = 489L

    ergoClient.execute { implicit ctx: BlockchainContext =>
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(lpTokenId, lpRedeemed))
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
          .convertToInputWith(fakeTxId5, fakeIndex)

      // fake LP box: trivially-true script, no lpNFT at tokens(0)
      val fakeLpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(dummyTokenId, 1), new ErgoToken(lpTokenId, lpBalanceIn), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId2, fakeIndex)

      val redeemBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(minStorageRent)
          .tokens(new ErgoToken(lpRedeemNFT, 1))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpRedeemScript))
          .build()
          .convertToInputWith(fakeTxId3, fakeIndex)

      // real LP box placed at the unexpected INPUTS(2) position
      // (main.es checks only the LP token id, not the quantity, so a small balance is sufficient)
      val lpBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(reservesXIn)
          .tokens(new ErgoToken(lpNFT, 1), new ErgoToken(lpTokenId, 1000000L), new ErgoToken(dexyUSD, reservesYIn))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), lpScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      // fake redeem math holds between fakeLpBox and this output; reserves of the real LP box are drained into it
      val validLpOutBox = KioskBox(
        lpAddress,
        reservesXIn - withdrawX,
        registers = Array(),
        tokens = Array((lpNFT, 1), (lpTokenId, lpBalanceIn + lpRedeemed), (dexyUSD, reservesYIn - withdrawY))
      )

      val validRedeemOutBox = KioskBox(
        lpRedeemAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((lpRedeemNFT, 1))
      )

      // tokens and value drained from the real LP box
      val hackerOutBox = KioskBox(
        changeAddress,
        1000004895000L,
        registers = Array(),
        tokens = Array((dummyTokenId, 1), (lpTokenId, 1000000L), (dexyUSD, 100000489L))
      )

      the[Exception] thrownBy {
        TxUtil.createTx(
          Array(fakeLpBox, redeemBox, lpBox, fundingBox),
          Array(oracleBox),
          Array(validLpOutBox, validRedeemOutBox, hackerOutBox),
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
