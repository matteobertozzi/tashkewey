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

VERSION="0.2.0"

export PROJECT_IDS="tashkewey-core tashkewey-netty tashkewey-aws-lambda demo-service"
./build.sh

# Prepare dist bundle
export TASHKEWEY_SERVICE_ROOT="build-dist"
rm -rf ${TASHKEWEY_SERVICE_ROOT}
mkdir -p ${TASHKEWEY_SERVICE_ROOT}
cp -r tashkewey-netty/target/lib ${TASHKEWEY_SERVICE_ROOT}
cp demo-service/target/demo-service-${VERSION}.jar ${TASHKEWEY_SERVICE_ROOT}/lib
cp tashkewey-netty/target/tashkewey-netty-${VERSION}.jar ${TASHKEWEY_SERVICE_ROOT}/lib
cp dummy-conf.json ${TASHKEWEY_SERVICE_ROOT}/config.json

./scripts/run-service.sh
