# Deployment Notes

## Mainnet

### Tokens

* GORT: 7ba2a85fdb302a181578b1f64cb4a533d89b3f8de4159efece75da41041537f9
* oracle pool NFT = "3c45f29a5165b030fdb5eaf5d81f8108f9d8f507b31487dd51f4ae08fe07cf4a"
* oracle token ID = "6183680b1c4caaf8ede8c60dc5128e38417bc5b656321388b22baa43a9d150c2"

* GORT LP NFT = "d1c9e20657b4e37de3cd279a994266db34b18e6e786371832ad014fd46583198"

* GORT Dev emission NFT = "bb484bb7fea08b15861e27cb203a13069082befb05f5437cae71237d9c5c6ac3"

// todo: reissue, at least 10 tokens should be issued to allow parallel execution
* Buyback NFT = "119a068a0119670de8a5d2467da33df572903c64aaa7b6ea4c9668ef0cfe0325"

### Deployment transactions

* GORT dev emission contract deployment transaction:
https://explorer.ergoplatform.com/en/transactions/a221696d22f79cb421cf9a9769dbcd759e9b1528333d8f3626e6305d76bc162f

//todo: reissue
* Buyback contract deployment transaction:
https://explorer.ergoplatform.com/en/transactions/a3b06409af1488ea0837955e5f6914cddb11177620ebef8e4cdd4976401a96bb

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