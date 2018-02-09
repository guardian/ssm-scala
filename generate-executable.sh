#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $DIR
sbt assembly
cat "$DIR/generate-executable-prefix" "$DIR/target/scala-2.12/ssm.jar" > "$DIR/target/scala-2.12/ssm" 
chmod +x "$DIR/target/scala-2.12/ssm"
echo "ssm executable now available at $DIR/target/scala-2.12/ssm"
