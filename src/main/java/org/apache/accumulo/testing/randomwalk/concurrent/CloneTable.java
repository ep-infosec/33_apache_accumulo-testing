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
package org.apache.accumulo.testing.randomwalk.concurrent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.NamespaceNotFoundException;
import org.apache.accumulo.core.client.TableExistsException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.testing.randomwalk.RandWalkEnv;
import org.apache.accumulo.testing.randomwalk.State;
import org.apache.accumulo.testing.randomwalk.Test;

public class CloneTable extends Test {

  @Override
  public void visit(State state, RandWalkEnv env, Properties props) throws Exception {
    AccumuloClient client = env.getAccumuloClient();
    Random rand = state.getRandom();
    String srcTableName = state.getRandomTableName();
    String newTableName = state.getRandomTableName();
    boolean flush = rand.nextBoolean();

    try {
      log.debug("Cloning table " + srcTableName + " " + newTableName + " " + flush);
      client.tableOperations().clone(srcTableName, newTableName, flush, new HashMap<>(),
          new HashSet<>());
    } catch (TableExistsException e) {
      log.debug("Clone " + srcTableName + " failed, " + newTableName + " exists");
    } catch (TableNotFoundException e) {
      log.debug("Clone " + srcTableName + " failed, doesnt exist");
    } catch (IllegalArgumentException e) {
      log.debug("Clone: " + e);
    } catch (AccumuloException e) {
      Throwable cause = e.getCause();
      if (cause instanceof NamespaceNotFoundException)
        log.debug(
            "Clone: " + srcTableName + " to " + newTableName + " failed, namespace not found");
      else
        throw e;
    }
  }
}
