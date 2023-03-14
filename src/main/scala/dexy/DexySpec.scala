package dexy

import kiosk.encoding.ScalaErgoConverters.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import kiosk.script.ScriptUtil
import scorex.util.encode.Base64

object DexySpec {
  // tokens for main boxes
  val oracleNFT = "472B4B6250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val bankNFT = "861A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val lpNFT = "361A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val extractionNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A54" // TODO replace with actual
  val payoutNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A52" // TODO replace with actual
  val lpSwapNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A51" // TODO replace with actual
  val lpMintNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A50" // TODO replace with actual
  val lpRedeemNFT = "161A3A5250655368566D597133743677397A24432646294A404D635166546A59" // TODO replace with actual

  val freeMintNFT = "061A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val arbitrageMintNFT = "961A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual

  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking98NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A57" // TODO replace with actual
  val tracking95NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A55" // TODO replace with actual
  val tracking101NFT = "261A3A5250655368566D597133743677397A24432646294A404D635166546A58" // TODO replace with actual

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
    "freeMintNFT" -> freeMintNFT,
    "arbitrageMintNFT" -> arbitrageMintNFT,
    "interventionNFT" -> interventionNFT,
    "payoutNFT" -> payoutNFT,
    "oracleNFT" -> oracleNFT,
    "bankNFT" -> bankNFT,
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
    "intMax" -> Int.MaxValue.toString
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

  val payToSwapScript = readContract("bank/paytoswap.es")
  val payToSwapErgoTree = ScriptUtil.compile(Map(), payToSwapScript)
  val payToSwapAddress = getStringFromAddress(getAddressFromErgoTree(payToSwapErgoTree))


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

    println(s"Pay to swap: $payToSwapAddress")
    println(payToSwapScript)
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
  }
}
