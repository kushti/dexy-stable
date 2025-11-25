# DexyUSD Deployment Notes



## Mainnet

### Parameters

LP:

* minimum value below each extract-to-the-future action activates (minBankNanoErgs in extract.es): 10,000 ERG

Bank:

* votes needed to update the bank script (minVotes in /bank/update/update.es): 3 (out of 5)
* 3 update NFTs should be issued (see "Updating the protocol" for details)


### Updating the protocol

Bank, extract, and intervention scripts may be updated. Bank update may include changing the NFTs of bank actions.

Update script boxes for all the three scripts share the same identification NFTs (used in ballot contract), thus three
update NFTs should be issued.


### Tokens

USE (DexyUSD) token = "bf0e1826d225617aeca3ad9a4df6b700af14dd683631b5ba9857f1b17322e53d"

DORT and oracle pool:

* DORT = ""
* oracle pool NFT = ""
* oracle token ID = ""

* DORT LP NFT = ""

* DORT Dev emission NFT = ""

// 3 tokens be issued to make parallel execution easier
* Buyback NFT = ""

Dexy LP tokens:

* Dexy LP token = ""

* Dexy LP NFT = ""

* LP Swap NFT = ""

* LP Mint NFT = ""

* LP Redeem NFT = ""

Bank tokens:

* Dexy Bank NFT = ""

* Arbitrage Mint NFT = ""

// 5 tokens issued, 3 out of 5 votes are needed for an update
* Dexy Update Ballot Token = ""

// 3 tokens issued for bank, intervention, extract-to-the-future contracts
* Dexy Update NFT = ""



### Deployment transactions

* USE token issuance
  https://explorer.ergoplatform.com/en/transactions/b3bb8f634a3b85f25879dfa95f5ed6d7b4a3a59f832fcf44b4bdecda4b0dbbe6

* DORT dev emission contract deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Buyback contract deployment transactions:

#### Tracking

* Tracking 95% deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Tracking 98% deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Tracking 101% deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/


#### LP

* LP Swap deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* LP Mint deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* LP redeem deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* LP extract to the future deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* LP deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

#### Bank

* Arbitrage Mint deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Free Mint deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Payout deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Intervention:
  https://explorer.ergoplatform.com/en/transactions/

* Buyback deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

* Bank deployment transaction:
  https://explorer.ergoplatform.com/en/transactions/

## Testnet

### Tokens

gort = ""
oracleTokenId = ""
oraclePoolNFT = ""

gortDevEmissionNFT: String = "" // todo: not real

// DORT / ERG LP
gortLpNFT = "" // todo: not real

buybackNFT = "" // todo: not real

dexyTokenId = ""

lpTokenId = ""

// tokens for main boxes
bankNFT = ""
lpNFT = ""

// update tokens
updateNFT = ""
ballotTokenId = ""

// all tokens below for aux boxes (1 for each type of box)
interventionNFT = ""
freeMintNFT = ""
arbitrageMintNFT = ""
payoutNFT = ""

lpSwapNFT = ""
lpMintNFT = ""
lpRedeemNFT = ""
extractionNFT = ""

// should be reissued every time!
// boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
tracking95NFT = ""
tracking98NFT = ""
tracking101NFT = ""