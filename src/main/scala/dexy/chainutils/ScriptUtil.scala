package dexy.chainutils

import org.ergoplatform.kiosk.ergo._
import org.ergoplatform.ErgoAddressEncoder.NetworkPrefix
import org.ergoplatform.{ErgoAddress, ErgoAddressEncoder}
import sigma.GroupElement
import sigmastate.Values.ErgoTree
import sigmastate.eval.CompiletimeIRContext
import sigmastate.lang.{CompilerSettings, SigmaCompiler, TransformingSigmaBuilder}

import scala.collection.mutable.{Map => MMap}

class ScriptUtil(networkPrefix: NetworkPrefix) {

  private val compiler = SigmaCompiler(CompilerSettings(networkPrefix, TransformingSigmaBuilder, lowerMethodCalls = true))

  implicit val ergoAddressEncoder: ErgoAddressEncoder = new ErgoAddressEncoder(networkPrefix)

  def getAddressFromErgoTree(ergoTree: ErgoTree) = ergoAddressEncoder.fromProposition(ergoTree).get

  def getStringFromAddress(ergoAddress: ErgoAddress): String = ergoAddressEncoder.toString(ergoAddress)

  def addIfNotExist(envMap: MMap[String, KioskType[_]], name: String, kioskType: KioskType[_]) = {
    envMap
      .get(name)
      .fold(
        envMap += name -> kioskType
      )(_ => throw new Exception(s"Variable $name is already defined"))
  }

  def compile(env: Map[String, KioskType[_]], ergoScript: String): ErgoTree = {
    import sigmastate.lang.Terms._
    implicit val irContext = new CompiletimeIRContext
    compiler.compile(env.map{case (k,v) => k -> v.value}, ergoScript).buildTree.asBoolValue.asSigmaProp
  }

  case class BetterMMap(map: MMap[String, KioskType[_]]) {
    def setCollByte(name: String, bytes: Array[Byte]) = addIfNotExist(map, name, KioskCollByte(bytes))

    def setLong(name: String, long: Long) = addIfNotExist(map, name, KioskLong(long))

    def setInt(name: String, int: Int) = addIfNotExist(map, name, KioskInt(int))

    def setGroupElement(name: String, groupElement: GroupElement) = addIfNotExist(map, name, KioskGroupElement(groupElement))
  }

  implicit def mapToBetterMMap(map: MMap[String, KioskType[_]]) = BetterMMap(map)
}

