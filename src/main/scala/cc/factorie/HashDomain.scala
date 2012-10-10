package cc.factorie

import scala.util.MurmurHash
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import cc.factorie.la.GrowableSparseBinaryTensor1

/*
 * The expected usage is something like:
 * 
 *  object MyFeaturesDomain extends StringHashDomain(size = 100000)
 *  class MyFeatures(val v: MyVariable) extends HashingBinaryFeatureVectorVariable[String] {
 *    override def domain = MyFeaturesDomain 
 *  }
 * 
 * @author Brian Martin
 * @date 10/7/2012
 * @since 1.0
 */

class HashDomain[C](size: Int) extends DiscreteDomain(size) {
  def index(c: C) = math.abs(c.hashCode() % size)
}

class StringHashDomain(size: Int) extends HashDomain[String](size) {
  override def index(s: String) = math.abs(MurmurHash.stringHash(s) % size)
}

abstract class HashingBinaryFeatureVectorVariable[C] extends DiscreteTensorVariable {
  override def domain: HashDomain[C] = null
  def ++=(cs: Iterable[C]) = cs.map(c => tensor.update(domain.index(c), 1.0))
  def this(initVals:Iterable[C]) = { this(); initVals.map(c => domain.index(c)).foreach(this.tensor.+=(_, 1.0)) }
  set(new GrowableSparseBinaryTensor1(domain.dimensionDomain))(null)
}