package dexy.chainutils

import dexy.chainutils.ScriptUtil.{getAddressFromErgoTree, getStringFromAddress}
import kiosk.ergo._
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.ergoplatform.ErgoAddressEncoder.{MainnetNetworkPrefix, TestnetNetworkPrefix}
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant, LongConstant}
import sigmastate.basics.DLogProtocol.ProveDlog
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

  val gortDevEmissionNFT: String = "bb484bb7fea08b15861e27cb203a13069082befb05f5437cae71237d9c5c6ac3"

  // GORT / ERG LP
  val gortLpNFT = "d1c9e20657b4e37de3cd279a994266db34b18e6e786371832ad014fd46583198"

  // 3 tokens issued to make parallel execution easier
  val buybackNFT = "610735cbf197f9de67b3628129feaa5a52403286859d140be719467c0fb94328"

  // Dexy LP tokens
  override val lpNFT: String = "905ecdef97381b92c2f0ea9b516f312bfb18082c61b24b40affa6a55555c77c7"
  override val lpSwapNFT: String = "ff7b7eff3c818f9dc573ca03a723a7f6ed1615bf27980ebd4a6c91986b26f801"
  override val lpMintNFT: String = "10b755771f7253cff9727a9ca54bb2867e22b1b236657051c47ea9556c517e10"
  override val lpRedeemNFT: String = "471057efea32bf406d529902217844a258d3d6bedfcdcd3cfbab01872cc0b74c"
  override val lpTokenId: String = "cf74432b2d3ab8a1a934b6326a1004e1a19aec7b357c57209018c4aa35226246"

  override val tracking95NFT: String = "ff5269b5cdd037ea391b7210e28aeae0034ef670b9c4263995fe2a920e8d5a1d"
  override val tracking98NFT: String = "854bb70ed735b6c6a65ca80ce1f10bf217552d2be9ac936091a127f8c6480eaa"
  override val tracking101NFT: String = "4675c1819c3e22add72b73f4b7e83eb743d45013b4ee2d8a63e215de9bc6f57f"

  override val bankNFT: String = "75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"

  override val updateNFT: String = "7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09"

  override val ballotTokenId: String = "3277be793f89bd88706938dd09ad49afe29a62b67b596d54a5fd7e06bf8e71ce"

  override val interventionNFT: String = "6597acef421c21a6468a2b58017df6577b23f00099d9e0772c0608deabdf6d13"

  override val extractionNFT: String = "615be55206b1fea6d7d6828c1874621d5a6eb0e318f98a4e08c94a786f947cec"

  override val arbitrageMintNFT: String = "3fefa1e3fef4e7abbdc074a20bdf751675f058e4bcce5cef0b38bb9460be5c6a"

  override val freeMintNFT: String = "74f906985e763192fc1d8d461e29406c75b7952da3a89dbc83fe1b889971e455"

  override val payoutNFT: String = "26ef992a598eadfddabfd3c51509fb277b075c943b17199407f68c467b9de1ae"

  override val dexyTokenId: String = "6122f7289e7bb2df2de273e09d4b2756cda6aeb0f40438dc9d257688f45183ad"

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
  val gortLpNFT = "043ea12f03769748e436c003886c455ddf1a7cd4aafbd214602822d5213f4e68" // todo: not real

  val buybackNFT = "9b8a5d2d1fff88653a11ce1d697e8e2e603dbfe34cc7124f4c76e5cd45c5bf34" // todo: not real

  val dexyTokenId = "68e52efc3a235006e893afcf642a75d4e1e56f8c324b200a4c16d93216d83832"

  val lpTokenId = "53f62621df1ada5e27f38032610314125395fdddea39064971f51633468a0af0"

  // tokens for main boxes
  val bankNFT = "764eeeb81d8f6c566d7abae113ffe558ab86a4c10277800e958a017c86345c78"
  val lpNFT = "6873424faf94dad45f54d20793dc6214026ab68bd3309b46b5695243174efafa"

  // update tokens
  val updateNFT = "c7894e6bf1654c321d1bfb1576ba62330f0c5f671142ca9221e9515b3af4d123"
  val ballotTokenId = "0c4febf15f39966e66fa057123b1439c4cd19229cb2b5b526cd74a5304d4bd20"

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT = "0734f92848b3c0aa887f976cb2b43c412ecb98612fcd172b468861c01d1c64a0"
  val freeMintNFT = "9a46aaf31a0c7410d86481240804932417238788dbc5f8478de6d07182cd3be6"
  val arbitrageMintNFT = "e6a6a03862f94c77d7535dd5492f0934fbc9d89f1689bb4be2d215f0db3342a0"
  val payoutNFT = "3f44eeaaf64aea3c39b64c49f00f24c9341e236d989ba65710b206a7a17de5c9"

  val lpSwapNFT = "30e641b65fe4f726693a5aee3f465094baf37a8453530c7d8c749e5d501c64dd"
  val lpMintNFT = "c6d2b46536607bfdf9ae374786ea2e95a1b62a237998ad2a59b5b1ffbc976ccc"
  val lpRedeemNFT = "55d05e29148780cab10fec92c28e4b7b88a0de3b264d229a76d501d1faae2881"
  val extractionNFT = "228f4c97e1857fe2124feebfc521fd2986190f839412cb83aceb8ffd65238192"

  // should be reissued every time!
  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT = "3e90605cd3db3d72fb34bf5ae1ebb15537cfe36b40ec81014d0e57cf8836c962"
  val tracking98NFT = "b94d36be8fe53bc07c3c53a2ee892e8398e8565346e1ea4bf42575f00fe7149f"
  val tracking101NFT = "f14b42ab7a8ff1ba2e2b7056e27cd9c7e018c355499c385850db7f34da881431"
}

object DexySpec extends ContractUtils {

  // todo: for tests, use
  // import TestnetTokenIds._
  // val networkPrefix = MainnetNetworkPrefix
  import MainnetTokenIds._
  val networkPrefix = MainnetNetworkPrefix

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

  val feeNumLp = 997
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

  val bankUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("bankNFT"))
  val bankUpdateErgoTree = ScriptUtil.compile(Map(), bankUpdateScript)
  val bankUpdateAddress = getStringFromAddress(getAddressFromErgoTree(bankUpdateErgoTree))

  val extractUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("extractionNFT"))
  val extractUpdateErgoTree = ScriptUtil.compile(Map(), extractUpdateScript)
  val extractUpdateAddress = getStringFromAddress(getAddressFromErgoTree(extractUpdateErgoTree))

  val interventionUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("interventionNFT"))
  val interventionUpdateErgoTree = ScriptUtil.compile(Map(), interventionUpdateScript)
  val interventionUpdateAddress = getStringFromAddress(getAddressFromErgoTree(interventionUpdateErgoTree))

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

    def scanRequest(name: String, nftId: String): String = {
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

    def gortDevEmissionDeploymentRequest(): String = {
      val initLastPaymentHeight = if (networkPrefix == MainnetNetworkPrefix) {
        1200000  // mainnet value
      } else {
        1000000  // testnet value
      }
      val initLastPaymentHeightEncoded = Base16.encode(ValueSerializer.serialize(IntConstant(initLastPaymentHeight)))

      // todo: revisit before mainnnet deployment
      val eae = new ErgoAddressEncoder(ErgoAddressEncoder.MainnetNetworkPrefix)
      val devp2pk = "9hUzb5RvSgDqJdtyCN9Ke496Yy63mpcUJKbRq4swzQ5EQKgygKT"
      val lock = ProveDlog(eae.fromString(devp2pk).get.asInstanceOf[P2PKAddress].pubkey.value)
      val lockEncoded = Base16.encode(ValueSerializer.serialize(lock))

      s"""
         |  [
         |    {
         |      "address": "$gortDevEmissionAddress",
         |      "value": 1000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$gortDevEmissionNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$gort",
         |          "amount": 1
         |        }
         |      ],
         |      "registers": {
         |        "R4": "$initLastPaymentHeightEncoded",
         |        "R5": "$lockEncoded"
         |      }
         |    }
         |  ]
         |""".stripMargin
    }

    def trackingContractDeploymentRequest(num: Int): String = {

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

    def arbMintDeploymentRequest(): String = {
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

    def freeMintDeploymentRequest(): String = {
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

    def bankContractDeploymentRequest(): String = {
      s"""
         |  [
         |    {
         |      "address": "$bankAddress",
         |      "value": 100000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$bankNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": ${initialDexyTokens - 8000}
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def buybackContractDeploymentRequest(): String = {
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

    def interventionDeploymentRequest(): String = {
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

    // 10 ERG to protect box from storage rent
    def payoutDeploymentRequest(): String = {
      s"""
         |  [
         |    {
         |      "address": "$payoutAddress",
         |      "value": 10000000000,
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


    def lpSwapDeploymentRequest(): String = {
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

    def lpMintDeploymentRequest(): String = {
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

    def lpRedeemDeploymentRequest(): String = {
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

    def lpExtractDeploymentRequest(): String = {
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


    def lpDeploymentRequest(): String = {
      s"""
         |  [ // todo: recheck values on deployment
         |    {
         |      "address": "$lpAddress",
         |      "value": 1000000000000,
         |      "assets": [
         |        {
         |          "tokenId": "$lpNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$lpTokenId",
         |          "amount": ${initialLp - 1000000L}
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": 7813
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

    println("===================== GORT dev emission related requests ====================")

    println("GORT dev emission scan request: ")
    println(scanRequest("GORT dev emission ", gortDevEmissionNFT))
    println("GORT dev emission deployment request: ")
    println(gortDevEmissionDeploymentRequest())
  }
}