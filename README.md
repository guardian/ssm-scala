Scala-SSM
=========

Sala-SSM is a command-line tool for executing commands on EC2 servers
using EC2 Run Command, written in Scala. This can be used as an
alternative to `ssh` for servers in AWS accounts to which you have
[IAM](https://aws.amazon.com/iam/) access.

## Installation

Fetch the most recently released version of the program from the
Github releases page

[Permalink to latest release](https://github.com/guardian/ssm-scala/releases/latest)

You can then write a simple wrapper script and put it on your path:

    java -jar <path-to-jar>/ssm.jar "@$"

Call it `ssm` and make sure it is executable (`chmod +x ssm`).

## Usage

Assuming you have followed the above instructions you run the program
as follows:

    ssm <args>

### Usage examples

Likely example using short form of arguments:

    ssm -t app,stack,stage --profile <aws-profile> -c ls

REPL (interactive) mode:

    ssm -t app,stack,stage --profile <aws-profile> -I

Execute a command on all matching instances:

    ssm --ass-tags app,stack,stage --profile <aws-profile> --cmd ls

Execute the contents of a script file on matching instances:

    ssm --ass-tags app,stack,stage --profile <aws-profile> --src-file <script>

Execute `ls` on the specified instance:

    ssm --instances i-01234567 --profile <aws-profile> --cmd ls

Execute `ls` on multiple specified instances (using the short form of
the arguments):

    ssm -i i-01234567,i98765432 --profile <aws-profile> -c ls

## Arguments

Refer to the program's help to see the required arguments, but here is
some info about some of them.

Note that the AWS profile to use is required, while region will
default to `eu-west-1` (Ireland). You are also required to provide
the name of [an AWS profile](https://docs.aws.amazon.com/cli/latest/userguide/cli-multiple-profiles.html)
that is used to authenticate AWS API calls.

### Execution targets

`ssm` needs to be told which instances should execute the provided
command(s). You can do this by specifying instance IDs, or by
specifying App, Stack, and Stage tags.

    # by ID
	... --instances i-0123456,i-9876543
    ... -i i-0123456,i-9876543
	
	# by tag
	... --asset-tags app,stack,stage
	... -t app,stack,stage

If you provide tags, `ssm` will search for running instances that are
have those tags.

## Actions

### One-Off Commands

You can tell `ssm` the commands to execute with an argument that
specifies the command or by providing a file that contains the
commands to be run.

    # specify command
	--cmd ls
	-c ls
	
	# provide script file
	--src-file script
	-f script

### Command Loop (REPL)

With the `-I` interactive flag, `ssm` will initialise a list of
instances and then wait for commands to be specified.  Each command
will be executed on all instances and the user can select the instance
to display.

### SSH

Specifying the `-s` flag will cause `ssm` to generate a temporary ssh
key, and install the public key on a specific instance.  It will then
output the command to `ssh` directly to that instance.

The instance must already have both a public IP address _and_
appropriate security groups.

## Development

During development, the program can be run using sbt, either from an
sbt shell or from the CLI in that project.

    $ sbt "run --instances i-0123456 --profile xxx --region xxx --cmd pwd"

    sbt:ssm-scala> run --instances i-0123456 --profile xxx --region xxx --cmd pwd

However, `sbt` traps the program exit so in REPL mode you may find it
easier to create and run a jar instead.

You can produce a `jar` from your current source code using `assembly`
from the sbt shell, or by executing the following from within the
project. The command's output will show the location of the newly
created jar file, but it's likely to be in `target/scala-2.12/ssm.jar`

    sbt assembly

The jar can then be invoked as follows (it may be useful to create a
wrapper script, as described above in 'Installation').

    java -jar <path-to-jar>/ssm.jar "@$"

