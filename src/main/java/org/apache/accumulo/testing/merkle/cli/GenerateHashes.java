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
package org.apache.accumulo.testing.merkle.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.testing.cli.ClientOpts;
import org.apache.accumulo.testing.merkle.RangeSerialization;
import org.apache.accumulo.testing.merkle.skvi.DigestIterator;
import org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.io.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.google.common.collect.Iterables;

/**
 * Read from a table, compute a Merkle tree and output it to a table. Each key-value pair in the
 * destination table is a leaf node of the Merkle tree.
 */
public class GenerateHashes {
  private static final Logger log = LoggerFactory.getLogger(GenerateHashes.class);

  public static class GenerateHashesOpts extends ClientOpts {

    @Parameter(names = {"-t", "--table"}, required = true, description = "table to use")
    String tableName;

    @Parameter(names = {"-hash", "--hash"}, required = true, description = "type of hash to use")
    private String hashName;

    @Parameter(names = {"-o", "--output"}, required = true,
        description = "output table name, expected to exist and be writable")
    private String outputTableName;

    @Parameter(names = {"-nt", "--numThreads"}, required = false,
        description = "number of concurrent threads calculating digests")
    private int numThreads = 4;

    @Parameter(names = {"-iter", "--iterator"}, required = false,
        description = "Should we push down logic with an iterator")
    private boolean iteratorPushdown = false;

    @Parameter(names = {"-s", "--splits"}, required = false,
        description = "File of splits to use for merkle tree")
    private String splitsFile = null;

    String getHashName() {
      return hashName;
    }

    String getOutputTableName() {
      return outputTableName;
    }

    int getNumThreads() {
      return numThreads;
    }

    boolean isIteratorPushdown() {
      return iteratorPushdown;
    }

    String getSplitsFile() {
      return splitsFile;
    }

  }

  Collection<Range> getRanges(AccumuloClient client, String tableName, String splitsFile)
      throws TableNotFoundException, AccumuloSecurityException, AccumuloException,
      FileNotFoundException {
    if (null == splitsFile) {
      log.info("Using table split points");
      Collection<Text> endRows = client.tableOperations().listSplits(tableName);
      return endRowsToRanges(endRows);
    } else {
      log.info("Using provided split points");
      ArrayList<Text> splits = new ArrayList<>();

      String line;
      try (java.util.Scanner file = new java.util.Scanner(new File(splitsFile), UTF_8.name())) {
        while (file.hasNextLine()) {
          line = file.nextLine();
          if (!line.isEmpty()) {
            splits.add(new Text(line));
          }
        }
      }

      Collections.sort(splits);
      return endRowsToRanges(splits);
    }
  }

  public void run(GenerateHashesOpts opts) throws TableNotFoundException, AccumuloSecurityException,
      AccumuloException, NoSuchAlgorithmException, FileNotFoundException {
    try (AccumuloClient client = Accumulo.newClient().from(opts.getClientProps()).build()) {
      Collection<Range> ranges = getRanges(client, opts.tableName, opts.getSplitsFile());
      run(client, opts.tableName, opts.getOutputTableName(), opts.getHashName(),
          opts.getNumThreads(), opts.isIteratorPushdown(), ranges);
    }
  }

  public void run(final AccumuloClient client, final String inputTableName,
      final String outputTableName, final String digestName, int numThreads,
      final boolean iteratorPushdown, final Collection<Range> ranges)
      throws TableNotFoundException, AccumuloException, NoSuchAlgorithmException {
    if (!client.tableOperations().exists(outputTableName)) {
      throw new IllegalArgumentException(outputTableName + " does not exist, please create it");
    }

    // Get some parallelism
    ExecutorService svc = Executors.newFixedThreadPool(numThreads);

    try (BatchWriter bw = client.createBatchWriter(outputTableName)) {
      for (final Range range : ranges) {
        final MessageDigest digest = getDigestAlgorithm(digestName);

        svc.execute(() -> {
          Scanner s;
          try {
            s = client.createScanner(inputTableName, Authorizations.EMPTY);
          } catch (Exception e) {
            log.error("Could not get scanner for " + inputTableName, e);
            throw new RuntimeException(e);
          }

          s.setRange(range);

          Value v;
          Mutation m;
          if (iteratorPushdown) {
            IteratorSetting cfg = new IteratorSetting(50, DigestIterator.class);
            cfg.addOption(DigestIterator.HASH_NAME_KEY, digestName);
            s.addScanIterator(cfg);

            // The scanner should only ever return us one
            // Key-Value, otherwise this approach won't work
            Entry<Key,Value> entry = Iterables.getOnlyElement(s);

            v = entry.getValue();
            m = RangeSerialization.toMutation(range, v);
          } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (Entry<Key,Value> entry : s) {
              DataOutputStream out = new DataOutputStream(baos);
              try {
                entry.getKey().write(out);
                entry.getValue().write(out);
              } catch (Exception e) {
                log.error("Error writing {}", entry, e);
                throw new RuntimeException(e);
              }

              digest.update(baos.toByteArray());
              baos.reset();
            }

            v = new Value(digest.digest());
            m = RangeSerialization.toMutation(range, v);
          }

          // Log some progress
          log.info("{} computed digest for {} of {}", Thread.currentThread().getName(), range,
              Hex.encodeHexString(v.get()));

          try {
            bw.addMutation(m);
          } catch (MutationsRejectedException e) {
            log.error("Could not write mutation", e);
            throw new RuntimeException(e);
          }
        });
      }

      svc.shutdown();

      // Wait indefinitely for the scans to complete
      while (!svc.isTerminated()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          log.error(
              "Interrupted while waiting for executor service to gracefully complete. Exiting now");
          svc.shutdownNow();
          return;
        }
      }
    }
  }

  private TreeSet<Range> endRowsToRanges(Collection<Text> endRows) {
    ArrayList<Text> sortedEndRows = new ArrayList<>(endRows);
    Collections.sort(sortedEndRows);

    Text prevEndRow = null;
    TreeSet<Range> ranges = new TreeSet<>();
    for (Text endRow : sortedEndRows) {
      if (null == prevEndRow) {
        ranges.add(new Range(null, false, endRow, true));
      } else {
        ranges.add(new Range(prevEndRow, false, endRow, true));
      }
      prevEndRow = endRow;
    }

    ranges.add(new Range(prevEndRow, false, null, false));

    return ranges;
  }

  private MessageDigest getDigestAlgorithm(String digestName) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(digestName);
  }

  public static void main(String[] args) throws Exception {
    GenerateHashesOpts opts = new GenerateHashesOpts();
    opts.parseArgs(GenerateHashes.class.getName(), args);

    if (opts.isIteratorPushdown() && null != opts.getSplitsFile()) {
      throw new IllegalArgumentException(
          "Cannot use iterator pushdown with anything other than table split points");
    }

    GenerateHashes generate = new GenerateHashes();
    generate.run(opts);
  }
}
