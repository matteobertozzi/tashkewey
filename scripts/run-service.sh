#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

if [ -z "${TASHKEWEY_SERVICE_ROOT}" ]; then
  echo "env variable TASHKEWEY_SERVICE_ROOT not set"
  exit 1
fi

if [ -z "${JAVA_MAX_HEAP_SIZE}" ]; then
  JAVA_MAX_HEAP_SIZE="1g"
  JAVA_MAX_DMEM_SIZE="512m"
fi

# JDK Flags
JDK_FLAGS="-Dfile.encoding=UTF8 -XX:+ShowCodeDetailsInExceptionMessages"
JDK_FLAGS="${JDK_FLAGS} -XX:+UnlockExperimentalVMOptions"
JDK_FLAGS="${JDK_FLAGS} -XX:+UseShenandoahGC -XX:ShenandoahMinFreeThreshold=40 -XX:ShenandoahInitFreeThreshold=20 -XX:ShenandoahUncommitDelay=10000 -XX:ShenandoahGuaranteedGCInterval=60000"
#JDK_FLAGS="${JDK_FLAGS} -XX:+UseZGC -XX:+ZGenerational -XX:ZUncommitDelay=120"
JDK_FLAGS="${JDK_FLAGS} -Xms16m -Xmx${JAVA_MAX_HEAP_SIZE} -XX:MaxDirectMemorySize=${JAVA_MAX_DMEM_SIZE}"
#JDK_FLAGS="${JDK_FLAGS} -XX:+PrintFlagsFinal"

# Netty Flags
JDK_FLAGS="${JDK_FLAGS} -Dio.netty.maxDirectMemory=0"

# Easer Insights Flags
JDK_FLAGS="${JDK_FLAGS} -Deaser.insights.jdbc.pool.trace.connections=true -Deaser.insights.jdbc.pool.max.connections.per.thread=2"

TASHKEWEY_CLASSPATH="${TASHKEWEY_SERVICE_ROOT}/modules/*:${TASHKEWEY_SERVICE_ROOT}/lib/*"
if [ "${IS_AWS_SYS}" = "false" ]; then
  ls ${TASHKEWEY_SERVICE_ROOT}/modules
fi

java ${JDK_FLAGS} -cp ${TASHKEWEY_CLASSPATH} io.github.matteobertozzi.tashkewey.Main -c ${TASHKEWEY_SERVICE_ROOT}/config.json
