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

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.testing.randomwalk.RandWalkEnv;
import org.apache.accumulo.testing.randomwalk.State;

public class BulkMinusOne extends BulkImportTest {

  private static final Value negOne = new Value("-1".getBytes(UTF_8));

  @Override
  protected void runLater(State state, RandWalkEnv env) throws Exception {
    log.info("Decrementing");
    BulkPlusOne.bulkLoadLots(log, state, env, negOne);
  }

}
