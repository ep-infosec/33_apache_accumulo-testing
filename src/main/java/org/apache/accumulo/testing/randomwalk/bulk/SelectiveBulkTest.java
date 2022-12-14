/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.testing.randomwalk.bulk;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Properties;

import org.apache.accumulo.testing.randomwalk.RandWalkEnv;
import org.apache.accumulo.testing.randomwalk.State;

/**
 * Selectively runs the actual {@link BulkTest} based on the number of active TServers and the
 * number of queued operations.
 */
public abstract class SelectiveBulkTest extends BulkTest {

  @Override
  public void visit(State state, RandWalkEnv env, Properties props) throws Exception {
    if (SelectiveQueueing.shouldQueueOperation(state, env)) {
      super.visit(state, env, props);
      return;
    }
    log.debug("Skipping queueing of {} because of excessive queued tasks already",
        getClass().getSimpleName());
    log.debug("Waiting 30 seconds before continuing");
    try {
      Thread.sleep(SECONDS.toMillis(30));
    } catch (InterruptedException ignored) {}
  }

}
