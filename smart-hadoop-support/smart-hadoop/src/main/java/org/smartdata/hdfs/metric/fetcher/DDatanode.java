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
package org.smartdata.hdfs.metric.fetcher;

import com.google.common.base.Preconditions;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.apache.hadoop.util.Time;
import org.smartdata.hdfs.action.move.PendingMove;
import org.smartdata.hdfs.action.move.Source;
import org.smartdata.hdfs.action.move.StorageGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A class that keeps track of a datanode. */
public class DDatanode {
  final DatanodeInfo datanode;
  private final Map<String, Source> sourceMap;
  private final Map<String, StorageGroup> targetMap;
  private long delayUntil = 0L;
  /** blocks being moved but not confirmed yet */
  private final List<PendingMove> pendings;
  private volatile boolean hasFailure = false;
  private final int maxConcurrentMoves;

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + datanode;
  }

  public DDatanode(DatanodeInfo datanode, int maxConcurrentMoves) {
    this.datanode = datanode;
    this.sourceMap = new HashMap<>();
    this.targetMap = new HashMap<>();
    this.maxConcurrentMoves = maxConcurrentMoves;
    this.pendings = new ArrayList<>(maxConcurrentMoves);
  }

  public DatanodeInfo getDatanodeInfo() {
    return datanode;
  }

  private static <G extends StorageGroup> void put(String storageType,
      G g, Map<String, G> map) {
    final StorageGroup existing = map.put(storageType, g);
    Preconditions.checkState(existing == null);
  }

  public StorageGroup addTarget(String storageType) {
    final StorageGroup g = new StorageGroup(this.datanode, storageType);
    put(storageType, g, targetMap);
    return g;
  }

  public Source addSource(String storageType) {
    final Source s = new Source(storageType, this.getDatanodeInfo());
    put(storageType, s, sourceMap);
    return s;
  }

  synchronized public void activateDelay(long delta) {
    delayUntil = Time.monotonicNow() + delta;
  }

  synchronized private boolean isDelayActive() {
    if (delayUntil == 0 || Time.monotonicNow() > delayUntil) {
      delayUntil = 0;
      return false;
    }
    return true;
  }

  /** Check if the node can schedule more blocks to move */
  synchronized boolean isPendingQNotFull() {
    return pendings.size() < maxConcurrentMoves;
  }

  /** Check if all the dispatched moves are done */
  synchronized boolean isPendingQEmpty() {
    return pendings.isEmpty();
  }

  /** Add a scheduled block move to the node */
  synchronized public boolean addPendingBlock(PendingMove pendingBlock) {
    if (!isDelayActive() && isPendingQNotFull()) {
      return pendings.add(pendingBlock);
    }
    return false;
  }

  /** Remove a scheduled block move from the node */
  synchronized public boolean removePendingBlock(PendingMove pendingBlock) {
    return pendings.remove(pendingBlock);
  }

  void setHasFailure() {
    this.hasFailure = true;
  }
}