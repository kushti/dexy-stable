package dexy

import dexy.ScriptUtil.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant, LongConstant}
import sigmastate.serialization.ValueSerializer

object DexySpec {
  // oracle related tokens
  val dexyTokenId = "8ace0552ad1d6b054bc3ff8e04abd0c0b3b6c3877fc69f23c3ebd87d2c7d6667"

  val lpTokenId = "6d64db47940f30deb37ad1ea9b34cac0c4a631a5bdeccb918b2773022041adaa"

  // Gold Oracle Reward Token
  val gort = "e2636c9f0e32886954ab1f87ac2e016fdf53d63d8fa2101530d1e31ac59e365f"
  val oracleTokenId = "cc8d50d2a9f0f1254f363ee66a3c65e73ea8046386c001544b08841743df411d"
  val oraclePoolNFT = "f2f64bab1f2e9ab65c0e4a6a65eed0b26c5276eb5dda01d4a0e0ebe8a85bfbfc"

  // GORT / ERG LP
  val gortLpNFT = "2f51b13d06f4e599cbfd74493701e7f3cdba32c58c567f01ccf1d20f0405d607"

  // tokens for main boxes
  val bankNFT = "d9275315fef8d97e787109d6381172184419ae91a31224b0cbe90e32ac863de8"
  val lpNFT = "e7d59d5a70fdb4795c19677f3ac3e52a95e79c422f94a3e24434be9b8378f199"

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "8da8339cdadc4190f5406dd95ded5195014e374ffa1087bf5762ec2c1324f24f"
  val freeMintNFT = "049b59a66d1a8a24aaf86e1b0c455c8ab30628da6b0ea9f67567410cc0c3c658"
  val arbitrageMintNFT = "a06fa7ae18d21d13cc776fdc51dbd6d6d46912994cbaeb6ce880d09ffb536428"
  val buybackNFT = "f9de3236f3d431ccd7efe23c485602affb1e55ef3d57a1502105c91e49956334"
  val payoutNFT = "c811a10d4a22eaf6738434490f7b53740d09988a661ff20ed00bb71000dbc2a9"

  val lpSwapNFT = "51033a3e651e3d8930f3a065509b201f098755b3e158fc4b9f777c51307a2e14"
  val lpMintNFT = "9e48fd37c5763d587c0ced81d1ec9d327a63eb500814ca1d80009771d2f45af5"
  val lpRedeemNFT = "dfa5cbc6131bfb6bdda0a35ecf3ba78906749a85e5e8c5e3e73639bb27583e24"
  val extractionNFT = "4ed32ee7fe1236564010a006a72d875da8176d7600e6e17609c0c8e5d8dbfea2"

  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT = "6bcef8f1493353797a4d60060e100efd464f10cdd1479ecf5b77031b5f93f39c"
  val tracking98NFT = "e5a723cfcb7c6b6609ca4979f107b78fd2905eafbb8264835f52474de291a675"
  val tracking101NFT = "4d5369a82f2aaf432a573cc0cba9cf5eb545ed9444d20543eba69bafffd3c57f"

  // High level idea:
  // There are 3 main boxes in the protocol, and the others are auxiliary boxes to manage the main boxes
  // Main boxes:
  //   1. Bank box that emits Dexy tokens
  //   2. Liquidity pool (LP) box that allows swapping Dexy with Ergs
  //   3. Oracle (pool) box that has the rate of Erg/USD in R4 (Long) in units nanoErgs per USD

  // initial number of dexy tokens issued
  // used in payout
  lazy val initialDexyTokens = 5000000000000L

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
        |        "R4": "$denum",
        |        "R5": "$numValue",
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
         |        },
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
