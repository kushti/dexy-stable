package utils

import io.circe.Json
import io.circe.parser.parse
import scalaj.http.{Http, HttpOptions}

import scala.util.{Failure, Success}

object Explorer {
  private val baseUrlV1 = s"${Configuration.explorerUrl}/api/v1"
  private val unspentBoxesByTokenId = s"$baseUrlV1/boxes/unspent/byTokenId"

  private def getJsonAsString(url: String): String = {
    Http(s"$url")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.readTimeout(10000))
      .asString
      .body
  }


  def getUnspentTokenBoxes(tokenId: String): Json = {
    parse(getJsonAsString(s"$unspentBoxesByTokenId/$tokenId")).toTry match {
      case Success(infoJs) =>
        infoJs
      case Failure(exception) => throw exception
    }
  }

}
