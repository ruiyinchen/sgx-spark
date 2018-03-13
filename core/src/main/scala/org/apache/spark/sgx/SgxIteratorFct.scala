package org.apache.spark.sgx

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

import org.apache.spark.util.random.RandomSampler
import org.apache.spark.util.collection.PartitionedAppendOnlyMap
import org.apache.spark.util.collection.SizeTrackingAppendOnlyMap
import org.apache.spark.util.collection.SizeTrackingAppendOnlyMapIdentifier

import org.apache.spark.Partitioner
import org.apache.spark.TaskContext
import org.apache.spark.sgx.iterator.SgxIteratorConsumer
import org.apache.spark.sgx.iterator.SgxIteratorProviderIdentifier
import org.apache.spark.sgx.iterator.SgxIteratorIdentifier
import org.apache.spark.sgx.iterator.SgxFakeIterator

object SgxIteratorFct {
	def computeMapPartitionsRDD[U, T](id: SgxIteratorIdentifier[T], fct: (Int, Iterator[T]) => Iterator[U], partIndex: Int) =
		new ComputeMapPartitionsRDD[U, T](id, fct, partIndex).send()

	def computePartitionwiseSampledRDD[T, U](it: SgxIteratorIdentifier[T], sampler: RandomSampler[T, U]) =
		new ComputePartitionwiseSampledRDD[T, U](it, sampler).send()

	def computeZippedPartitionsRDD2[A, B, Z](a: SgxIteratorIdentifier[A], b: SgxIteratorIdentifier[B], fct: (Iterator[A], Iterator[B]) => Iterator[Z]) =
		new ComputeZippedPartitionsRDD2[A, B, Z](a, b, fct).send()

	def externalAppendOnlyMapInsertAll[K,V,C](
			entries2: SgxIteratorIdentifier[Product2[K, V]],
			mapId: SizeTrackingAppendOnlyMapIdentifier,
			mergeValue: (C, V) => C,
			createCombiner: V => C) =
		new ExternalAppendOnlyMapInsertAll[K,V,C](entries2, mapId, mergeValue, createCombiner).send()

	def externalSorterInsertAllCombine[K,V,C](
			records: SgxIteratorIdentifier[Product2[K, V]],
			mapId: SizeTrackingAppendOnlyMapIdentifier,
			mergeValue: (C, V) => C,
			createCombiner: V => C,
			shouldPartition: Boolean,
			partitioner: Option[Partitioner]) =
		new ExternalSorterInsertAllCombine[K,V,C](records, mapId, mergeValue, createCombiner, shouldPartition, partitioner).send()

	def resultTaskRunTask[T,U](id: SgxIteratorIdentifier[T], func: (TaskContext, Iterator[T]) => U, context: TaskContext) =
		new ResultTaskRunTask[T,U](id, func, context).send()

	def resultTaskRunTaskAfterShuffle[T,U](id: SgxIteratorIdentifier[T], func: (TaskContext, Iterator[T]) => U, context: TaskContext) =
		new ResultTaskRunTaskAfterShuffle[T,U](id, func, context).send()
}

private case class ComputeMapPartitionsRDD[U, T](
	id: SgxIteratorIdentifier[T],
	fct: (Int, Iterator[T]) => Iterator[U],
	partIndex: Int)
	extends SgxMessage[Iterator[U]] {

	def execute() = SgxFakeIterator(
		Await.result(Future {
			fct(partIndex, id.getIterator)
		}, Duration.Inf)
	)

	override def toString = this.getClass.getSimpleName + "(fct=" + fct + ", partIndex=" + partIndex + ", id=" + id + ")"
}

private case class ComputePartitionwiseSampledRDD[T, U](
	it: SgxIteratorIdentifier[T],
	sampler: RandomSampler[T, U]) extends SgxMessage[Iterator[U]] {

	def execute() = SgxFakeIterator(
		Await.result( Future {
			sampler.sample(it.getIterator)
		}, Duration.Inf)
	)

	override def toString = this.getClass.getSimpleName + "(sampler=" + sampler + ", it=" + it + ")"
}

private case class ComputeZippedPartitionsRDD2[A, B, Z](
	a: SgxIteratorIdentifier[A],
	b: SgxIteratorIdentifier[B],
	fct: (Iterator[A], Iterator[B]) => Iterator[Z]) extends SgxMessage[Iterator[Z]] {

	def execute() = SgxFakeIterator(
		Await.result( Future {
			fct(a.getIterator, b.getIterator)
		}, Duration.Inf)
	)

	override def toString = this.getClass.getSimpleName + "(fct=" + fct + " (" + fct.getClass.getSimpleName + "), a=" + a + ", b=" + b + ")"
}

private case class ExternalAppendOnlyMapInsertAll[K,V,C](
	entries2: SgxIteratorIdentifier[Product2[K, V]],
	mapId: SizeTrackingAppendOnlyMapIdentifier,
	mergeValue: (C, V) => C,
	createCombiner: V => C) extends SgxMessage[Unit] {

	def execute() = Await.result(Future {
	  try {
		val entries = entries2.getIterator
		val currentMap = mapId.getMap[K,C]
		var _peakMemoryUsedBytes = 0L

		var curEntry: Product2[K, V] = null
		val update: (Boolean, C) => C = (hadVal, oldVal) => {
			if (hadVal) mergeValue(oldVal, curEntry._2) else createCombiner(curEntry._2)
		}

		while (entries.hasNext) {
			curEntry = entries.next()
			val estimatedSize = currentMap.estimateSize()
			if (estimatedSize > _peakMemoryUsedBytes) {
				_peakMemoryUsedBytes = estimatedSize
			}
//			if (maybeSpill(currentMap, estimatedSize)) { // make ocall
//				currentMap = new SizeTrackingAppendOnlyMap[K, C]
//			}
//			logDebug("changeValue: " + curEntry._1.asInstanceOf[Encrypted].decrypt[K])
//			currentMap.changeValue(curEntry._1.asInstanceOf[Encrypted].decrypt[K], update)
			logDebug("changeValue: " + curEntry._1)
			currentMap.changeValue(curEntry._1, update)
//			addElementsRead() // make ocall
		}
	  } catch {
	    case e: Exception =>
	      logDebug(e.getMessage)
	      logDebug(e.getStackTraceString)
	  }
	}, Duration.Inf)
}

private case class ExternalSorterInsertAllCombine[K,V,C](
	records2: SgxIteratorIdentifier[Product2[K, V]],
	mapId: SizeTrackingAppendOnlyMapIdentifier,
	mergeValue: (C, V) => C,
	createCombiner: V => C,
	shouldPartition: Boolean,
	partitioner: Option[Partitioner]) extends SgxMessage[Unit] {

	def execute() = Await.result(Future {
	  logDebug("ZZZZ 0")
		val records = records2.getIterator
		logDebug("ZZZZ 1")
	  try {
	    mapId.getMap[K,C]
	  }
	  catch {
	    case e:Exception => 
	      logDebug(e.getMessage)
	      logDebug(e.getStackTraceString)
	  }
		val map = mapId.getMap[K,C].asInstanceOf[PartitionedAppendOnlyMap[K,C]]
	  logDebug("ZZZZ 2")
		logDebug("ExternalSorterInsertAllCombine: " + records2 + ", " + mapId)
		var kv: Product2[K, V] = null
		val update = (hadValue: Boolean, oldValue: C) => {
			if (hadValue) mergeValue(oldValue, kv._2) else createCombiner(kv._2)
		}
		while (records.hasNext) {
//			addElementsRead() // make ocall
        	kv = records.next()
        	map.changeValue((if (shouldPartition) partitioner.get.getPartition(kv._1) else 0, kv._1), update)
			logDebug("ExternalSorterInsertAllCombine: changeValue("+(if (shouldPartition) partitioner.get.getPartition(kv._1) else 0)+","+kv._1+") to " + update)
//			maybeSpillCollection(usingMap = true) // make ocall
		}
	}, Duration.Inf)//.asInstanceOf[U]
}

private case class ResultTaskRunTask[T,U](
	id: SgxIteratorIdentifier[T],
	func: (TaskContext, Iterator[T]) => U,
	context: TaskContext) extends SgxMessage[U] {

	def execute() = Await.result(Future {
	  val it = id.getIterator
//	  it match {
//	    case i: Iterator[Pair[Pair[Pair[Any,Any],Any],Any]] => func(context, i.map(c => (c._1._1._2, c._1._2)).asInstanceOf[Iterator[T]])
//	    case a: Any => func(context, a) 
//	  }
	  //					val l = list.map(n => n.decrypt[T])
//					if (l.size > 0 && l.front.isInstanceOf[Pair[Any,Any]] && l.front.asInstanceOf[Pair[Any,Any]]._2.isInstanceOf[SgxFakePairIndicator]) {
//					  l.map(c => c.asInstanceOf[Pair[Any,SgxFakePairIndicator]]._1.asInstanceOf[T])
//					} else l
	  func(context, it)
	}, Duration.Inf).asInstanceOf[U]

	override def toString = this.getClass.getSimpleName + "(id=" + id + ", func=" + func + ", context=" + context + ")"
}

private case class ResultTaskRunTaskAfterShuffle[T,U](
	id: SgxIteratorIdentifier[T],
	func: (TaskContext, Iterator[T]) => U,
	context: TaskContext) extends SgxMessage[U] {

	def execute() = Await.result(Future {
		try {
			logDebug("AAAA foobar1")
			id.getIterator.foreach(x => logDebug("foobar " + x))
			val x= func(context, id.getIterator.asInstanceOf[Iterator[Pair[Encrypted,Any]]].map(_._1.decrypt[Pair[Pair[Any,Any],Any]]).map(c => (c._1._2, c._2)).asInstanceOf[Iterator[T]])
			logDebug("AAAA foobar2")
			x
//		func(context, id.getIterator.asInstanceOf[Iterator[Pair[Encrypted,Any]]].map(_._1.decrypt[Pair[Pair[Any,Any],Any]]).map(c => (c._1._2, c._2)).asInstanceOf[Iterator[T]])
		} catch {
			case e: Exception =>
				logDebug(e.getMessage)
				logDebug(e.getStackTraceString)
		}
	}, Duration.Inf).asInstanceOf[U]

	override def toString = this.getClass.getSimpleName + "(id=" + id + ", func=" + func + ", context=" + context + ")"
}
