package offchain

import io.circe.parser.parse
import org.ergoplatform.ErgoBox
import org.ergoplatform.http.api.ApiCodecs
import scalaj.http.{Http, HttpOptions}

case class DexyScanIds(tracking95ScanId: Int,
                       tracking98ScanId: Int,
                       tracking101ScanId: Int,
                       oraclePoolScanId: Int)

class Utils(serverUrl: String, dexyScanIds: DexyScanIds) extends ApiCodecs {

  def getJsonAsString(url: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .header("api_key", "hello")
      .option(HttpOptions.readTimeout(10000))
      .asString
      .body
  }

  def unspentScanBoxes(scanId: Int): Seq[ErgoBox] = {
    val scanUnspentUrl = s"$serverUrl/scan/unspentBoxes/$scanId?minConfirmations=0&maxConfirmations=-1&minInclusionHeight=0&maxInclusionHeight=-1"
    val boxesUnspentJson = parse(getJsonAsString(scanUnspentUrl)).toOption.get
    boxesUnspentJson.\\("box").map(_.as[ErgoBox].toOption.get)
  }

  def tracking95Box() = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking98Box() = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def tracking101Box() = unspentScanBoxes(dexyScanIds.tracking95ScanId).headOption

  def oraclePoolBox() = unspentScanBoxes(dexyScanIds.oraclePoolScanId).headOption
}
