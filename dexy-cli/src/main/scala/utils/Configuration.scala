package utils
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.{ErgoClient, NetworkType, RestApiErgoClient}
import pureconfig.ConfigSource
import pureconfig.generic.auto._

object Configuration {
  val serviceConf: ServiceConf = ConfigSource.default.load[ServiceConf] match {
    case Right(conf) => conf
    case Left(error) => throw new Exception(error.toString())
  }

  lazy val networkType: NetworkType = if (serviceConf.network.networkType.equals("mainnet")) NetworkType.MAINNET else NetworkType.TESTNET
  lazy val explorerUrl: String = if (serviceConf.network.explorerUrl.isEmpty) RestApiErgoClient.getDefaultExplorerUrl(networkType) else serviceConf.network.explorerUrl
  lazy val addressEncoder = new ErgoAddressEncoder(networkType.networkPrefix)
  lazy val ergoClient: ErgoClient = RestApiErgoClient.create(serviceConf.network.nodeUrl, networkType, "", explorerUrl)
  lazy val minErg: Long = 100000
}
