@Echo OFF
Rem Copyright 2013, Qualcomm Innovation Center, Inc.
Rem
Rem    Licensed under the Apache License, Version 2.0 (the "License");
Rem    you may not use this file except in compliance with the License.
Rem    You may obtain a copy of the License at
Rem
Rem        http://www.apache.org/licenses/LICENSE-2.0
Rem
Rem    Unless required by applicable law or agreed to in writing, software
Rem    distributed under the License is distributed on an "AS IS" BASIS,
Rem    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
Rem    See the License for the specific language governing permissions and
Rem    limitations under the License.

setlocal

Rem Various names and paths
set SOURCE_PATH=src\
set MAIN_CLASS=AJGenerateCode
set MAIN_CLASS_FILE=%SOURCE_PATH%%MAIN_CLASS%.java

set TMP_MANIFEST=manifest
set OUTPUT_PATH=%CD%\

Rem If there is a command line argument it is expected to be the output path
if "%1"=="" (
        echo No output path specified, creating jar file in current directory:
        echo %OUTPUT_PATH%
) else (
        set OUTPUT_PATH=%1
)

for /F %%n in (%SOURCE_PATH%\config\name) do (
        set EXE_FILE_NAME=%%n
)

set EXE_FILE=%OUTPUT_PATH%%EXE_FILE_NAME%
set JAR_FILE_NAME=%EXE_FILE_NAME%
set JAR_FILE=%OUTPUT_PATH%%JAR_FILE_NAME%

echo Compiling...
javac -cp %SOURCE_PATH% -d %CD% %MAIN_CLASS_FILE%
if errorlevel 1 goto :EOF
echo Done compiling!

echo Creating jar file: %JAR_FILE_NAME%
echo Main-Class: %MAIN_CLASS% > %TMP_MANIFEST%

set javafiles=*.class
set miscfiles=%SOURCE_PATH%\c++\* %SOURCE_PATH%\config\*

jar cfm %JAR_FILE% %TMP_MANIFEST% %javafiles% %miscfiles%
if errorlevel 1 goto :EOF

Rem I cannot see that the mklink ever gets executed but
Rem it duplicates the same code build.sh which also doesn't
Rem seem to get executed.
if NOT %EXE_FILE%==%JAR_FILE% mklink %EXE_FILE% %JAR_FILE%

echo Done creating file(s)...

echo Cleaning up...
del *.class %TMP_MANIFEST%

echo Built and installed!
