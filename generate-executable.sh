#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SCALA_FOLDER="scala-3.7.0"
cd $DIR
sbt assembly
cat "$DIR/generate-executable-prefix" "$DIR/target/$SCALA_FOLDER/ssm.jar" > "$DIR/target/$SCALA_FOLDER/ssm"
chmod +x "$DIR/target/$SCALA_FOLDER/ssm"
echo "ssm executable now available at $DIR/target/$SCALA_FOLDER/ssm"
cd "$DIR/target/$SCALA_FOLDER"
tar -czf ssm.tar.gz ssm
echo "ssm tar.zg file now available at $DIR/target/$SCALA_FOLDER/ssm.tar.gz"
echo "ssm.tar.gz sha256:"
shasum -a 256 ssm.tar.gz
