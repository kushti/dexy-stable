package dexy

import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import kiosk.script.ScriptUtil
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant}
import sigmastate.serialization.ValueSerializer

object DexySpec {
  // oracle related tokens

  // Gold Oracle Reward Token
  val gort = "e2636c9f0e32886954ab1f87ac2e016fdf53d63d8fa2101530d1e31ac59e365f"
  // GORT / ERG LP
  val gortLpNFT = "601a587fd8c2921b52a77ad643f20efe9bf0f10110e3c21bf38381e590efa8a3"
  val oracleTokenId = "cc8d50d2a9f0f1254f363ee66a3c65e73ea8046386c001544b08841743df411d"

  // tokens for main boxes
  val oraclePoolNFT = "f2f64bab1f2e9ab65c0e4a6a65eed0b26c5276eb5dda01d4a0e0ebe8a85bfbfc"
  val bankNFT = "51ba86402f72f2c3e429cbd2e2b04022e4efa7812d25de1c4176cdbafb28dda6"
  val lpNFT = "444fead9b33519a4f4ff0358d668df1a69331d35d46f31c6c69640f306c5c1a3"

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "e34aa448d3bbb460a3b2f803d71a42ac4e05a741eea67ae81d5a9120cbe528c0"
  val extractionNFT = "0d83548d9a3d75d91e9362678c3a427a59ed74e31caff4b9e7d3ee7dae83f363"
  val payoutNFT = "2b0ec124afe13f27477fb4c49395f12a992790a21e85420357679bd1068b15e3"
  val lpSwapNFT = "9575571b4a25eb6309b115b5fb74305455807d227c91602eec603b6364bb6a02"
  val lpMintNFT = "2759643a389cedc5e2e37909ca19306e776c52c9085a45abb79b85a967654a2a"
  val lpRedeemNFT = "637c9ef5402f4fc468ef5fd284f808c56454702bb8f97dfaa738214e4f310116"

  val buybackNFT = "01f06a2c6fac3b6fe596627e72cfa37fef450330789777c572cc3a9ab266e152"

  val freeMintNFT = "2a77bd4056753fb95533ce6149f7b1a60050b05d24d1190ce2f78102a8a5312e"
  val arbitrageMintNFT = "7d757b372e0b2d3b77823c8abba3853231a6d770e400e6a3853db7ef18be5bf6"

  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT = "2a69d7ce75ec961ab3329ddca7a9479044fb0b160b8fef26a632322b994ebced"
  val tracking98NFT = "1839000c4332ff55b162e974b04c0ed68a8dbf572f458c2692f067e73f0c74e9"
  val tracking101NFT = "14a15e8371c0dbde6335af9750336ea293b127489b0bb687416dfc2deb496f73"

  // High level idea:
  // There are 3 main boxes in the protocol, and the others are auxiliary boxes to manage the main boxes
  // Main boxes:
  //   1. Bank box that emits Dexy tokens
  //   2. Liquidity pool (LP) box that allows swapping Dexy with Ergs
  //   3. Oracle (pool) box that has the rate of Erg/USD in R4 (Long) in units nanoErgs per USD

  lazy val initialDexyTokens = 10000000000000L

  val initialLp =
    100000000000L // initially how many Lp minted (and we need to add that many to tokens(1), after removing some for token burning as in UniSwap v2)

  val feeNumLp = 3
  val feeDenomLp = 1000

  val nftDictionary: Map[String, String] = Map(
    "oracleTokenId" -> oracleTokenId,
    "gortId" -> gort,
    "gortLpNFT" -> gortLpNFT,
    "freeMintNFT" -> freeMintNFT,
    "arbitrageMintNFT" -> arbitrageMintNFT,
    "interventionNFT" -> interventionNFT,
    "payoutNFT" -> payoutNFT,
    "oracleNFT" -> oraclePoolNFT,
    "bankNFT" -> bankNFT,
    "buybackNFT" -> buybackNFT,
    "lpNFT" -> lpNFT,
    "tracking95NFT" -> tracking95NFT,
    "tracking98NFT" -> tracking98NFT,
    "tracking101NFT" -> tracking101NFT,
    "extractionNFT" -> extractionNFT,
    "lpSwapNFT" -> lpSwapNFT,
    "lpMintNFT" -> lpMintNFT,
    "lpRedeemNFT" -> lpRedeemNFT
  ).mapValues(hex => Base64.encode(hex.decodeHex)) ++ Map(
    "initialDexyTokens" -> (initialDexyTokens.toString + "L"),
    "feeNumLp" -> (feeNumLp.toString + "L"),
    "feeDenomLp" -> (feeDenomLp.toString + "L"),
    "initialLp" -> (initialLp.toString + "L"),
    "intMax" -> Int.MaxValue.toString,
    "epochLength" -> 30.toString
  )

  // totally inefficient substitution method, but ok for our contracts
  def substitute(contract: String): String = {
    nftDictionary.foldLeft(contract){case (c, (k,v)) =>
      c.replace("$"+k, v)
    }
  }

  def readContract(path: String) = {
    substitute(scala.io.Source.fromFile("contracts/" + path, "utf-8").getLines.mkString("\n"))
  }

  // arbitrage mint box
  val arbitrageMintScript = readContract("bank/arbmint.es")


  // free mint box
  val freeMintScript = readContract("bank/freemint.es")

  // payout box
  val payoutScript = readContract("bank/payout.es")

  // below contract is adapted from N2T DEX contract in EIP-14 https://github.com/ergoplatform/eips/blob/de30f94ace1c18a9772e1dd0f65f00caf774eea3/eip-0014.md?plain=1#L558-L636
  lazy val lpScript = readContract("lp/lp.es")

  val lpSwapScript = readContract("lp/swap.es")

  val lpMintScript = readContract("lp/mint.es")

  val lpRedeemScript = readContract("lp/redeem.es")

  val trackingScript = readContract("tracking.es")

  val extractScript = readContract("lp/extract.es")

  val trackingErgoTree = ScriptUtil.compile(Map(), trackingScript)
  val trackingAddress = getStringFromAddress(getAddressFromErgoTree(trackingErgoTree))

  val bankScript = readContract("bank/bank.es")
  val bankErgoTree = ScriptUtil.compile(Map(), bankScript)
  val bankAddress = getStringFromAddress(getAddressFromErgoTree(bankErgoTree))

  val arbitrageMintErgoTree = ScriptUtil.compile(Map(), arbitrageMintScript)
  val arbitrageMintAddress = getStringFromAddress(getAddressFromErgoTree(arbitrageMintErgoTree))
  val freeMintErgoTree = ScriptUtil.compile(Map(), freeMintScript)
  val freeMintAddress = getStringFromAddress(getAddressFromErgoTree(freeMintErgoTree))
  val payoutErgoTree = ScriptUtil.compile(Map(), payoutScript)
  val payoutAddress = getStringFromAddress(getAddressFromErgoTree(payoutErgoTree))

  val interventionScript = readContract("bank/intervention.es")
  val interventionErgoTree = ScriptUtil.compile(Map(), interventionScript)
  val interventionAddress = getStringFromAddress(getAddressFromErgoTree(interventionErgoTree))

  val buybackScript = readContract("bank/buyback.es")
  val buybackErgoTree = ScriptUtil.compile(Map(), buybackScript)
  val buybackAddress = getStringFromAddress(getAddressFromErgoTree(buybackErgoTree))


  val lpErgoTree = ScriptUtil.compile(Map(), lpScript)
  val lpAddress = getStringFromAddress(getAddressFromErgoTree(lpErgoTree))
  val lpSwapErgoTree = ScriptUtil.compile(Map(), lpSwapScript)
  val lpSwapAddress = getStringFromAddress(getAddressFromErgoTree(lpSwapErgoTree))
  val lpMintErgoTree = ScriptUtil.compile(Map(), lpMintScript)
  val lpMintAddress = getStringFromAddress(getAddressFromErgoTree(lpMintErgoTree))
  val lpRedeemErgoTree = ScriptUtil.compile(Map(), lpRedeemScript)
  val lpRedeemAddress = getStringFromAddress(getAddressFromErgoTree(lpRedeemErgoTree))
  val extractErgoTree = ScriptUtil.compile(Map(), extractScript)
  val extractAddress = getStringFromAddress(getAddressFromErgoTree(extractErgoTree))


  def main(args: Array[String]): Unit = {
    println(s"Bank: $bankAddress")
    println(bankScript)
    println()

    println(s"ArbitrageMint: $arbitrageMintAddress")
    println(arbitrageMintScript)
    println()

    println(s"FreeMint: $freeMintAddress")
    println(freeMintScript)
    println()

    println(s"Payout: $payoutAddress")
    println(payoutScript)
    println()

    println(s"Buyback: $buybackAddress")
    println(buybackScript)
    println()

    println(s"LP: $lpAddress")
    println(lpScript)
    println()

    println(s"LP Swap: $lpSwapAddress")
    println(lpSwapScript)
    println()

    println(s"LP Mint: $lpMintAddress")
    println(lpMintScript)
    println()

    println(s"LP Redeem: $lpRedeemAddress")
    println(lpRedeemScript)
    println()

    println(s"Tracking: $trackingAddress")
    println(trackingScript)
    println()

    println(s"Extract: $extractAddress")
    println(extractScript)
    println()

    println(s"Intervention: $interventionAddress")
    println(interventionScript)
    println()

    val num95 = Base16.encode(ValueSerializer.serialize(IntConstant(95)))

    val num98 = Base16.encode(ValueSerializer.serialize(IntConstant(98)))

    val num101 = Base16.encode(ValueSerializer.serialize(IntConstant(101)))

    val denum = Base16.encode(ValueSerializer.serialize(IntConstant(100)))

    val falseValue = Base16.encode(ValueSerializer.serialize(BooleanConstant(false)))

    val trueValue = Base16.encode(ValueSerializer.serialize(BooleanConstant(true)))

    val intMaxValue = Base16.encode(ValueSerializer.serialize(IntConstant(Int.MaxValue)))

    val req =
      s"""
        |{
        |  "requests": [
        |    {
        |      "address": "$trackingAddress",
        |      "value": 1000000000,
        |      "assets": [
        |        {
        |          "tokenId": "$tracking95NFT",
        |          "amount": 1
        |        }
        |      ],
        |      "registers": {
        |        "R4": "$denum",
        |        "R5": "$num95",
        |        "R6": "$trueValue",
        |        "R7": "$intMaxValue"
        |      }
        |    }
        |  ]
        |}
        |""".stripMargin

    println(req)
  }
}
