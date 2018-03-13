package org.apache.spark.sgx.iterator

import org.apache.spark.sgx.ClientHandle
import org.apache.spark.sgx.IdentifierManager
import org.apache.spark.sgx.SgxFactory
import org.apache.spark.sgx.SgxFct
import org.apache.spark.sgx.SgxSettings
import org.apache.spark.sgx.Encrypted

import org.apache.spark.util.collection.WritablePartitionedIterator
import org.apache.spark.storage.DiskBlockObjectWriter

import org.apache.spark.internal.Logging

class SgxFakePairIndicator() extends Serializable {}

case class SgxFakeIteratorException(id: Long) extends RuntimeException("A FakeIterator is just a placeholder and not supposed to be used. (" + id + ")") {}

private object FakeIterators extends IdentifierManager[Iterator[Any]]() {}

case class SgxFakeIterator[T](@transient val delegate: Iterator[T]) extends Iterator[T] with SgxIterator[T] with SgxIteratorIdentifier[T] with Logging {

	val id = scala.util.Random.nextLong

	FakeIterators.put(id, delegate)

	override def hasNext: Boolean = throw SgxFakeIteratorException(id)

	override def next: T = throw SgxFakeIteratorException(id)

	def access(): Iterator[T] = new SgxIteratorConsumer(ClientHandle.sendRecv[SgxIteratorProviderIdentifier[T]](MsgAccessFakeIterator(this)))

	def provide() = SgxFactory.newSgxIteratorProvider[Any](FakeIterators.remove(id), true).getIdentifier

	override def getIdentifier = this

	override def getIterator = {
	  new Iterator[T] with Logging {
	    val i = FakeIterators.remove[Iterator[T]](id)
	    
	    override def next = {
	      val n = i.next()
	  //					val l = list.map(n => n.decrypt[T])
//					if (l.size > 0 && l.front.isInstanceOf[Pair[Any,Any]] && l.front.asInstanceOf[Pair[Any,Any]]._2.isInstanceOf[SgxFakePairIndicator]) {
//					  l.map(c => c.asInstanceOf[Pair[Any,SgxFakePairIndicator]]._1.asInstanceOf[T])
//					} else l
	      val x= if (n != null && n.isInstanceOf[Pair[Any,Any]] && n.asInstanceOf[Pair[Any,Any]]._2.isInstanceOf[SgxFakePairIndicator]) {
	        n.asInstanceOf[Pair[Encrypted,SgxFakePairIndicator]]._1.decrypt[T]
	      } else n
	      logDebug("next: " + x)
	      x
	    }
	    
	    override def hasNext = i.hasNext
	  }
	}

	override def toString = this.getClass.getSimpleName + "(id=" + id + ")"
}

private object WritablePartitionedFakeIterators extends IdentifierManager[WritablePartitionedIterator]() {}

case class SgxWritablePartitionedFakeIterator[K,V](@transient val delegate: WritablePartitionedIterator) extends WritablePartitionedIterator with Logging {

	// delegate lives inside enclave

	val id = scala.util.Random.nextLong

	WritablePartitionedFakeIterators.put(id, delegate)

	override def writeNext(writer: DiskBlockObjectWriter) = {
		if (SgxSettings.IS_DRIVER) {
			logDebug("writeNext DRIVER")
			delegate.writeNext(writer)
		}
		else if (SgxSettings.IS_WORKER) {
			logDebug("writeNext WORKER: " + writer)
			val cur = SgxFct.writablePartitionedIteratorGetNext[K,V,((Int,K),V)](this)
//			writer.write(cur._1._2, cur._2)
			writer.write(cur, new SgxFakePairIndicator())
		}
		else {
			logDebug("writeNext MISC")
			throw SgxFakeIteratorException(id)
		}
	}

	override def hasNext: Boolean = {
		if (SgxSettings.IS_DRIVER) {
			logDebug("hasNext DRIVER")
			delegate.hasNext()
		}
		else if (SgxSettings.IS_WORKER) {
			logDebug("hasNext WORKER")
			SgxFct.writablePartitionedIteratorHasNext(this)
		}
		else {
			logDebug("hasNext MISC")
			throw SgxFakeIteratorException(id)
		}
	}

	override def nextPartition() = {
		if (SgxSettings.IS_DRIVER) {
			logDebug("nextPartition DRIVER")
			delegate.nextPartition()
		}
		else if (SgxSettings.IS_WORKER) {
			logDebug("nextPartition WORKER")
			SgxFct.writablePartitionedIteratorNextPartition(this)
		}
		else {
			logDebug("nextPartition MISC")
			throw SgxFakeIteratorException(id)
		}
	}

	override def getNext[T]() = {
		if (SgxSettings.IS_DRIVER) {
			logDebug("getNext DRIVER")
			delegate.getNext[T]()
		}
		else if (SgxSettings.IS_WORKER) {
			logDebug("getNext WORKER")
			SgxFct.writablePartitionedIteratorGetNext[K,V,T](this)
		}
		else {
			logDebug("getNext MISC")
			throw SgxFakeIteratorException(id)
		}
	}

	def getIterator = WritablePartitionedFakeIterators.get(id)
}