package offchain

import io.circe.parser.parse
import org.ergoplatform.ErgoBox
import org.ergoplatform.ErgoBox.R4
import org.ergoplatform.http.api.ApiCodecs
import scalaj.http.{Http, HttpOptions}
import sigmastate.Values.LongConstant

case class DexyScanIds(tracking95ScanId: Int,
                       tracking98ScanId: Int,
                       tracking101ScanId: Int,
                       oraclePoolScanId: Int,
                       lpScanId: Int)

class OffchainUtils(serverUrl: String, apiKey: String, dexyScanIds: DexyScanIds) extends ApiCodecs {

  def getJsonAsString(url: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .header("api_key", apiKey)
      .option(HttpOptions.readTimeout(10000))
      .asString
      .body
  }

  def unspentScanBoxes(scanId: Int): Seq[ErgoBox] = {
    val scanUnspentUrl = s"$serverUrl/scan/unspentBoxes/$scanId?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(scanUnspentUrl)).toOption.get
    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def fetchInputs(): Seq[ErgoBox] = {
    val boxesUnspentUrl = s"$serverUrl/wallet/boxes/unspent?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(boxesUnspentUrl)).toOption.get

    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def tracking95Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking98Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking101Box(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def oraclePoolBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.oraclePoolScanId).headOption

  def lpBox(): Option[ErgoBox] = unspentScanBoxes(dexyScanIds.lpScanId).headOption
}

object Test extends App {
  val scanIds = DexyScanIds(19, 20, 21, 32, 31)

  val utils = new OffchainUtils("http://176.9.15.237:9052", "", scanIds)

  val oraclePrice = utils.oraclePoolBox().get.additionalRegisters.apply(R4).value.asInstanceOf[Long]
  val lpBox = utils.lpBox().get
  val lpPrice = lpBox.value / lpBox.additionalTokens.apply(2)._2

  println(oraclePrice / 1000000)
  println(lpPrice)


}
