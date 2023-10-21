package offchain

object BuyBackUtils {
  val fakeScanIds = DexyScanIds(1, 1, 1, 1, 1, 1, 1)

  val utils = new OffchainUtils(
    serverUrl = "http://127.0.0.1:9052",
    apiKey = "",
    localSecretStoragePath = "/home/kushti/ergo/backup/176keystore",
    localSecretUnlockPass = "",
    dexyScanIds = fakeScanIds)


}
