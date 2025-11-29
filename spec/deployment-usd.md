# USE Deployment Notes



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

USE (DexyUSD) token = "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669" 

6 decimals

DORT and oracle pool:

* DORT = "ae399fcb751e8e247d0da8179a2bcca2aa5119fff9c85721ffab9cdc9a3cb2dd"

* oracle pool NFT = "6a2b821b5727e85beb5e78b4efb9f0250d59cd48481d2ded2c23e91ba1d07c66"

* oracle token ID = "74fa4aee3607ceb7bdefd51a856861b5dbfa434a8f6c93bfe967de8ed1a30a78"

* DORT LP NFT = "35bc71897cd44d1a624285c54a0be66b69d1c61674603ed89dfe136f32035f0e"

* DORT Dev emission NFT = "" // no dev emission

// 3 tokens be issued to make parallel execution easier
* Buyback NFT = "dcce07af04ea4f9b7979336476594dc16321547bcc9c6b95a67cb1a94192da4f"

Dexy LP tokens:

* USE LP token = "804a66426283b8281240df8f9de783651986f20ad6391a71b26b9e7d6faad099"

* USE LP NFT = "4ecaa1aac9846b1454563ae51746db95a3a40ee9f8c5f5301afbe348ae803d41"

* LP Swap NFT = "ef461517a55b8bfcd30356f112928f3333b5b50faf472e8374081307a09110cf"

* LP Mint NFT = "2cf9fb512f487254777ac1d086a55cda9e74a1009fe0d30390a3792f050de58f"

* LP Redeem NFT = "1bfea21924f670ca5f13dd6819ed3bf833ec5a3113d5b6ae87d806db29b94b9a"

* extractionNFT = "bc685d6ad1703ba5775736308fd892807edc04f48ba7a52e802fab241a59962c"

Bank tokens:

* USE Bank NFT = "78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"

* Arbitrage Mint NFT = "c79bef6fe21c788546beab08c963999d5ef74151a9b7fd6c1843f626eea0ecf5"

* interventionNFT = "dbf655f0f6101cb03316e931a689412126fefbfb7c78bd9869ad6a1a58c1b424"

* freeMintNFT = "40db16e1ed50b16077b19102390f36b41ca35c64af87426d04af3b9340859051"

* arbitrageMintNFT = "c79bef6fe21c788546beab08c963999d5ef74151a9b7fd6c1843f626eea0ecf5"

* payoutNFT = "a2482fca4ca774ef9d3896977e3677b031597c6e312b0c10d47157bb0d6ed69f"

// 5 tokens issued, 3 out of 5 votes are needed for an update
* USE Update Ballot Token = "a67d769e70b98e56e81de78fb8dcc689e037754932da67edf49bab420ec8986e"

// 3 tokens issued for bank, intervention, extract-to-the-future contracts
* USE Update NFT = "f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883"

* tracking95NFT = "57af5c7446d419e98e2e6fbd4bce9029cd589f8094686c457902feb472f194ec"

* tracking98NFT = "47472f675d7791462520d78b6c676e65c23b7c11ca54d73d3e031aadb5d56be2"

* tracking101NFT = "fec586b8d7b92b336a5fea060556cbb4ced15d5334dcb7ca9f9a7bb6ca866c42"


* USE token issuance
  https://explorer.ergoplatform.com/en/transactions/adbf3c5855aa66baf5e45dc192c2bb6dc85f168eafffc9ade7d3fd79137a39cd


### Deployment transactions

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