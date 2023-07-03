{
    // --- NOTES ---
    // * tokens(0) in the BankBox is the hodlERG token
    // * After initialization 1 hodlERG token should be burned so circulating supply is never zero.

    // --- REGISTERS ---
    // BankBox
    // R4: treasury where devFees are accumulated until withdrawn

    // --- CONSTANTS ---
    val tokenTotalSupply = 97739924000000000L   // Same as ERG
    val precisionFactor = 1000L
    val fee = (precisionFactor * 3L) / 100L     // 3%, only applied when burning
    val devFee = (precisionFactor * 3L) / 1000L // 0.3%, only applied when burning

    // --- LOGIC  ---
    val bankBoxIn = SELF
    val treasuryIn = bankBoxIn.R4[Long].get
    val reserveIn = bankBoxIn.value - treasuryIn
    val hodlCoinsIn = bankBoxIn.tokens(0)._2  // hodlCoins in the BankBox
    val hodlCoinsCircIn = tokenTotalSupply - hodlCoinsIn // hodlCoins in circulation

    val bankBoxOut = OUTPUTS(0)
    val treasuryOut = bankBoxOut.R4[Long].get
    val reserveOut = bankBoxOut.value - treasuryOut
    val hodlCoinsOut = bankBoxOut.tokens(0)._2
    val hodlCoinsCircOut = tokenTotalSupply - hodlCoinsOut

    val reserveDelta = reserveOut - reserveIn
    val treasuryDelta = treasuryOut - treasuryIn
    val hodlCoinsCircDelta = hodlCoinsCircOut  - hodlCoinsCircIn

    val isTreasuryWithdrawalAction = (treasuryDelta < 0L)
    val isMintAction = hodlCoinsCircDelta >= 0L

    val treasuryNeverNegative = treasuryIn >= 0L && treasuryOut >= 0L
    val tokenIdsConserved = bankBoxOut.tokens(0)._1 == bankBoxIn.tokens(0)._1 && // hodlERG token preserved
                            bankBoxOut.tokens(1)._1 == bankBoxIn.tokens(1)._1    // hodlERG Bank NFT token preserved

    val generalConditions = bankBoxOut.value >= 10000000L &&
                            bankBoxOut.propositionBytes == bankBoxIn.propositionBytes &&
                            tokenIdsConserved &&
                            treasuryNeverNegative

    val treasuryWithdrawalConditions = {
        val amountPerDev = - (treasuryDelta / 3L)
        val noRoundingError = treasuryDelta == - 3L * amountPerDev
        val noDust = amountPerDev >= 50000000L // Only allow withdrawal of dev fee if box values are at least 0.05 ERG.

        val threeEqualWithdrawalOutputs = { // split withdrawn amount to 3 boxes
            val box1 = OUTPUTS(1)
            val box2 = OUTPUTS(2)
            val box3 = OUTPUTS(3)

            box1.propositionBytes == PK("9hHondX3uZMY2wQsXuCGjbgZUqunQyZCNNuwGu6rL7AJC8dhRGa").propBytes &&
            box1.value == amountPerDev &&
            box2.propositionBytes == PK("9gnBtmSRBMaNTkLQUABoAqmU2wzn27hgqVvezAC9SU1VqFKZCp8").propBytes &&
            box2.value == amountPerDev &&
            box3.propositionBytes == PK("9iE2MadGSrn1ivHmRZJWRxzHffuAk6bPmEv6uJmPHuadBY8td5u").propBytes &&
            box3.value == amountPerDev
        }

        noRoundingError &&
        noDust &&
        threeEqualWithdrawalOutputs &&
        hodlCoinsOut == hodlCoinsIn // amount of hodlERGs in the bank must stay the same
    }

    val mintConditions = {
        val price = ((reserveIn * precisionFactor) / hodlCoinsCircIn)
        val expectedAmountDeposited = hodlCoinsCircDelta * price / precisionFactor

        val validReserveDelta = reserveDelta == expectedAmountDeposited
        val validTreasuryDelta = treasuryDelta == 0

        validReserveDelta &&
        validTreasuryDelta
    }

    val burnConditions = {
        val hodlCoinsBurned = hodlCoinsCircIn - hodlCoinsCircOut
        val price = ((reserveIn * precisionFactor) / hodlCoinsCircIn)
        val expectedAmountBeforeFees = hodlCoinsBurned * price / precisionFactor
        val feeAmount = expectedAmountBeforeFees * fee / precisionFactor
        val devFeeAmount = expectedAmountBeforeFees * devFee / precisionFactor
        val expectedAmountWithdrawn = expectedAmountBeforeFees - feeAmount - devFeeAmount

        val validReserveDelta = reserveDelta == - expectedAmountWithdrawn - devFeeAmount
        val validTreasuryDelta = treasuryDelta == devFeeAmount

        validReserveDelta &&
        validTreasuryDelta
    }

    sigmaProp(
        generalConditions && {
        if (isTreasuryWithdrawalAction) treasuryWithdrawalConditions
        else if (isMintAction) mintConditions
        else burnConditions
    })
}