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

# Arguments

Refer to the program's help to see the required arguments, but here is
some info about some of them.

Note that the AWS profile to use is required, while region will
default to `eu-west-1` (Ireland).

## Execution targets

`ssm` needs to be told which instances should execute the provided
command(s). You can do this by specifying instance IDs, or by
specifying App, Stack, and Stage tags.

    # by ID
	... --instances i-0123456,i-9876543
    ... -i i-0123456,i-9876543
	
	# by tag
	... --ass-tags app,stack,stage
	... -t app,stack,stage

If you provide tags, `ssm` will search for running instances that are
have those tags.

# Actions

## One-Off Commands

You can tell `ssm` the commands to execute with an argument that
specifies the command or by providing a file that contains the
commands to be run.

    # specify command
	--cmd ls
	-c ls
	
	# provide script file
	--src-file script
	-f script

## Command Loop (REPL)

With the `-I` interactive flag, `ssm` will initialise a list of instances and then
wait for commands to be specified.  Each command will be executed on all instances and
the user can select the instance to display.

## SSH

Specifying the `-s` flag will cause `ssm` to generate a temporary ssh key, and install the public key
on a specific instance.  It will then output the command to `ssh` directly to that instance.

The instance must already have both a public IP address _and_ appropriate security groups.