package utils

import ergoplatform.dexy.{DexyContracts, DexyToken}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoToken, InputBox}
import scala.collection.JavaConverters._

object OnChainData {
  def getLastOracleBox(ctx: BlockchainContext): InputBox = {
    ctx.getCoveringBoxesFor(
      Address.create(Configuration.serviceConf.network.oracleAddress),
      (1e9 * 1e8).toLong,
      List(new ErgoToken(DexyToken.oraclePoolNFT, 1L)).asJava
    ).getBoxes.asScala.head
  }

  def getLastEmissionBox(ctx: BlockchainContext): InputBox = {
    ctx.getCoveringBoxesFor(
      new Address(DexyContracts.dexyAddresses.emissionAddress),
      (1e9 * 1e8).toLong,
      List(new ErgoToken(DexyToken.emissionNFT, 1L)).asJava
    ).getBoxes.asScala.head
  }

  def getLastSwappingBox(ctx: BlockchainContext): InputBox = {
    ctx.getCoveringBoxesFor(
      new Address(DexyContracts.dexyAddresses.swappingAddress),
      (1e9 * 1e8).toLong,
      List(new ErgoToken(DexyToken.swappingNFT, 1L)).asJava
    ).getBoxes.asScala.head
  }

  def getLastTrackingBox(ctx: BlockchainContext): InputBox = {
    ctx.getCoveringBoxesFor(
      new Address(DexyContracts.dexyAddresses.trackingAddress),
      (1e9 * 1e8).toLong,
      List(new ErgoToken(DexyToken.trackingNFT, 1L)).asJava
    ).getBoxes.asScala.head
  }

  def getLastLPBox(ctx: BlockchainContext): InputBox = {
    ctx.getCoveringBoxesFor(
      new Address(DexyContracts.dexyAddresses.lpAddress),
      (1e9 * 1e8).toLong,
      List(new ErgoToken(DexyToken.lpNFT, 1L)).asJava
    ).getBoxes.asScala.head
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
