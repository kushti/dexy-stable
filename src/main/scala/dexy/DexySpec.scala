package dexy

import dexy.ScriptUtil.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant, LongConstant}
import sigmastate.serialization.ValueSerializer

object DexySpec {
  // oracle related tokens
  val dexyTokenId = "20e901376d96816a407916c42c5ad789a9698c3041b77ddb46d19b78fedba649"

  val lpTokenId = "0ebc7e8fd7739ad3fb62430f600c59bca4ed1ee351d34eeabe99e9965207b0b6"

  // Gold Oracle Reward Token
  val gort = "e2636c9f0e32886954ab1f87ac2e016fdf53d63d8fa2101530d1e31ac59e365f"
  val oracleTokenId = "cc8d50d2a9f0f1254f363ee66a3c65e73ea8046386c001544b08841743df411d"
  val oraclePoolNFT = "f2f64bab1f2e9ab65c0e4a6a65eed0b26c5276eb5dda01d4a0e0ebe8a85bfbfc"

  // GORT / ERG LP
  val gortLpNFT = "73d715bca9f5c40be602f664f9590c20c4be7aeebc2792aa236b06518d22d265"

  // tokens for main boxes
  val bankNFT = "b40ca7aec3f38d286692661a92aecffc431f096877428ee735f72ddb4ea38fb4"
  val lpNFT = "1aa26bc8d5e8c55f62816f6015b4201baf87322a6b4e2d407766af7740c4ed7c"

  // update tokens
  val updateNFT = ""
  val ballotTokenId = ""

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "99c9caa94816a0da9b5d4d653ac3a04d4ad107fa279ac7a2aadd7e3e66de8cca"
  val freeMintNFT = "9a8648530229195e31422ce6b1b587541972b2e0a73d54df33b2e248a42eb153"
  val arbitrageMintNFT = "d8d96710cc66f21d1ae6e83438826ad611b7e181df5d59e5b6e796fdc603e5dd"
  val buybackNFT = "2cbabc2be7292e2e857a1f2c34a8b0c090de2f30fa44c68ab71454e5586bd45e"
  val payoutNFT = "1aa26bc8d5e8c55f62816f6015b4201baf87322a6b4e2d407766af7740c4ed7c"

  val lpSwapNFT = "1049da47189d0a525329e92ebbb12fd8a36595ced38cec38f6bc2f979c214095"
  val lpMintNFT = "71c44ddda3121592762cebc67c345cd0fa4c4c5cc7276834cbd1462c27b6c40f"
  val lpRedeemNFT = "32d8b9524509af5f456c9104401d600f77f66d1c0547c83ed8bed867d6684867"
  val extractionNFT = "1aea0f37c54736a2c2ea5629dfeab31c40b011bb4eb74d43e4aaeeffafdcac24"

  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT = "899ce775313b36395046eef952575752efbbfabc75dc64193e0c4feafeb1c793"
  val tracking98NFT = "5992c8f4e12449675abc924604af0e9ef225d0c78138b510a7d901064bc9610e"
  val tracking101NFT = "91adc4201764e7a1aa46eb79668607455715e7d7405f0d2fea494a60d3c8a9b6"

  // High level idea:
  // There are 3 main boxes in the protocol, and the others are auxiliary boxes to manage the main boxes
  // Main boxes:
  //   1. Bank box that emits Dexy tokens
  //   2. Liquidity pool (LP) box that allows swapping Dexy with Ergs
  //   3. Oracle (pool) box that has the rate of Erg/USD in R4 (Long) in units nanoErgs per USD

  // initial number of dexy tokens issued
  // used in payout
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
    "updateNFT" -> updateNFT,
    "ballotTokenId" -> ballotTokenId,
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

  val ballotScript = readContract("bank/update/ballot.es")
  val ballotErgoTree = ScriptUtil.compile(Map(), ballotScript)
  val ballotAddress = getStringFromAddress(getAddressFromErgoTree(ballotErgoTree))

  val updateScript = readContract("bank/update/update.es")
  val updateErgoTree = ScriptUtil.compile(Map(), updateScript)
  val updateAddress = getStringFromAddress(getAddressFromErgoTree(updateErgoTree))

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

    val intZero = Base16.encode(ValueSerializer.serialize(IntConstant(0)))
    val longZero = Base16.encode(ValueSerializer.serialize(LongConstant(0)))

    def scanRequest(name: String, nftId: String) = {
      s"""
        |{
        |  "scanName": "$name",
        |  "walletInteraction": "shared",
        |  "removeOffchain": true,
        |  "trackingRule": {
        |    "predicate": "containsAsset",
        |    "assetId": "$nftId"
        |  }
        |}
        |""".stripMargin
    }

    def trackingContractDeploymentRequest(num: Int) = {

      val num95 = Base16.encode(ValueSerializer.serialize(IntConstant(95)))

      val num98 = Base16.encode(ValueSerializer.serialize(IntConstant(98)))

      val num101 = Base16.encode(ValueSerializer.serialize(IntConstant(101)))

      val denum = Base16.encode(ValueSerializer.serialize(IntConstant(100)))

      val falseValue = Base16.encode(ValueSerializer.serialize(BooleanConstant(false)))

      val trueValue = Base16.encode(ValueSerializer.serialize(BooleanConstant(true)))

      val intMaxValue = Base16.encode(ValueSerializer.serialize(IntConstant(Int.MaxValue)))

      val isBelow = if(num < 100) trueValue else falseValue
      val (numValue, trackingNft) = if (num == 95) {
        (num95, tracking95NFT)
      } else if (num == 98) {
        (num98, tracking98NFT)
      } else if (num == 101) {
        (num101, tracking101NFT)
      } else {
        ???
      }

      s"""
        |  [
        |    {
        |      "address": "$trackingAddress",
        |      "value": 1000000000,
        |      "assets": [
        |        {
        |          "tokenId": "$trackingNft",
        |          "amount": 1
        |        }
        |      ],
        |      "registers": {
        |        "R4": "$numValue",
        |        "R5": "$denum",
        |        "R6": "$isBelow",
        |        "R7": "$intMaxValue"
        |      }
        |    }
        |  ]
        |""".stripMargin
    }

    def arbMintDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$arbitrageMintAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$arbitrageMintNFT",
         |          "amount": 1
         |        }
         |      ],
         |      "registers": {
         |        "R4": "$intZero",
         |        "R5": "$longZero"
         |      }
         |    }
         |  ]
         |""".stripMargin
    }

    def freeMintDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$freeMintAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$freeMintNFT",
         |          "amount": 1
         |        }
         |      ],
         |      "registers": {
         |        "R4": "$intZero",
         |        "R5": "$longZero"
         |      }
         |    }
         |  ]
         |""".stripMargin
    }

    def bankContractDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$bankAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$bankNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": $initialDexyTokens
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def buybackContractDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$buybackAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$buybackNFT",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def interventionDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$interventionAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$interventionNFT",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def payoutDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$payoutAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$payoutNFT",
         |          "amount": 1
         |        }
         |      ],
         |      "registers": {
         |        "R4": "$intZero"
         |      }
         |    }
         |  ]
         |""".stripMargin
    }


    def lpSwapDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$lpSwapAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$lpSwapNFT",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def lpMintDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$lpMintAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$lpMintNFT",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def lpRedeemDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$lpRedeemAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$lpRedeemNFT",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def lpExtractDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$extractAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$extractionNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": 1
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def lpDeploymentRequest() = {
      s"""
         |  [
         |    {
         |      "address": "$lpAddress",
         |      "value": 43224547253880,
         |      "assets": [
         |        {
         |          "tokenId": "$lpNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$lpTokenId",
         |          "amount": ${initialLp - 6400000000L}
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": 1000000
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    println("============================Deployment requests===============================")
    println("Oracle pool scan request: ")
    println(scanRequest("Oracle pool", oraclePoolNFT))

    println("Tracking 95% scan request: ")
    println(scanRequest("Tracking 95%", tracking95NFT))
    println("Tracking 95% deployment request: ")
    println(trackingContractDeploymentRequest(95))

    println("Tracking 98% scan request: ")
    println(scanRequest("Tracking 98%", tracking98NFT))
    println("Tracking 98% deployment request: ")
    println(trackingContractDeploymentRequest(98))

    println("Tracking 101% scan request: ")
    println(scanRequest("Tracking 101%", tracking101NFT))
    println("Tracking 101% deployment request: ")
    println(trackingContractDeploymentRequest(101))

    println("Arb mint scan request: ")
    println(scanRequest("Arbitrage mint", arbitrageMintNFT))
    println("Arb mint deployment request: ")
    println(arbMintDeploymentRequest())

    println("Bank scan request: ")
    println(scanRequest("Bank", bankNFT))
    println("Bank contract deployment request: ")
    println(bankContractDeploymentRequest())

    println("Buyback scan request: ")
    println(scanRequest("Buyback", buybackNFT))
    println("Buyback contract deployment request: ")
    println(buybackContractDeploymentRequest())

    println("Free mint scan request: ")
    println(scanRequest("Free mint", freeMintNFT))
    println("Free mint contract deployment request: ")
    println(freeMintDeploymentRequest())

    println("Intervention scan request: ")
    println(scanRequest("Intervention", interventionNFT))
    println("Intervention contract deployment request: ")
    println(interventionDeploymentRequest())

    println("Payout scan request: ")
    println(scanRequest("Payout", payoutNFT))
    println("Payout contract deployment request: ")
    println(payoutDeploymentRequest())

    println("LP swap scan request: ")
    println(scanRequest("LP swap", lpSwapNFT))
    println("LP swap contract deployment request: ")
    println(lpSwapDeploymentRequest())

    println("LP mint scan request: ")
    println(scanRequest("LP mint", lpMintNFT))
    println("LP mint contract deployment request: ")
    println(lpMintDeploymentRequest())

    println("LP redeem scan request: ")
    println(scanRequest("LP redeem", lpRedeemNFT))
    println("LP redeem contract deployment request: ")
    println(lpRedeemDeploymentRequest())

    println("LP extract scan request: ")
    println(scanRequest("LP extract", extractionNFT))
    println("LP extract contract deployment request: ")
    println(lpExtractDeploymentRequest())

    println("LP scan request: ")
    println(scanRequest("LP", lpNFT))
    println("LP contract deployment request: ")
    println(lpDeploymentRequest())
  }
}
