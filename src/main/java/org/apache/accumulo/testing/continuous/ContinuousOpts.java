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
package org.apache.accumulo.testing.continuous;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;

/**
 * Common CLI arguments for the Continuous Ingest suite.
 */
public class ContinuousOpts {

  public static class ShortConverter implements IStringConverter<Short> {
    @Override
    public Short convert(String value) {
      return Short.valueOf(value);
    }
  }

  @Parameter(names = "--min", description = "lowest random row number to use")
  long min = 0;

  @Parameter(names = "--max", description = "maximum random row number to use")
  long max = Long.MAX_VALUE;

  @Parameter(names = "--num", description = "the number of entries to ingest")
  long num = Long.MAX_VALUE;

  @Parameter(names = "--maxColF", description = "maximum column family value to use",
      converter = ShortConverter.class)
  short maxColF = Short.MAX_VALUE;

  @Parameter(names = "--maxColQ", description = "maximum column qualifier value to use",
      converter = ShortConverter.class)
  short maxColQ = Short.MAX_VALUE;

  @Parameter(names = "--addCheckSum", description = "turn on checksums")
  boolean checksum = false;

  @Parameter(names = "--visibilities",
      description = "read the visibilities to ingest with from a file")
  String visFile = null;
}
