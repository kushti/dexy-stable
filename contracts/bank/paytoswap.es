{
  // This script contains pay-to-swap conditions
  //  Swap
  //   Input         |  Output        |   Data-Input
  // -----------------------------------------------
  // 0 LP            |  LP            |
  // 1 Swap          |  Swap          |
  // 2 Pay-to-swap   |                |

  // checking that swap inputs provided
  val swapNft = INPUTS(0).tokens(0)._1 == fromBase64("$lpNFT") &&
                  INPUTS(1).tokens(0)._1 == fromBase64("$lpSwapNFT")

  sigmaProp(swapNft)

}