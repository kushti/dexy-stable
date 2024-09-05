# Deployment Notes

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

GORT and oracle pool:

* GORT = "7ba2a85fdb302a181578b1f64cb4a533d89b3f8de4159efece75da41041537f9"
* oracle pool NFT = "3c45f29a5165b030fdb5eaf5d81f8108f9d8f507b31487dd51f4ae08fe07cf4a"
* oracle token ID = "6183680b1c4caaf8ede8c60dc5128e38417bc5b656321388b22baa43a9d150c2"

* GORT LP NFT = "d1c9e20657b4e37de3cd279a994266db34b18e6e786371832ad014fd46583198"

* GORT Dev emission NFT = "bb484bb7fea08b15861e27cb203a13069082befb05f5437cae71237d9c5c6ac3"

// 3 tokens be issued to make parallel execution easier
* Buyback NFT = "bf24ed4af7eb5a7839c43aa6b240697d81b196120c837e1a941832c266d3755c"

Dexy LP tokens:

* Dexy LP token = "376603b9ecbb953202fbac977f418ab5edc9d9effafbbe1418f5aece661dfa1f"

* Dexy LP NFT = "905ecdef97381b92c2f0ea9b516f312bfb18082c61b24b40affa6a55555c77c7"

* LP Swap NFT = "c9f1304c58a1b789c0c5b4c601fa12acde1188fdff245d72bdc549c9296d2aa4"

* LP Mint NFT = "19b8281b141d19c5b3843a4a77e616d6df05f601e5908159b1eaf3d9da20e664"

* LP Redeem NFT = "08c47eef5e782f146cae5e8cfb5e9d26b18442f82f3c5808b1563b6e3b23f729"

Bank tokens: 

* Dexy Bank NFT = "75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"

* Arbitrage Mint NFT = "c28c5104a4ceb13f9e6ca18f312d3e5d543e64a94eb2e4333e4d6c2f0590042a"

// 5 tokens issued, 3 out of 5 votes are needed for an update
* Dexy Update Ballot Token = "3277be793f89bd88706938dd09ad49afe29a62b67b596d54a5fd7e06bf8e71ce"

// 3 tokens issued for bank, intervestion, extract-to-the-future contracts
* Dexy Update NFT = "7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09"



### Deployment transactions

* GORT dev emission contract deployment transaction:
https://explorer.ergoplatform.com/en/transactions/a221696d22f79cb421cf9a9769dbcd759e9b1528333d8f3626e6305d76bc162f

* Buyback contract deployment transactions:
https://explorer.ergoplatform.com/en/transactions/bd9ec38f1f60f748ef4cf6bd30a7414bcf985f42c37df08eb8002ce589c6202e
https://explorer.ergoplatform.com/en/transactions/bbb7c447e4e1472b3625f705c5b4e029af873ec241bf683b3b4ae385f36f52a1
https://explorer.ergoplatform.com/en/transactions/9343582531817d00b79c811f00ebe6184be5bc0d2d4b46bbc4a8cd88a8938b75

#### Tracking

* Tracking 95% deployment transaction:
https://explorer.ergoplatform.com/en/transactions/4c14e7a493ef49f6b9924ef2c9071a2f018098fd4e55079cad74a64a920f6077

* Tracking 98% deployment transaction:
https://explorer.ergoplatform.com/en/transactions/4e87315a08162bb63d8b7cd7cc8e0f01928c21bd1c21e12af4d98ac0dd89cba5

* Tracking 101% deployment transaction:
https://explorer.ergoplatform.com/en/transactions/f15bbae3f3def0e2f825bc656a232fdbe678048969f3a059c1d35713fbde3240

#### LP 

* LP Swap deployment transaction:
https://explorer.ergoplatform.com/en/transactions/0af8231dbc0f1b230be9611ec06db18949a9004653faa1715d3bd2e7489a8019

* LP Mint deployment transaction:
https://explorer.ergoplatform.com/en/transactions/50846c2cb97cbba0ea3c8581327f08ab7849491a788340324fab9036da98f24e

* LP redeem deployment transaction:
https://explorer.ergoplatform.com/en/transactions/4b7131a95bcbd07a8e97e8449316181fc48b7ea9256ac7c562445b7e836b350e

#### Bank

* Arbitrage Mint deployment transaction:
https://explorer.ergoplatform.com/en/transactions/8fa18c55c710f267d15d8f107fcf4d526d5e51583b0feaed8a9cd858900705d7

* Free Mint deployment transaction:
https://explorer.ergoplatform.com/en/transactions/df7a809754766880c12885b970c51df952b6c6f2dccf1234a150815db91f751e

## Testnet

### Tokens

gort = "01510156b109cd66c41a703c9911925ab305e4fe2bdc898680ad255c6972c404"
oracleTokenId = "001e182cc3f04aec4486c7a5018d198e9591a7cfb0b372f5f95fa3e5ddbd24d3"
oraclePoolNFT = "d94bfac40b516353983443209104dcdd5b7ca232a01ccb376ee8014df6330907"

gortDevEmissionNFT: String = "d94bfac40b516353983443209104dcdd5b7ca232a01ccb376ee8014df6330907" // todo: not real

// GORT / ERG LP
gortLpNFT = "043ea12f03769748e436c003886c455ddf1a7cd4aafbd214602822d5213f4e68" // todo: not real

buybackNFT = "0158bb202fdf8eacdd4b08e972776077284e5a69708af719669e6f65ceeaf809" // todo: not real

dexyTokenId = "0d69a552b30df9be519099ec07682039b0610267aaee48d2a1d3dad398287ef5"

lpTokenId = "0e1f9fb56d3e2ab827cdf4a39ad59c679188ce7fc71df1572f58ae5b7b08ec2f"

// tokens for main boxes
bankNFT = "0f6209f38beb3189e897df42189b858a2f9d96e0d00d6036333ca4bccc11af22"
lpNFT = "0fa04f3851b18085f160d90bc3dba1c63f2fdc73f884c9fd94395dbfc9c293b6"

// update tokens
updateNFT = "0faa129eecdfc04ba4b454bbf33785f89f9f56eda2df0577d610da3adce65ddb"
ballotTokenId = "0fbc880ca7be40e36b4fb112aaba83dd44bd613ee171c4775a5c6ae4d358e644"

// all tokens below for aux boxes (1 for each type of box)
interventionNFT = "102a5f871dd4f3b051dc3295068a5f69af148b5fe9d182353798b67c013f9d9e"
freeMintNFT = "113b18ca82b272dca4499a847ab4ea792cd9f92bd98f5f9975dc1fd2160615ff"
arbitrageMintNFT = "1201d3bb72a9c212d37fdea2a76d0e1857a4f69189901c3fad02b8723174b98c"
payoutNFT = "126a63cd06bdb020a48bb76a5927c67e50ac20a2c3d05fb1e2489b4e41be4339"

lpSwapNFT = "1290ef3c02310d7dd110a9cc6b0a0aa3b4d669ed4c75b3ed9b25652b9189e50d"
lpMintNFT = "12a1cfce7a3ad46b3d7d8a78bf1ec29c210f1ab8216a5670ed6ea0af8abd19b4"
lpRedeemNFT = "12e5820470d332344c73ad21f5b1249c40f2ecbdd8d51db3f912944c279db909"
extractionNFT = "12f11de6defb4478423b08c01a50affa63d4c4ba7a070c87fb66f58ea7c0e4db"

// should be reissued every time!
// boxes for tracking ratio of LP rate and oracle pool rate (see details in Tracking contract)
tracking95NFT = "13a2cc2c2f2954d8b336a0fe95d0ac21e29eb8f60de82661d54b1b8c48e52a86"
tracking98NFT = "13aa951ba2b7f82da62e1b970eca3b8f30ddbf331f8010d60094488ba938f31b"
tracking101NFT = "1432466dbf0c15294546086e6a76701c89a1a6ed6f9179242151d72c92f79f3b"