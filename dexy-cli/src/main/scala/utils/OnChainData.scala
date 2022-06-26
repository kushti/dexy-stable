package utils

import ergoplatform.dexy.{DexyToken}
import io.circe.Json
import org.ergoplatform.appkit.{Address, BlockchainContext, InputBox}

import scala.collection.JavaConverters._

object OnChainData {
  def getLastOracleBox(ctx: BlockchainContext): InputBox = {
    val oracleBoxJson = Explorer.getUnspentTokenBoxes(DexyToken.oraclePoolNFT)
    val lastOracleBoxId = oracleBoxJson.hcursor.downField("items").as[List[Json]]
      .getOrElse(throw new Exception("couldn't parse oracle box")).head
      .hcursor.downField("boxId").as[String].getOrElse("")
    ctx.getBoxesById(lastOracleBoxId).head
  }

  def getLastBankBox(ctx: BlockchainContext): InputBox = {
    val bankBoxJson = Explorer.getUnspentTokenBoxes(DexyToken.bankNFT)
    val bankBoxId = bankBoxJson.hcursor.downField("items").as[List[Json]]
      .getOrElse(throw new Exception("couldn't parse bank box")).head
      .hcursor.downField("boxId").as[String].getOrElse("")
    ctx.getBoxesById(bankBoxId).head
  }

  def getLastFreeMintBox(ctx: BlockchainContext): InputBox = {
    val freeMintBoxJson = Explorer.getUnspentTokenBoxes(DexyToken.freeMintNFT)
    val freeMintBoxId = freeMintBoxJson.hcursor.downField("items").as[List[Json]]
      .getOrElse(throw new Exception("couldn't free mint box")).head
      .hcursor.downField("boxId").as[String].getOrElse("")
    ctx.getBoxesById(freeMintBoxId).head
  }

  def getLastInterventionBox(ctx: BlockchainContext): InputBox = {
    val interventionBoxJson = Explorer.getUnspentTokenBoxes(DexyToken.interventionNFT)
    val interventionBoxId = interventionBoxJson.hcursor.downField("items").as[List[Json]]
      .getOrElse(throw new Exception("couldn't parse intervention box")).head
      .hcursor.downField("boxId").as[String].getOrElse("")
    ctx.getBoxesById(interventionBoxId).head
  }

  def getLastLPBox(ctx: BlockchainContext): InputBox = {
    val lpBoxJson = Explorer.getUnspentTokenBoxes(DexyToken.lpNFT)
    val lpBoxId = lpBoxJson.hcursor.downField("items").as[List[Json]]
      .getOrElse(throw new Exception("couldn't parse lp box")).head
      .hcursor.downField("boxId").as[String].getOrElse("")
    ctx.getBoxesById(lpBoxId).head
  }

  def selectInputBoxForBuyer(ctx: BlockchainContext, neededErg: Long, userAddress: Address): Seq[InputBox] = {
    val inBox = ctx.getCoveringBoxesFor(
      userAddress,
      neededErg,
      List.empty.asJava
    )
    if (inBox.isCovered) inBox.getBoxes.asScala
    else throw new Exception("Don't enough Erg")
  }
}
