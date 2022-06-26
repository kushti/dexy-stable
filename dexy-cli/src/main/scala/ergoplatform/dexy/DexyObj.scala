package ergoplatform.dexy

import ergoplatform.dexy.DexyOffChain.buyDexyUSDFromBankBox
import org.ergoplatform.appkit.Address
import utils.Configuration
object DexyObj {

  def main(args: Array[String]): Unit = {
    println(s"Started app with args")
    println(args.toSeq)
    Configuration.ergoClient.execute { ctx =>
      buyDexyUSDFromBankBox(
        ctx,
        Address.create(s"${args.toSeq.toIndexedSeq(0)}"), // User Address
        args.toSeq.toIndexedSeq(1).toLong,                // User Value
        args.toSeq.toIndexedSeq(2).toLong                 // User Fee
      )
    }
  }
}
