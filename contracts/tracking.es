{
    // This box: Tracking box
    //
    // TOKENS
    //   tokens(0): Tracking NFT
    //
    // REGISTERS
    //   R4: Int (nominator)
    //   R5: Int (denumerator)
    //   R6: Boolean (isBelow, a flag indicating the type of tracking)
    //   R7: Int (trackingHeight)
    //
    // TRANSACTIONS
    // [1] Update tracker
    //   Input         |  Output        |   Data-Input
    // -----------------------------------------------
    // 0 Tracking box  |  Tracking box  |   Oracle
    // 1               |                |   LP


    // Oracle data:
    // R4 of the oracle contains the rate "nanoErgs per USD" in Long format

    //
    // A "tracker" is like a monitor that triggers an alarm when an event occurs.
    // The alarm continues to "ring" until the tracker resets the alarm, which can only happen after the event has ended.
    // Thus, if the alarm is in a "triggered" state, we can be sure that the event is ongoing (i.e., not yet ended).
    // In our case, the event we are monitoring is the ratio of LP rate and Oracle rate going below (or above) some value.
    // The registers define the ratio to monitor (R4, R5) and whether we are monitoring above or below (R6), along with
    // the height at which the trigger occurred (R7). The value in R7 will be "infinity" if the event has ended.

    // This box is be spent whenever tracker state must change i.e., move from trigger to reset or vice versa
    // This box can only be be spent if the tracker state changes.
    // Someone must spend this box keeping LP as data input.

    val threshold = 3 // error threshold in trigger height, in number of blocks

    val oracleBoxIndex = 0
    val lpBoxIndex = 1
    val selfOutIndex = 0

    val lpNFT = fromBase64("$lpNFT") // to identify LP box
    val oracleNFT = fromBase64("$oracleNFT") // to identify oracle pool box

    val lpBox = CONTEXT.dataInputs(lpBoxIndex)
    val oracleBox = CONTEXT.dataInputs(oracleBoxIndex)
    val successor = OUTPUTS(selfOutIndex)

    val tokenY = lpBox.tokens(2)  // Dexy tokens

    val validLp = lpBox.tokens(0)._1 == lpNFT

    val validOracleBox = oracleBox.tokens(0)._1 == oracleNFT
    val validSuccessor = successor.tokens == SELF.tokens                      &&
                         successor.propositionBytes == SELF.propositionBytes  &&
                         SELF.value <= successor.value

    // oracle delivers nanoErgs per 1 kg of gold
    // we divide it by 1000000 to get nanoErg per dexy, i.e. 1 mg of gold
    // we assume always > 0 (ref oracle pool contracts) NanoErgs per USD
    val oracleRateXY = oracleBox.R4[Long].get / 1000000L
    val reservesX = lpBox.value
    val reservesY = tokenY._2   // number of Dexy tokens
    val lpRateXY = reservesX / reservesY  // we can assume that reservesY > 0 (since at least one token must exist)

    // Let t = num/denom
    // trackerHeight is the height at which the tracker was "triggered".
    // If the tracker "reset", then trackerHeight will store Int.MaxValue
    //
    // isBelow tells us if the tracking should be tracking "below" or "above" the ratio.
    // Let r be the ratio "oracle pool rate" / "LP rate", where the term "rate" denotes "Ergs per dexy"
    // Now, if "isBelow" is true (i.e. "lower" tracking), then the tracker will be triggered when r goes below t and will be reset once r goes above t

    // there are three tracking boxes as of now:
    // box # | num | denom | height | isBelow
    // ------+-----+-------+--------+--------
    // 1     | 95  | 100   | _      | true     (for extracting to future)
    // 2     | 98  | 100   | _      | true     (for arbitrage mint)
    // 3     | 101 | 100   | _      | false    (for release in future - reverse of extract to future)


    val numIn = SELF.R4[Int].get
    val denomIn = SELF.R5[Int].get
    val isBelowIn = SELF.R6[Boolean].get
    val trackerHeightIn = SELF.R7[Int].get

    val numOut = successor.R4[Int].get
    val denomOut = successor.R5[Int].get
    val isBelowOut = successor.R6[Boolean].get
    val trackerHeightOut = successor.R7[Int].get

    val validTracking = {
        // For a ratio of 95%, set num to 95 and denom to 100, and set isBelow to true
        // Then trackerHeight will be set when oracle pool rate becomes <= 95% of LP rate
        // and it will be reset to Int.MaxValue when that rate becomes > than 95% of LP rate
        //
        // Let oracle pool rate be P and LP rate at earlier point be L0 and currently (via data input) be L1
        // Let N and D denote num and denom respectively. Then we can use the following table to decide correctness
        //
        // EVENT    | isBelow | INPUT       | OUTPUT
        // ---------+---------+-------------+-----------
        // trigger  | true    | L0/P >= N/D | L1/P <  N/D
        // reset    | true    | L0/P <  N/D | L1/P >= N/D (reverse of 1st row)
        // ---------+---------+-------------+------------
        // trigger  | false   | L0/P <= N/D | L1/P >  N/D
        // reset    | false   | L0/P >  N/D | L1/P <= N/D (reverse of 1st row)

        val x = lpRateXY * denomIn
        val y = numIn * oracleRateXY

        val notTriggeredEarlier = trackerHeightIn == $intMax  // Infinity
        val triggeredNow = trackerHeightOut >= HEIGHT - threshold &&
                           trackerHeightOut <= HEIGHT

        val notResetEarlier = trackerHeightIn < $intMax       // Less than Infinity
        val resetNow = trackerHeightOut == $intMax            // Infinity

        val trigger = ((isBelowIn && x < y) || (!isBelowIn && x > y)) && notTriggeredEarlier && triggeredNow
        val reset = ((isBelowIn && x >= y) || (!isBelowIn && x <= y)) && notResetEarlier && resetNow
        val correctAction = trigger || reset

        numOut == numIn          &&
        denomOut == denomIn      &&
        isBelowOut == isBelowIn  &&
        correctAction
    }

    sigmaProp(validSuccessor && validLp && validOracleBox && validTracking)
}