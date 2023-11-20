package dexy

import dexy.ScriptUtil.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import org.ergoplatform.ErgoAddressEncoder.{MainnetNetworkPrefix, TestnetNetworkPrefix}
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant, LongConstant}
import sigmastate.serialization.ValueSerializer

trait NetworkTokenIds {
  // oracle related tokens
  // take token IDs below from oracle pool UI
  // Gold Oracle Reward Token
  val gort: String
  val oracleTokenId: String
  val oraclePoolNFT: String

  // GORT dev emission contract NFT
  val gortDevEmissionNFT: String

  // GORT / ERG LP
  val gortLpNFT: String

  val buybackNFT: String

  val dexyTokenId: String

  val lpTokenId: String

  // tokens for main boxes
  val bankNFT: String
  val lpNFT: String

  // update tokens
  val updateNFT: String
  val ballotTokenId: String

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT: String
  val freeMintNFT: String
  val arbitrageMintNFT: String
  val payoutNFT: String

  val lpSwapNFT: String
  val lpMintNFT: String
  val lpRedeemNFT: String
  val extractionNFT: String

  // should be reissued every time!
  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT: String
  val tracking98NFT: String
  val tracking101NFT: String
}

object MainnetTokenIds extends NetworkTokenIds {

  // oracle related tokens
  // take token IDs below from oracle pool UI
  // Gold Oracle Reward Token
  val gort = "7ba2a85fdb302a181578b1f64cb4a533d89b3f8de4159efece75da41041537f9"
  val oracleTokenId = "6183680b1c4caaf8ede8c60dc5128e38417bc5b656321388b22baa43a9d150c2"
  val oraclePoolNFT = "3c45f29a5165b030fdb5eaf5d81f8108f9d8f507b31487dd51f4ae08fe07cf4a"

  val gortDevEmissionNFT: String = ???

  // GORT / ERG LP
  val gortLpNFT = "d1c9e20657b4e37de3cd279a994266db34b18e6e786371832ad014fd46583198"

  val buybackNFT = "119a068a0119670de8a5d2467da33df572903c64aaa7b6ea4c9668ef0cfe0325"
  
  override val dexyTokenId: String = ???
  override val lpTokenId: String = ???
  override val bankNFT: String = ???
  override val lpNFT: String = ???
  override val updateNFT: String = ???
  override val ballotTokenId: String = ???
  override val interventionNFT: String = ???
  override val freeMintNFT: String = ???
  override val arbitrageMintNFT: String = ???
  override val payoutNFT: String = ???
  override val lpSwapNFT: String = ???
  override val lpMintNFT: String = ???
  override val lpRedeemNFT: String = ???
  override val extractionNFT: String = ???
  override val tracking95NFT: String = ???
  override val tracking98NFT: String = ???
  override val tracking101NFT: String = ???
}

object TestnetTokenIds extends NetworkTokenIds {

  // oracle related tokens
  // take token IDs below from oracle pool UI
  // Gold Oracle Reward Token
  val gort = "01510156b109cd66c41a703c9911925ab305e4fe2bdc898680ad255c6972c404"
  val oracleTokenId = "001e182cc3f04aec4486c7a5018d198e9591a7cfb0b372f5f95fa3e5ddbd24d3"
  val oraclePoolNFT = "d94bfac40b516353983443209104dcdd5b7ca232a01ccb376ee8014df6330907"

  val gortDevEmissionNFT: String = "d94bfac40b516353983443209104dcdd5b7ca232a01ccb376ee8014df6330907" // todo: not real

  // GORT / ERG LP
  val gortLpNFT = "043ea12f03769748e436c003886c455ddf1a7cd4aafbd214602822d5213f4e68"

  val buybackNFT = "0158bb202fdf8eacdd4b08e972776077284e5a69708af719669e6f65ceeaf809"

  val dexyTokenId = "0160b869f30a5424e59cb3453e8a726b81fe83761d02ab41829cb7b2e4b624bc"

  val lpTokenId = "01be480a89df53f3c88c08478e8433f388130c8845630e87016809e1791ea57f"

  // tokens for main boxes
  val bankNFT = "0226551966f8ce4be3beb8c55892ca32076a7eaf3632c473a3094c337eca6344"
  val lpNFT = "024e4ff49356d696d5da6a5c2b1408d24f9074cb38257871524dd8d5915ab636"

  // update tokens
  val updateNFT = "a1cf4b74becdcfa26e132ff950ed38c2d8b9f0f05048c39da995baf7a4f3766d"
  val ballotTokenId = "027b2daff5513c9b10b719e64948f4685df330dd493cbe8931c8853d1bc9e8f2"

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "02892b9bbdf4575b557648972e75148fbbf4ff52751ee6c5718f3ce55fd3b61a"
  val freeMintNFT = "02aa17b1d1c542b0f6ac32b8cd894e36e2701fc15b83765babc2f1bcce779db2"
  val arbitrageMintNFT = "030bffaf2d9603c7d05c1887c67cfae9a8902a4c1d6bdd42bd6e394251a92650"
  val payoutNFT = "037dbf9880f9b3588dad0955533e1e1be93432f657cd6618dd91a456d08d28d2"

  val lpSwapNFT = "04ba758620d5e0d54cd4db52bf71834f4f6aad06be47ac7e025a1f5b666ab437"
  val lpMintNFT = "04eb5c28222ca3158f7dbfe9f7a6a80400ebd0058231b3dd7617643d01dd1172"
  val lpRedeemNFT = "04efcc96ec0519de9228d47e9a20f5fc07ae6acf2befff5e30ec51d0c8ffca9e"
  val extractionNFT = "04ff942b4ea09322087a77fdb7d1308e005bc9f56b0361974cc786f298106794"

  // should be reissued every time!
  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT = "050c7e944a0d4a918f5f8f501f888c683568b1a11c49b45f9db33ee668085661"
  val tracking98NFT = "0583370b6d9f82a06f2c58564be4ae5f5112957056a8806a54ab883dad455467"
  val tracking101NFT = "0587c06837859ba8f035cab9ae975491560750cb0f2eccb95c31dfb43782e7d5"
}

object DexySpec extends ContractUtils {

  import TestnetTokenIds._
  val networkPrefix = TestnetNetworkPrefix

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
    "gortDevEmissionNFT" -> gortDevEmissionNFT,
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

  override val defaultSubstitutionMap = nftDictionary


  // GORT-related scripts

  // GORT dev emission script
  val gortDevEmissionScript = readContract("gort-dev/emission.es")
  val gortDevEmissionErgoTree = ScriptUtil.compile(Map(), gortDevEmissionScript)
  val gortDevEmissionAddress = getStringFromAddress(getAddressFromErgoTree(gortDevEmissionErgoTree))

  // arbitrage mint box
  val arbitrageMintScript = readContract("bank/arbmint.es")

  // free mint box
  val freeMintScript = readContract("bank/freemint.es")

  // payout box
  val payoutScript = readContract("bank/payout.es")

  // below contract is adapted from N2T DEX contract in EIP-14 https://github.com/ergoplatform/eips/blob/de30f94ace1c18a9772e1dd0f65f00caf774eea3/eip-0014.md?plain=1#L558-L636
  lazy val lpScript = readContract("lp/pool/main.es")

  val lpSwapScript = readContract("lp/pool/swap.es")

  val lpMintScript = readContract("lp/pool/mint.es")

  val lpRedeemScript = readContract("lp/pool/redeem.es")

  val extractScript = readContract("lp/pool/extract.es")

  val trackingScript = readContract("tracking.es")

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

  // proxy contracts (used in tests only)
  val lpSwapBuyV1Script = readContract("lp/proxy/SwapBuyV1.es")
  val lpSwapBuyV1ErgoTree = ScriptUtil.compile(Map(), lpSwapBuyV1Script)
  val lpSwapBuyV1Address = getStringFromAddress(getAddressFromErgoTree(lpSwapBuyV1ErgoTree))

  val lpSwapSellV1Script = readContract("lp/proxy/SwapSellV1.es")
  val lpSwapSellV1ErgoTree = ScriptUtil.compile(Map(), lpSwapSellV1Script)
  val lpSwapSellV1Address = getStringFromAddress(getAddressFromErgoTree(lpSwapSellV1ErgoTree))

  def main(args: Array[String]): Unit = {
    println(s"Gort dev emission: $gortDevEmissionAddress")
    println(gortDevEmissionScript)
    println()

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
         |        },
         |        {
         |          "tokenId": "$gort",
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
