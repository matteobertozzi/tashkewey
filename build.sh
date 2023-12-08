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

PROJECT_IDS="."

op_type="BUILD"
mvn_cmds="clean install -DskipTests"
if [ $# -ge 1 ]; then
  case "$1" in
    "--build")
      shift
      ;;
    "--test")
      op_type="TEST"
      mvn_cmds="clean test"
      shift
      ;;
    "--deploy")
      op_type="DEPLOY"
      mvn_cmds="clean deploy"
      shift
      ;;
    "--help")
      echo "build.sh [type] [projectName, ...]"
      echo "    --build   build projects (default)"
      echo "    --test    run tests"
      echo "    --help    show this help"
      exit 0
      ;;
  esac
fi

if [ $# -lt 1 ]; then
  build_ids=$PROJECT_IDS
else
  build_ids=$*
fi

root_dir=`pwd`
echo "Run ${op_type}"
echo " - mvn: ${mvn_cmds}"
echo " - root dir: ${root_dir}"
echo " - projects: ${build_ids}"
for project in $build_ids; do
  cd "${root_dir}/${project}" 2> /dev/null
  if [ $? -ne 0 ]; then
    echo " * unable to build ${project}. dir not found"
    continue
  fi

  git_hash=`git describe --always --dirty --match "NOT A TAG"`
  git_branch=`git branch --no-color --show-current`
  echo "${op_type} ${project} (${git_hash} ${git_branch})"
  mvn -Dgit.hash=${git_hash} -Dgit.branch=${git_branch} ${mvn_cmds}
  build_res=$?
  if [ $build_res -ne 0 ]; then
    echo "failed $build_res";
    exit 1
  fi
done
