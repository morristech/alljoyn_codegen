#!/bin/bash
# Copyright 2010 - 2011, Qualcomm Innovation Center, Inc.
# 
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
# 
#        http://www.apache.org/licenses/LICENSE-2.0
# 
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# various names and paths
#
SOURCE_PATH="src/"
MAIN_CLASS=AJGenerateCode
MAIN_CLASS_FILE=$SOURCE_PATH$MAIN_CLASS".java"
TMP_MANIFEST="manifest"
OUTPUT_PATH="$PWD/"
EXE_FILE_NAME=`cat $SOURCE_PATH/config/name`
EXE_FILE=$OUTPUT_PATH$EXE_FILE_NAME
JAR_FILE_NAME="$EXE_FILE_NAME"
JAR_FILE=$OUTPUT_PATH"$JAR_FILE_NAME"

# if there is a command line argument it is expected to be the output path
#
if [ -z $1 ]; then
    echo "No output path specified, creating jar file in current directory:"
    echo "$OUTPUT_PATH"
else
    OUTPUT_PATH="$1"
fi

# set -e will exit script immediately if any of the commands below it exit with
# a non-zero status
set -e

echo -n "Compiling...	"
javac -cp $SOURCE_PATH -d "$PWD" $MAIN_CLASS_FILE
echo "done compiling!"

echo "Creating jar file: '$JAR_FILE_NAME'"
echo "Main-Class: $MAIN_CLASS" > $TMP_MANIFEST
javafiles=`ls *.class`
miscfiles=`ls $SOURCE_PATH/c++/* $SOURCE_PATH/config/*`

jar cfm "$JAR_FILE" $TMP_MANIFEST $javafiles $miscfiles

# at this point failure isn't a big deal, so unset -e
set +e
chmod a+x $JAR_FILE
if [ $EXE_FILE != $JAR_FILE ]; then
    echo "Creating \"executable\" (link to jar) '$EXE_FILE_FILE'"
    ln -s $JAR_FILE $EXE_FILE
fi
echo "Done creating file(s)..."

echo "cleaning up..."
rm *.class $TMP_MANIFEST

echo
echo "Built and installed!"