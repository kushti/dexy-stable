package oracles

import dexy.{Common, DexySpec}
import dexy.DexySpec.gort
import kiosk.ergo.{DhtData, KioskBox, KioskGroupElement, KioskInt, KioskLong}
import kiosk.tx.TxUtil
import oracles.OracleContracts.config
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, ErgoToken, ErgoValue, InputBox}
import kiosk.ErgoUtil.{addressToGroupElement => addr2Grp}
import kiosk.encoding.ScalaErgoConverters.{stringToGroupElement => str2Grp}
import special.sigma.GroupElement

trait OracleHelpers extends Common {

  private lazy val keyPairs: Array[(KioskGroupElement, BigInt)] = Array(
    "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx" -> "37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0",
    "9f9q6Hs7vXZSQwhbrptQZLkTx15ApjbEkQwWXJqD2NpaouiigJQ" -> "5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d",
    "9fGp73EsRQMpFC7xaYD5JFy2abZeKCUffhDBNbQVtBtQyw61Vym" -> "3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e",
    "9fSqnSHKLzRz7sRkfwNW4Rqtmig2bHNaaspsQms1gY2sU6LA2Ng" -> "148bb91ada6ad5e6b1bba02fe70ecd96095e00cbaf0f1f9294f02fedf9855ea0",
    "9g3izpikC6xuvhnXxNHT1y5nwJNofMsoPiCgr4JXcZV6GUgWPqh" -> "148bb91ada6ad5e6b1bba02fe70ecd96095e00cbaf0f1f9294f02fedf9855ea1",
    "9g1RasRmLpijKSD1TWuGnCEmBfacRzGANqwHPKXNPcSQypsHCT5" -> "009b74e570880f0a558ed1231c280aff9d8afb2ba238e4ac2ea2d4f5507f01c6ae"
  ).map { case (address, secret) => KioskGroupElement(str2Grp(addr2Grp(address))) -> BigInt(secret, 16) }

  val pubKey1: KioskGroupElement = keyPairs(0)._1
  val pubKey2: KioskGroupElement = keyPairs(1)._1
  val pubKey3: KioskGroupElement = keyPairs(2)._1
  val pubKey4: KioskGroupElement = keyPairs(3)._1
  val pubKey5: KioskGroupElement = keyPairs(4)._1
  val pubKey6: KioskGroupElement = keyPairs(5)._1

  val privKey1: BigInt = keyPairs(0)._2
  val privKey2: BigInt = keyPairs(1)._2
  val privKey3: BigInt = keyPairs(2)._2
  val privKey4: BigInt = keyPairs(3)._2
  val privKey5: BigInt = keyPairs(4)._2
  val privKey6: BigInt = keyPairs(5)._2

  val dummyTxId = "f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809"
  val dummyBoxId = "5267556B58703273357638792F413F4428472B4B6250655368566D5971337436"
  val dummyIndex = 1.toShort

  val dummyEpochCounter = 1

  val rewardTokenId = DexySpec.gort

  val dummyScript = "sigmaProp(true)"
  val junkAddress = "4MQyML64GnzMxZgm" // sigmaProp(true)
  val dummyNanoErgs = 10000000000000L

  def dummyFundingBox(implicit ctx: BlockchainContext) =
    ctx // for funding transactions
      .newTxBuilder()
      .outBoxBuilder
      .value(dummyNanoErgs)
      // .tokens(new ErgoToken(rewardTokenId, 1000000L))
      .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
      .build()
      .convertToInputWith(dummyTxId, dummyIndex)

  val defaultGortSupply = 1000000000L

  def bootstrapOracleBox(pubKey: KioskGroupElement,
                         rewardTokenQty: Long,
                         optRewardTokenId: Option[String] = None,
                         optOracleAddress: Option[String] = None,
                         optOracleTokenId: Option[String] = None)
                        (implicit ctx: BlockchainContext) =
    TxUtil
      .createTx(
        Array(
          ctx // for funding transactions
            .newTxBuilder()
            .outBoxBuilder
            .value(dummyNanoErgs)
            .tokens(new ErgoToken(optOracleTokenId.getOrElse(config.oracleTokenId), 1),
                    new ErgoToken(optRewardTokenId.getOrElse(rewardTokenId), rewardTokenQty))
            .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
            .build()
            .convertToInputWith(dummyTxId, dummyIndex)),
        Array[InputBox](),
        Array(
          KioskBox(
            optOracleAddress.getOrElse(OracleContracts.oracleAddress),
            value = minStorageRent,
            registers = Array(pubKey),
            tokens = Array((optOracleTokenId.getOrElse(config.oracleTokenId), 1), (optRewardTokenId.getOrElse(rewardTokenId), rewardTokenQty))
          )),
        fee = 1000000L,
        changeAddress,
        Array[String](),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)

  private def getOracleTokensToAdd(rewardTokenQty: Long, customRewardTokenId: Option[String], optOracleTokenId: Option[String] = None): Array[(String, Long)] = {
    val oracleTokenIdToUse = optOracleTokenId.getOrElse(config.oracleTokenId)
    if (rewardTokenQty > 0) Array((oracleTokenIdToUse, 1L), (customRewardTokenId.getOrElse(rewardTokenId), rewardTokenQty)) else Array((oracleTokenIdToUse, 1L))
  }

  def createDataPoint(dataPointValue: Long,
                      epochCounter: Int,
                      outAddress: String,
                      outValue: Long,
                      inputOracleBox: InputBox,
                      privKey: BigInt,
                      contextVarOutIndex: Int,
                      rewardTokenQty: Long,
                      newPubKey: Option[KioskGroupElement] = None,
                      customCreationHeight: Option[Int] = None,
                      customRewardTokenId: Option[String] = None,
                      customOracleTokenId: Option[String] = None)(implicit ctx: BlockchainContext) = {
    TxUtil
      .createTx(
        Array(inputOracleBox.withContextVars(new ContextVar(0, KioskInt(contextVarOutIndex).getErgoValue)), dummyFundingBox),
        Array[InputBox](),
        Array(
          KioskBox(
            outAddress,
            value = outValue,
            registers = Array(
              newPubKey.getOrElse(KioskGroupElement(inputOracleBox.getRegisters.get(0).asInstanceOf[ErgoValue[GroupElement]].getValue)),
              KioskInt(epochCounter),
              KioskLong(dataPointValue)
            ),
            tokens = getOracleTokensToAdd(rewardTokenQty, customRewardTokenId, customOracleTokenId),
            creationHeight = customCreationHeight
          )),
        1500000,
        changeAddress,
        Array[String](privKey.toString),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)
  }


  def bootstrapPoolBox(customCreationHeight: Int,
                       rate: Long,
                       counter: Int = 0,
                       newPoolNFT: Option[String] = None,
                       optRewardTokenQty: Option[Long] = None,
                       optRewardTokenId: Option[String] = None)
                      (implicit ctx: BlockchainContext) =
    TxUtil
      .createTx(
        Array(
          ctx // for funding transactions
            .newTxBuilder()
            .outBoxBuilder
            .value(dummyNanoErgs)
            .tokens(
              new ErgoToken(newPoolNFT.getOrElse(config.poolNFT), 1),
              new ErgoToken(optRewardTokenId.getOrElse(rewardTokenId), optRewardTokenQty.getOrElse(defaultGortSupply))
            )
            .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
            .build()
            .convertToInputWith(dummyTxId, dummyIndex)),
        Array[InputBox](),
        Array(
          KioskBox(
            OracleContracts.poolAddress,
            value = minStorageRent,
            registers = Array(KioskLong(rate), KioskInt(counter)),
            tokens = Array(
              (newPoolNFT.getOrElse(config.poolNFT), 1),
              (optRewardTokenId.getOrElse(gort), optRewardTokenQty.getOrElse(defaultGortSupply))
            ),
            creationHeight = Some(customCreationHeight)
          )),
        fee = 1000000L,
        changeAddress,
        Array[String](),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)

  def bootstrapRefreshBox(optRefreshAddress: Option[String] = None, optRefreshNFT: Option[String] = None)(implicit ctx: BlockchainContext) =
    TxUtil
      .createTx(
        Array(
          ctx // for funding transactions
            .newTxBuilder()
            .outBoxBuilder
            .value(dummyNanoErgs)
            .tokens(new ErgoToken(optRefreshNFT.getOrElse(config.refreshNFT), 1))
            .contract(ctx.compileContract(ConstantsBuilder.empty(), dummyScript))
            .build()
            .convertToInputWith(dummyTxId, dummyIndex)),
        Array[InputBox](),
        Array(
          KioskBox(
            optRefreshAddress.getOrElse(OracleContracts.refreshAddress),
            value = minStorageRent,
            registers = Array.empty,
            tokens = Array((optRefreshNFT.getOrElse(config.refreshNFT), 1))
          )),
        fee = 1000000L,
        changeAddress,
        Array[String](),
        Array[DhtData](),
        false
      )
      .getOutputsToSpend
      .get(0)

}
