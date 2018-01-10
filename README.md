Scala-SSM
=========

Scala library for executing commands on EC2 servers using EC2 Run
Command.

## Usage

When properly released, there will be a jar avaiable in the releases
section of this repository.

The program can be run using sbt, either from an sbt shell or from the
CLI in that project.

    $ sbt "run --instances i-0123456 --profile xxx --region xxx --cmd pwd"

    sbt:ssm-scala> run --instances i-0123456 --profile xxx --region xxx --cmd pwd

You can produce a `jar` from your current source code using `assembly`.

    sbt assembly

You can then write a simple wrapper script and put it on your path:

    java -jar <path-to-jar>/ssm.jar "@$"

Call it `ssm` and make sure it is executable.
