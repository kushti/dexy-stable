{
    // Liquidity pool script
    // Unlike ErgoDex (Spectrum) scripts, we split the script into many action scripts, like done with the bank script
    //
    // Other differences from original Spectrum's script are:
    //  * 2% redemption fee
    //  * redemption is inactive when LP price is < 0.98 * oracle price
    //  * additional intervention action (where bank interacts with LP), defined in bank/intervention.es
    //  * additional extract-to-the-future and release-extracted-tokens actions (extract.es)
    //
    // This box: (LP box)
    //
    // TOKENS
    //   Tokens(0): NFT to uniquely identify LP box.
    //   Tokens(1): LP tokens
    //   Tokens(2): Y tokens, the Dexy tokens (Note that X tokens are NanoErgs (the value)
    //
    // TRANSACTIONS
    //
    // [1] Intervention
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Bank          |  Bank          |
    // 2 Intervention  |  Intervention  |
    //
    // [2] Swap
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |
    // 1 Swap          |  Swap          |
    //
    // [3] Redeem LP tokens
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Redeem        |  Redeem
    //
    // [4] Mint LP tokens
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |
    // 1 Mint          |  Mint
    //
    // [5] Extract to future
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Extract       |  Extract       |   Bank
    // 2               |                |   Tracking (95%)
    //
    // [6] Release extracted to future tokens
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 LP            |  LP            |   Oracle
    // 1 Extract       |  Extract       |   Tracking (101%)
    //
    // -------------------------------------------------------------
    // Notation:
    //
    // X is the primary token
    // Y is the secondary token
    // In DexyUSD, X is NanoErg and Y is USD


    // inputs
    val interventionBoxIndex = 2
    val extractBoxIndex = 1
    val lpActionBoxIndex = 1 // swap/redeem/mint

    // outputs
    val selfOutIndex = 0

    val interventionNFT = fromBase64("$interventionNFT")
    val extractionNFT = fromBase64("$extractionNFT")
    val swapNFT = fromBase64("$lpSwapNFT")
    val mintNFT = fromBase64("$lpMintNFT")
    val redeemNFT = fromBase64("$lpRedeemNFT")

    val interventionBox = INPUTS(interventionBoxIndex)
    val extractBox = INPUTS(extractBoxIndex)
    val swapBox = INPUTS(lpActionBoxIndex)
    val mintBox = INPUTS(lpActionBoxIndex)
    val redeemBox = INPUTS(lpActionBoxIndex)

    val successor = OUTPUTS(selfOutIndex) // copy of this box after exchange

    val validSwap      = swapBox.tokens(0)._1 == swapNFT
    val validMint      = mintBox.tokens(0)._1 == mintNFT
    val validRedeem    = redeemBox.tokens(0)._1 == redeemNFT

    val validIntervention = interventionBox.tokens.size > 0 && interventionBox.tokens(0)._1 == interventionNFT
    val validExtraction   = extractBox.tokens(0)._1 == extractionNFT

    val lpNftIn      = SELF.tokens(0)
    val lpReservesIn = SELF.tokens(1)
    val tokenYIn     = SELF.tokens(2)

    val lpNftOut      = successor.tokens(0)
    val lpReservesOut = successor.tokens(1)
    val tokenYOut     = successor.tokens(2)

    val preservedScript      = successor.propositionBytes == SELF.propositionBytes
    val preservedLpNft       = lpNftIn == lpNftOut
    val preservedLpTokenId   = lpReservesOut._1 == lpReservesIn._1
    val preservedDexyTokenId = tokenYOut._1 == tokenYIn._1

    // Note:
    //    supplyLpIn = initialLp - lpReservesIn._2
    //    supplyLpOut = initialLp - lpReservesOut._2
    // Thus:
    //    deltaSupplyLp = supplyLpOut - supplyLpIn
    //                  = (initialLp - lpReservesOut._2) - (initialLp - lpReservesIn._2)
    //                  = lpReservesIn._2 - lpReservesOut._2

    val deltaSupplyLp  = lpReservesIn._2 - lpReservesOut._2

    // since tokens can be repeated, we ensure for sanity that there are no more tokens
    val noMoreTokens         = successor.tokens.size == 3

    val lpAction = validSwap || validMint || validRedeem

    val dexyAction = (validIntervention || validExtraction) &&
                      deltaSupplyLp == 0 // ensure Lp tokens are not extracted during dexyAction
    sigmaProp(
        preservedScript           &&
        preservedLpNft            &&
        preservedLpTokenId        &&
        preservedDexyTokenId      &&
        noMoreTokens              &&
        (lpAction || dexyAction)
    )
}