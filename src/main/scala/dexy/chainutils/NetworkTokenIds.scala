package dexy.chainutils


trait NetworkTokenIds {
  // oracle related tokens
  // take token IDs below from oracle pool UI
  // Gold Oracle Reward Token
  val gort: String
  val oracleTokenId: String
  val oraclePoolNFT: String

  // GORT dev emission contract NFT
  val gortDevEmissionNFT: String

  // GORT / ERG LP
  val gortLpNFT: String

  val buybackNFT: String

  // dexy gold token id
  val dexyTokenId: String

  val lpTokenId: String

  // tokens for main boxes
  val bankNFT: String
  val lpNFT: String

  // update tokens
  val updateNFT: String
  val ballotTokenId: String

  // all tokens below for aux boxes (1 for each type of box)
  val interventionNFT: String
  val freeMintNFT: String
  val arbitrageMintNFT: String
  val payoutNFT: String

  val lpSwapNFT: String
  val lpMintNFT: String
  val lpRedeemNFT: String
  val extractionNFT: String

  // should be reissued every time!
  // boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
  val tracking95NFT: String
  val tracking98NFT: String
  val tracking101NFT: String
}