package offchain

import offchain.BuyBackUtils.utils

/**
 * Offchain functions to work with GORT dev emission contract
 */
object GortDevUtils extends App {
  val utils = new OffchainUtils(
    serverUrl = "http://176.9.15.237:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = OffchainUtils.scanIds)

  // merge pay-to-emission and emission boxes
  def merge() = {
    val creationHeight = utils.currentHeight()
  }

  def payout() = {
    val creationHeight = utils.currentHeight()
  }
}
