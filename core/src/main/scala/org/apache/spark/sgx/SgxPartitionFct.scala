package org.apache.spark.sgx

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.spark.rdd.RDD
import org.apache.spark.Partitioner

object SgxPartitionFct {

    def defaultPartitioner(rddId: Int, otherIds: Int*) =
        new DefaultPartitioner(rddId, otherIds: _*).send()

    private abstract class SgxTaskPartitioner extends SgxMessage[Partitioner] {
        override def toString = this.getClass.getSimpleName
    }

    private case class DefaultPartitioner(rddId: Int, otherIds: Int*) extends SgxTaskPartitioner {
        def execute() = Await.result( Future {
            val rdd = SgxMain.rddIds.get(rddId).asInstanceOf[RDD[_]]
            val others = otherIds.map(SgxMain.rddIds.get(_).asInstanceOf[RDD[_]])
            Partitioner.defaultPartitioner(rdd, others: _*)
        }, Duration.Inf)
    }
}
