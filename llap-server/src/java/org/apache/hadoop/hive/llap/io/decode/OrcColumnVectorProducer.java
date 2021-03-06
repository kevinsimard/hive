/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.llap.io.decode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.llap.cache.BufferUsageManager;
import org.apache.hadoop.hive.llap.cache.LowLevelCache;
import org.apache.hadoop.hive.llap.counters.QueryFragmentCounters;
import org.apache.hadoop.hive.llap.io.api.impl.ColumnVectorBatch;
import org.apache.hadoop.hive.llap.io.api.impl.LlapIoImpl;
import org.apache.hadoop.hive.llap.io.encoded.OrcEncodedDataReader;
import org.apache.hadoop.hive.llap.io.metadata.OrcMetadataCache;
import org.apache.hadoop.hive.llap.metrics.LlapDaemonCacheMetrics;
import org.apache.hadoop.hive.llap.metrics.LlapDaemonIOMetrics;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatchCtx;
import org.apache.hadoop.hive.ql.io.orc.encoded.Consumer;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.plan.PartitionDesc;
import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Reporter;
import org.apache.orc.TypeDescription;
import org.apache.orc.OrcConf;

public class OrcColumnVectorProducer implements ColumnVectorProducer {

  private final OrcMetadataCache metadataCache;
  private final LowLevelCache lowLevelCache;
  private final BufferUsageManager bufferManager;
  private final Configuration conf;
  private boolean _skipCorrupt; // TODO: get rid of this
  private LlapDaemonCacheMetrics cacheMetrics;
  private LlapDaemonIOMetrics ioMetrics;

  public OrcColumnVectorProducer(OrcMetadataCache metadataCache,
      LowLevelCache lowLevelCache, BufferUsageManager bufferManager,
      Configuration conf, LlapDaemonCacheMetrics cacheMetrics, LlapDaemonIOMetrics ioMetrics) {
    LlapIoImpl.LOG.info("Initializing ORC column vector producer");

    this.metadataCache = metadataCache;
    this.lowLevelCache = lowLevelCache;
    this.bufferManager = bufferManager;
    this.conf = conf;
    this._skipCorrupt = OrcConf.SKIP_CORRUPT_DATA.getBoolean(conf);
    this.cacheMetrics = cacheMetrics;
    this.ioMetrics = ioMetrics;
  }

  @Override
  public ReadPipeline createReadPipeline(
      Consumer<ColumnVectorBatch> consumer, FileSplit split, List<Integer> columnIds,
      SearchArgument sarg, String[] columnNames, QueryFragmentCounters counters,
      TypeDescription readerSchema, InputFormat<?, ?> unused0, Deserializer unused1,
      Reporter reporter, JobConf job, Map<Path, PartitionDesc> unused2) throws IOException {
    cacheMetrics.incrCacheReadRequests();
    OrcEncodedDataConsumer edc = new OrcEncodedDataConsumer(consumer, columnIds.size(),
        _skipCorrupt, counters, ioMetrics);
    // Note: we use global conf here and ignore JobConf.
    OrcEncodedDataReader reader = new OrcEncodedDataReader(lowLevelCache, bufferManager,
        metadataCache, conf, split, columnIds, sarg, columnNames, edc, counters, readerSchema);
    edc.init(reader, reader);
    return edc;
  }
}
