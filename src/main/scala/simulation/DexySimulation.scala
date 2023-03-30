package simulation

import scala.util.Random

case class Bank(E: Double, O: Double)

case class LiquidityPool(erg: Double, usd: Double) {
  def poolPrice: Double = usd / erg
}

trait Oracle {
  def p: Double
}

case class Time(override val p: Double, epoch: Long) extends Oracle

case class State(bank: Bank, liquidityPool: LiquidityPool, time: Time)

object Constants {
  val R = 4
}

object Functions {

  def calcS(oracle: Oracle): Double = {
    oracle.p / Constants.R
  }

  def safeE(pool: LiquidityPool, bank: Bank, oracle: Oracle): Double = {
    val s = calcS(oracle)
    Math.sqrt(pool.erg * pool.usd / s.toDouble) + bank.O / s.toDouble
  }

  def safeE(st: State): Double = safeE(st.liquidityPool, st.bank, st.time)

  // mint functions
  def arbMintPossible(pool: LiquidityPool, oracle: Oracle): Boolean = {
    oracle.p > pool.poolPrice
  }

  def arbMint(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle): Bank = {
    val amtUSD = Math.sqrt(oracle.p * liquidityPool.erg * liquidityPool.usd) - liquidityPool.usd
    val amtERG = amtUSD / oracle.p * 1.003
    Bank(bank.E + amtERG, bank.O + amtUSD)
  }

  def arb(bank: Bank, liquidityPool: LiquidityPool, targetPrice: Double): (Bank, LiquidityPool) = {
    val amtUSD = Math.sqrt(targetPrice * liquidityPool.erg * liquidityPool.usd) - liquidityPool.usd
    val newBank = Bank(bank.E, bank.O - amtUSD)
    val newE = liquidityPool.erg * liquidityPool.usd / (liquidityPool.usd + amtUSD)
    val newPool = LiquidityPool(newE, liquidityPool.usd + amtUSD)
    newBank -> newPool
  }

  def ergInterventionNeeded(pool: LiquidityPool, bank: Bank, oracle: Oracle): Boolean = {
    (pool.poolPrice / oracle.p > 1.02) && bank.E > 0
  }

  def ergIntervention(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle): (Bank, LiquidityPool) = {
    val amtERG = Math.max(Math.min(
      Math.sqrt(liquidityPool.erg * liquidityPool.usd / oracle.p) - liquidityPool.erg,
      bank.E
    ), bank.E / 100)
    val newBank = Bank(bank.E - amtERG, bank.O)
    val newU = liquidityPool.erg * liquidityPool.usd / (liquidityPool.erg + amtERG)
    val newPool = LiquidityPool(liquidityPool.erg + amtERG, newU)
    newBank -> newPool
  }

  def burnNeeded(pool: LiquidityPool, oracle: Oracle): Boolean = {
    pool.poolPrice / oracle.p > 1.05
  }

  def burn(pool: LiquidityPool, oracle: Oracle): LiquidityPool = {
    LiquidityPool(pool.erg, oracle.p * pool.erg)
  }

  def freeMint(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle): Bank = {
    val amtUSD = liquidityPool.usd / 100
    val amtERG = amtUSD / oracle.p * 1.003
    Bank(bank.E + amtERG, bank.O + amtUSD)
  }
}


object DexySimulation extends App {

  import Functions._

  val CoinsInOneErgo = 1000000000L
  val CentsInOneUSD = 100L

  val p = 2000
  val lp = LiquidityPool(10000, 20000)
  val bank = Bank(10000, 0)
  val time = Time(p, 1)

  val initialState = State(bank, lp, time)

  (2 to 100000).foldLeft(initialState) { case (st, epoch) =>
    println("price: " + st.time.p + " safe E: " + safeE(st) + " bank: " + st.bank + " pool: " + st.liquidityPool)

    val direction = Random.nextBoolean()
    val delta = Random.nextDouble() / 50 * st.time.p
    val price = if (direction) {
      st.time.p + delta
    } else {
      st.time.p - delta
    }

    val newTime = Time(price, epoch)

    val (beforeBank, beforePool) = if (arbMintPossible(st.liquidityPool, newTime)) {
      println("ARB, pool price: " + st.liquidityPool.poolPrice)
      arb(arbMint(st.bank, st.liquidityPool, newTime), st.liquidityPool, newTime.p)
    } else if (ergInterventionNeeded(st.liquidityPool, st.bank, newTime)) {
      println("INTERVENTION, pool price: " + st.liquidityPool.poolPrice)
      ergIntervention(st.bank, st.liquidityPool, newTime)
    } else if (burnNeeded(st.liquidityPool, newTime)) {
      println("BURN!!!")
      st.bank -> burn(st.liquidityPool, newTime)
    } else {
      println("Free mint!")
      freeMint(st.bank, st.liquidityPool, newTime) -> st.liquidityPool
    }

    val (newBank, newPool) = if (Random.nextInt(20) == 0 && beforeBank.O.toInt > 0) {
      val rnd = Random.nextInt(beforeBank.O.toInt)
      val tradeAmount = Math.min(Math.min(rnd, beforeBank.O / 10), beforePool.usd / 10)
      val newBank = Bank(beforeBank.E, beforeBank.O - tradeAmount)

      val newPoolUsd = beforePool.usd + tradeAmount
      val newPoolErg = beforePool.erg * beforePool.usd / newPoolUsd
      println(s"Dumping $tradeAmount USD, pool price after dump: ${newPoolUsd / newPoolErg}" )
      newBank -> LiquidityPool(newPoolErg, newPoolUsd)
    } else {
      beforeBank -> beforePool
    }

    State(newBank, newPool, newTime)
  }
}
