package ergoplatform.dexy

import org.ergoplatform.appkit.impl.ErgoTreeContract
import utils.Configuration
import org.ergoplatform.appkit.{Address, BlockchainContext, ContextVar, ErgoToken, ErgoValue, InputBox, SignedTransaction}
import utils.OnChainData._

import scala.collection.JavaConverters._

object DexyOffChain {

  def calculateNeededErg(OPBox: InputBox, tokenRequested: Long, fee: Long): (Long, Long) = {
    val oraclePoolRate = OPBox.getRegisters.get(0).getValue.asInstanceOf[Long]
    val emissionNeededAmount = oraclePoolRate * tokenRequested
    (emissionNeededAmount, emissionNeededAmount + fee + Configuration.minErg)
  }

  def buyDexyUSDFromEmissionBox(ctx: BlockchainContext, userAddress: Address, userValue: Long, userFee: Long): Unit = {

    val lastOPBox = getLastOracleBox(ctx)
    val neededErg = calculateNeededErg(lastOPBox, userValue, userFee)

    val selfOutIndex = 0
    val lastEmissionBox = getLastEmissionBox(ctx).withContextVars(ContextVar.of(0.toByte, selfOutIndex))
    val userInputBoxes = selectInputBoxForBuyer(ctx, neededErg._2, userAddress)


    val txBuilderObj = ctx.newTxBuilder()

    val userBox = txBuilderObj.outBoxBuilder()
      .value(Configuration.minErg)
      .tokens(new ErgoToken(DexyToken.dexyUSDToken, userValue))
      .contract(new ErgoTreeContract(userAddress.getErgoAddress.script))
      .build()

    val newEmissionBox = txBuilderObj.outBoxBuilder()
      .value(lastEmissionBox.getValue + neededErg._1)
      .tokens(
        lastEmissionBox.getTokens.get(0),
        new ErgoToken(lastEmissionBox.getTokens.get(1).getId, lastEmissionBox.getTokens.get(1).getValue - userValue)
      )
      .contract(new ErgoTreeContract(DexyContracts.dexyAddresses.emissionAddress.script))
      .build()

    val tx = txBuilderObj.boxesToSpend((lastEmissionBox +: userInputBoxes).asJava)
      .outputs(newEmissionBox, userBox)
      .fee(userFee)
      .sendChangeTo(userAddress.getErgoAddress)
      .withDataInputs(List(lastOPBox).asJava)
      .build()

    println("Please enter your privateKey:")
    val secret = scala.io.StdIn.readLine()

    val prover = ctx.newProverBuilder()
      .withDLogSecret(BigInt(secret, 16).bigInteger)
      .build()

    val signed: SignedTransaction = prover.sign(tx)
    println(s"Your tx is: ${signed.toJson(false)}")

    println("Are you sure broadcast tx ? (y/n)")
    val check = scala.io.StdIn.readChar()
    if(check == 'y') println(s"TxID is: ${ctx.sendTransaction(signed)}")
    else sys.exit(0)
  }

}
