/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.examples.streaming

import java.io.File
import java.nio.charset.Charset

import com.google.common.io.Files

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Time, Seconds, StreamingContext}
import org.apache.spark.streaming.StreamingContext._
import org.apache.spark.util.IntParam

/**
 * Counts words in text encoded with UTF8 received from the network every second.
 *
 * Usage: NetworkWordCount <hostname> <port> <checkpoint-directory> <output-file>
 *   <hostname> and <port> describe the TCP server that Spark Streaming would connect to receive
 *   data. <checkpoint-directory> directory to HDFS-compatible file system which checkpoint data
 *   <output-file> file to which the word counts will be appended
 *
 * In local mode, <master> should be 'local[n]' with n > 1
 * <checkpoint-directory> and <output-file> must be absolute paths
 *
 *
 * To run this on your local machine, you need to first run a Netcat server
 *
 *      `$ nc -lk 9999`
 *
 * and run the example as
 *
 *      `$ ./bin/spark-submit examples.jar \
 *      --class org.apache.spark.examples.streaming.RecoverableNetworkWordCount \
 *              localhost 9999 ~/checkpoint/ ~/out`
 *
 * If the directory ~/checkpoint/ does not exist (e.g. running for the first time), it will create
 * a new StreamingContext (will print "Creating new context" to the console). Otherwise, if
 * checkpoint data exists in ~/checkpoint/, then it will create StreamingContext from
 * the checkpoint data.
 *
 * To run this example in a local standalone cluster with automatic driver recovery,
 *
 *      `$ ./spark-class org.apache.spark.deploy.Client -s launch <cluster-url> \
 *              <path-to-examples-jar> \
 *              org.apache.spark.examples.streaming.RecoverableNetworkWordCount <cluster-url> \
 *              localhost 9999 ~/checkpoint ~/out`
 *
 * <path-to-examples-jar> would typically be
 * <spark-dir>/examples/target/scala-XX/spark-examples....jar
 *
 * Refer to the online documentation for more details.
 */

object RecoverableNetworkWordCount {

  def createContext(ip: String, port: Int, outputPath: String) = {

    // If you do not see this printed, that means the StreamingContext has been loaded
    // from the new checkpoint
    println("Creating new context")
    val outputFile = new File(outputPath)
    if (outputFile.exists()) outputFile.delete()
    val sparkConf = new SparkConf().setAppName("RecoverableNetworkWordCount")
    // Create the context with a 1 second batch size
    val ssc = new StreamingContext(sparkConf, Seconds(1))

    // Create a NetworkInputDStream on target ip:port and count the
    // words in input stream of \n delimited text (eg. generated by 'nc')
    val lines = ssc.socketTextStream(ip, port)
    val words = lines.flatMap(_.split(" "))
    val wordCounts = words.map(x => (x, 1)).reduceByKey(_ + _)
    wordCounts.foreachRDD((rdd: RDD[(String, Int)], time: Time) => {
      val counts = "Counts at time " + time + " " + rdd.collect().mkString("[", ", ", "]")
      println(counts)
      println("Appending to " + outputFile.getAbsolutePath)
      Files.append(counts + "\n", outputFile, Charset.defaultCharset())
    })
    ssc
  }

  def main(args: Array[String]) {
    if (args.length != 4) {
      System.err.println("You arguments were " + args.mkString("[", ", ", "]"))
      System.err.println(
        """
          |Usage: RecoverableNetworkWordCount <hostname> <port> <checkpoint-directory>
          |     <output-file>. <hostname> and <port> describe the TCP server that Spark
          |     Streaming would connect to receive data. <checkpoint-directory> directory to
          |     HDFS-compatible file system which checkpoint data <output-file> file to which the
          |     word counts will be appended
          |
          |In local mode, <master> should be 'local[n]' with n > 1
          |Both <checkpoint-directory> and <output-file> must be absolute paths
        """.stripMargin
      )
      System.exit(1)
    }
    val Array(ip, IntParam(port), checkpointDirectory, outputPath) = args
    val ssc = StreamingContext.getOrCreate(checkpointDirectory,
      () => {
        createContext(ip, port, outputPath)
      })
    ssc.start()
    ssc.awaitTermination()
  }
}
