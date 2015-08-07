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

package org.apache.spark.sql.ui

import java.util.concurrent.atomic.AtomicInteger

import org.apache.spark.Logging
import org.apache.spark.sql.SQLContext
import org.apache.spark.ui.{SparkUI, SparkUITab}

private[sql] class SQLTab(sqlContext: SQLContext, sparkUI: SparkUI)
  extends SparkUITab(sparkUI, SQLTab.nextTabName) with Logging {

  val parent = sparkUI
  val listener = sqlContext.listener

  attachPage(new AllExecutionsPage(this))
  attachPage(new ExecutionPage(this))
  parent.attachTab(this)

  parent.addStaticHandler(SQLTab.STATIC_RESOURCE_DIR, "/static/sql")
}

private[sql] object SQLTab {

  private val STATIC_RESOURCE_DIR = "org/apache/spark/sql/ui/static"

  private val nextTabId = new AtomicInteger(0)

  private def nextTabName: String = {
    val nextId = nextTabId.getAndIncrement()
    if (nextId == 0) "SQL" else s"SQL${nextId}"
  }
}
