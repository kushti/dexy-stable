package simul

import scala.util.Random

case class Bank(E: Double, O: Double)

case class LiquidityPool(e: Double, u: Double) {
  def poolPrice: Double = u / e
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

  def calcS(oracle: Oracle) = {
    oracle.p / Constants.R
  }

  def safeE(pool: LiquidityPool, bank: Bank, oracle: Oracle): Double = {
    val s = calcS(oracle)
    Math.sqrt(pool.e * pool.u / s.toDouble) + bank.O / s.toDouble
  }

  def safeE(st: State): Double = safeE(st.liquidityPool, st.bank, st.time)

  // mint functions
  def arbMintPossible(pool: LiquidityPool, oracle: Oracle): Boolean = {
    oracle.p > pool.poolPrice
  }

  def arbMint(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle): Bank = {
    val amtUSD = Math.sqrt(oracle.p * liquidityPool.e * liquidityPool.u) - liquidityPool.u
    val amtERG = amtUSD / oracle.p * 1.01
    Bank(bank.E + amtERG, bank.O + amtUSD)
  }

  def arb(bank: Bank, liquidityPool: LiquidityPool, targetPrice: Double): (Bank, LiquidityPool) = {
    val amtUSD = Math.sqrt(targetPrice * liquidityPool.e * liquidityPool.u) - liquidityPool.u
    val newBank = Bank(bank.E , bank.O - amtUSD)
    val newE = liquidityPool.e * liquidityPool.u / (liquidityPool.u + amtUSD)
    val newPool = LiquidityPool(newE, liquidityPool.u + amtUSD)
    newBank -> newPool
  }

  def ergInterventionNeeded(pool: LiquidityPool, bank: Bank, oracle: Oracle): Boolean = {
    (pool.poolPrice / oracle.p > 1.02) && bank.E > 0
  }

  def ergIntervention(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle): (Bank, LiquidityPool) = {
    val amtERG = Math.min(
      Math.sqrt(liquidityPool.e * liquidityPool.u / oracle.p) - liquidityPool.e,
      bank.E
    )
    val newBank = Bank(bank.E - amtERG, bank.O)
    val newU = liquidityPool.e * liquidityPool.u / (liquidityPool.e + amtERG)
    val newPool = LiquidityPool(liquidityPool.e + amtERG, newU)
    newBank -> newPool
  }

  def burnNeeded(pool: LiquidityPool, oracle: Oracle): Boolean = {
    pool.poolPrice / oracle.p > 1.05
  }

  def burn(pool: LiquidityPool, oracle: Oracle): LiquidityPool = {
    LiquidityPool(pool.e, oracle.p * pool.e)
  }

  def freeMintAllowed(bank: Bank, oracle: Oracle) = {
    bank.O / bank.E < oracle.p
  }

  def freeMint(bank: Bank, liquidityPool: LiquidityPool, oracle: Oracle) = {
    val amtUSD = liquidityPool.u / 100
    val amtERG = amtUSD / oracle.p
    Bank(bank.E + amtERG, bank.O + amtUSD)
  }
}


object DexySimulation extends App {
  import Functions._

  val CoinsInOneErgo = 1000000000L
  val CentsInOneUSD = 100L


  val p = 2
  val lp = LiquidityPool(10000, 20000)
  val bank = Bank(10000, 0)
  val time = Time(p, 1)

  val initialState = State(bank, lp, time)

  (2 to 10000).foldLeft(initialState){case (st, epoch) =>
    println("price: " + st.time.p + " safe E: " + safeE(st) + " bank: " + st.bank + " pool: " + st.liquidityPool)

    val direction = Random.nextBoolean()
    val delta = Random.nextDouble() / 30 * st.time.p
    val price = if(direction){st.time.p + delta * 1.06} else {st.time.p - delta}

    val newTime = Time(price, epoch)

    val (newBank, newPool) = if(arbMintPossible(st.liquidityPool, newTime)){
      println("ARB, pool price: " + st.liquidityPool.poolPrice)
      arb(arbMint(st.bank, st.liquidityPool, newTime), st.liquidityPool, newTime.p)
    } else if(ergInterventionNeeded(st.liquidityPool, st.bank, newTime)){
      println("INTERVENTION, pool price: " + st.liquidityPool.poolPrice)
      ergIntervention(st.bank, st.liquidityPool, newTime)
    } else if(burnNeeded(st.liquidityPool, newTime)) {
      println("BURN!!!")
      st.bank -> burn(st.liquidityPool, newTime)
    } else if(freeMintAllowed(st.bank, newTime)) {
      println("Free mint!")
      freeMint(st.bank, st.liquidityPool, newTime) -> st.liquidityPool
    } else {
      (st.bank, st.liquidityPool)
    }

    State(newBank, newPool, newTime)
  }
}
