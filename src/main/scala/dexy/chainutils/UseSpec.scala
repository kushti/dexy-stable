package dexy.chainutils

import org.ergoplatform.kiosk.ergo._
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.ergoplatform.ErgoAddressEncoder.MainnetNetworkPrefix
import scorex.crypto.encode.Base16
import scorex.util.encode.Base64
import sigmastate.Values.{BooleanConstant, IntConstant, LongConstant}
import sigmastate.crypto.DLogProtocol.ProveDlog
import sigmastate.serialization.ValueSerializer

/**
 * Mainnet deployment data for USE (DexyUSD)
 */
object MainnetUseTokenIds extends NetworkTokenIds {

  // oracle related tokens
  // take token IDs below from oracle pool UI

  // DORT
  val gort = "ae399fcb751e8e247d0da8179a2bcca2aa5119fff9c85721ffab9cdc9a3cb2dd"

  // USD oracle pool data
  val oracleTokenId = "74fa4aee3607ceb7bdefd51a856861b5dbfa434a8f6c93bfe967de8ed1a30a78"
  val oraclePoolNFT = "6a2b821b5727e85beb5e78b4efb9f0250d59cd48481d2ded2c23e91ba1d07c66"

  val gortDevEmissionNFT: String = "" // no emission for USD oracle

  // GORT / ERG LP
  val gortLpNFT = "35bc71897cd44d1a624285c54a0be66b69d1c61674603ed89dfe136f32035f0e"

  // 3 tokens issued to make parallel execution easier
  val buybackNFT = "dcce07af04ea4f9b7979336476594dc16321547bcc9c6b95a67cb1a94192da4f"

  // Dexy LP tokens
  override val lpNFT: String = "4ecaa1aac9846b1454563ae51746db95a3a40ee9f8c5f5301afbe348ae803d41"
  override val lpSwapNFT: String = "ef461517a55b8bfcd30356f112928f3333b5b50faf472e8374081307a09110cf"
  override val lpMintNFT: String = "2cf9fb512f487254777ac1d086a55cda9e74a1009fe0d30390a3792f050de58f"
  override val lpRedeemNFT: String = "1bfea21924f670ca5f13dd6819ed3bf833ec5a3113d5b6ae87d806db29b94b9a"
  override val lpTokenId: String = "804a66426283b8281240df8f9de783651986f20ad6391a71b26b9e7d6faad099"

  override val tracking95NFT: String = "57af5c7446d419e98e2e6fbd4bce9029cd589f8094686c457902feb472f194ec"
  override val tracking98NFT: String = "47472f675d7791462520d78b6c676e65c23b7c11ca54d73d3e031aadb5d56be2"
  override val tracking101NFT: String = "fec586b8d7b92b336a5fea060556cbb4ced15d5334dcb7ca9f9a7bb6ca866c42"

  override val bankNFT: String = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"

  override val updateNFT: String = "f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883"

  override val ballotTokenId: String = "a67d769e70b98e56e81de78fb8dcc689e037754932da67edf49bab420ec8986e"

  override val interventionNFT: String = "dbf655f0f6101cb03316e931a689412126fefbfb7c78bd9869ad6a1a58c1b424"

  override val extractionNFT: String = "bc685d6ad1703ba5775736308fd892807edc04f48ba7a52e802fab241a59962c"

  override val arbitrageMintNFT: String = "c79bef6fe21c788546beab08c963999d5ef74151a9b7fd6c1843f626eea0ecf5"

  override val freeMintNFT: String = "40db16e1ed50b16077b19102390f36b41ca35c64af87426d04af3b9340859051"

  override val payoutNFT: String = "a2482fca4ca774ef9d3896977e3677b031597c6e312b0c10d47157bb0d6ed69f"

  // USE token
  override val dexyTokenId: String = "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669"

}


object UseSpec extends ContractUtils {

  import MainnetUseTokenIds._
  val networkPrefix = MainnetNetworkPrefix

  val scriptUtil = new ScriptUtil(networkPrefix)
  import scriptUtil._

  // High level idea:
  // There are 3 main boxes in the protocol, and the others are auxiliary boxes to manage the main boxes
  // Main boxes:
  //   1. Bank box that emits Dexy tokens
  //   2. Liquidity pool (LP) box that allows swapping Dexy with Ergs
  //   3. Oracle (pool) box that has the rate of Erg/USD in R4 (Long) in units nanoErgs per USD

  // initial number of dexy tokens issued
  // used in payout
  lazy val initialDexyTokens = 1000000000000000000L

  val initialLp = 9223372036854775000L // initially how many Lp minted (and we need to add that many to tokens(1), after removing some for token burning as in UniSwap v2)

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
  ).map{case (k, hex) => k -> Base64.encode(hex.decodeHex)} ++ Map(
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
  val gortDevEmissionErgoTree = scriptUtil.compile(Map(), gortDevEmissionScript)
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

  val trackingErgoTree = scriptUtil.compile(Map(), trackingScript)
  val trackingAddress = getStringFromAddress(getAddressFromErgoTree(trackingErgoTree))

  val bankScript = readContract("bank/bank.es")
  val bankErgoTree = scriptUtil.compile(Map(), bankScript)
  val bankAddress = getStringFromAddress(getAddressFromErgoTree(bankErgoTree))

  val ballotScript = readContract("bank/update/ballot.es")
  val ballotErgoTree = scriptUtil.compile(Map(), ballotScript)
  val ballotAddress = getStringFromAddress(getAddressFromErgoTree(ballotErgoTree))

  val bankUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("bankNFT"))
  val bankUpdateErgoTree = scriptUtil.compile(Map(), bankUpdateScript)
  val bankUpdateAddress = getStringFromAddress(getAddressFromErgoTree(bankUpdateErgoTree))

  val extractUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("extractionNFT"))
  val extractUpdateErgoTree = scriptUtil.compile(Map(), extractUpdateScript)
  val extractUpdateAddress = getStringFromAddress(getAddressFromErgoTree(extractUpdateErgoTree))

  val interventionUpdateScript = readContract("bank/update/update.es", "contractToUpdateNFT" -> defaultSubstitutionMap("interventionNFT"))
  val interventionUpdateErgoTree = scriptUtil.compile(Map(), interventionUpdateScript)
  val interventionUpdateAddress = getStringFromAddress(getAddressFromErgoTree(interventionUpdateErgoTree))

  val arbitrageMintErgoTree = scriptUtil.compile(Map(), arbitrageMintScript)
  val arbitrageMintAddress = getStringFromAddress(getAddressFromErgoTree(arbitrageMintErgoTree))
  val freeMintErgoTree = scriptUtil.compile(Map(), freeMintScript)
  val freeMintAddress = getStringFromAddress(getAddressFromErgoTree(freeMintErgoTree))
  val payoutErgoTree = scriptUtil.compile(Map(), payoutScript)
  val payoutAddress = getStringFromAddress(getAddressFromErgoTree(payoutErgoTree))

  val interventionScript = readContract("bank/intervention.es")
  val interventionErgoTree = scriptUtil.compile(Map(), interventionScript)
  val interventionAddress = getStringFromAddress(getAddressFromErgoTree(interventionErgoTree))

  val buybackScript = readContract("bank/buyback.es")
  val buybackErgoTree = scriptUtil.compile(Map(), buybackScript)
  val buybackAddress = getStringFromAddress(getAddressFromErgoTree(buybackErgoTree))


  val lpErgoTree = scriptUtil.compile(Map(), lpScript)
  val lpAddress = getStringFromAddress(getAddressFromErgoTree(lpErgoTree))
  val lpSwapErgoTree = scriptUtil.compile(Map(), lpSwapScript)
  val lpSwapAddress = getStringFromAddress(getAddressFromErgoTree(lpSwapErgoTree))
  val lpMintErgoTree = scriptUtil.compile(Map(), lpMintScript)
  val lpMintAddress = getStringFromAddress(getAddressFromErgoTree(lpMintErgoTree))
  val lpRedeemErgoTree = scriptUtil.compile(Map(), lpRedeemScript)
  val lpRedeemAddress = getStringFromAddress(getAddressFromErgoTree(lpRedeemErgoTree))
  val extractErgoTree = scriptUtil.compile(Map(), extractScript)
  val extractAddress = getStringFromAddress(getAddressFromErgoTree(extractErgoTree))

  // proxy contracts (used in tests only)
  val lpSwapBuyV1Script = readContract("lp/proxy/SwapBuyV1.es")
  val lpSwapBuyV1ErgoTree = scriptUtil.compile(Map(), lpSwapBuyV1Script)
  val lpSwapBuyV1Address = getStringFromAddress(getAddressFromErgoTree(lpSwapBuyV1ErgoTree))

  val lpSwapSellV1Script = readContract("lp/proxy/SwapSellV1.es")
  val lpSwapSellV1ErgoTree = scriptUtil.compile(Map(), lpSwapSellV1Script)
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
         |      "value": 10000000000,
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
      val ergsToPut = 395158 / 2
      val nanoErgsToPut = ergsToPut * 1000000000L
      val useToPut = Math.round(ergsToPut * 0.538 * 1000.0)
      println("useToPut: " + useToPut)
      val lpToTake = Math.sqrt(nanoErgsToPut.toDouble * useToPut.toDouble).toLong

      println("lpToTake: " + lpToTake)

      s"""
         |  [
         |    {
         |      "address": "$lpAddress",
         |      "value": ${nanoErgsToPut},
         |      "assets": [
         |        {
         |          "tokenId": "$lpNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$lpTokenId",
         |          "amount": ${initialLp - lpToTake}
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": ${useToPut}
         |        }
         |      ]
         |    }
         |  ]
         |""".stripMargin
    }

    def bankContractDeploymentRequest(): String = {
      val ergsToPut = 395158 / 2 + 18000
      val nanoErgsToPut = ergsToPut * 1000000000L

      s"""
         |  [
         |    {
         |      "address": "$bankAddress",
         |      "value": ${nanoErgsToPut},
         |      "assets": [
         |        {
         |          "tokenId": "$bankNFT",
         |          "amount": 1
         |        },
         |        {
         |          "tokenId": "$dexyTokenId",
         |          "amount": ${999999999883982777L}
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