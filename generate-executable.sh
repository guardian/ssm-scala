#!/bin/sh
sbt assembly
cat generate-executable-prefix target/scala-2.12/ssm.jar > target/scala-2.12/ssm 
chmod +x target/scala-2.12/ssm
echo "ssm executable now available at target/scala-2.12/ssm"
