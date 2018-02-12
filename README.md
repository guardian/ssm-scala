SSM-Scala
=========

SSM-Scala is a command-line tool for executing commands on EC2 servers
using EC2 Run Command, written in Scala. This can be used as an
alternative to `ssh` for servers in AWS accounts to which you have
[IAM](https://aws.amazon.com/iam/) access.

Instructions for using SSM Scala in your own project can be found [below](#How-to-use-SSM-Scala-with-your-own-project).

## Installation

Fetch the most recently released version of the program from the [Github releases page](https://github.com/guardian/ssm-scala/releases/latest) and make sure it is executable (`chmod +x ssm`). You may then want to put it somewhere in your PATH.

## Usage

The automatically generated help section for `ssm` is

```
Usage: ssm [cmd|repl|ssh] [options]

  -p, --profile <value>    The AWS profile name to use for authenticating this execution
  -i, --instances <value>  Specify the instance ID(s) on which the specified command(s) should execute
  -t, --tags <value>       Search for instances by tag e.g. '--tags app,stack,stage'
  -r, --region <value>     AWS region name (defaults to eu-west-1)

Command: cmd [options]
Execute a single (bash) command, or a file containing bash commands
  -c, --cmd <value>        A bash command to execute
  -f, --file <value>       A file containing bash commands to execute

Command: repl
Run SSM in interactive/repl mode

Command: ssh [options]
Create and upload a temporary ssh key
  -a, --any <value>        Indicates whether the command should run on any single instance
```

The general syntax is 

```
./ssm [cmd|repl|ssh] [options]
```

An example of `cmd` is 

```
./ssm cmd -c ls --profile security -t security-hq,security,PROD
```

For more information about `--profile` see [AWS profiles](https://docs.aws.amazon.com/cli/latest/userguide/cli-multiple-profiles.html).

The general syntax for the `-t` switch is `-t <app>,<stack>,<stage>`. 

For convenience, all subsequent examples will use the wrapper syntax.

The syntax for using the `repl` command is:

```
./ssm repl --profile <aws-profile> -t <app>,<stack>,<stage>
```

This causes `ssm` to generate a list of
instances and then wait for commands to be specified.  Each command
will be executed on all instances and the user can select the instance
to display.

The syntax for using the `ssh` command is:

```
./ssm ssh --profile <aws-profile> -t <app>,<stack>,<stage> 
```

This causes `ssm` to generate a temporary ssh
key, and install the public key on a specific instance.  It will then
output the command to `ssh` directly to that instance. 
The instance must already have both a public IP address _and_
appropriate security groups.

Notet that if the argument `-t <app>,<stack>,<stage>` resolves to more than one instance, the command will stop with an error message. You can circumvent this behaviour and instruct `ssm` to process with one single instance with the argument `-a true`.

### More usage examples

Execute a command on all matching instances:

```
./ssm cmd -c <command> --profile <aws-profile> --ass-tags <app>,<stack>,<stage>
```

Execute the contents of a script file on matching instances:

```
./ssm --file <path-to-script> --profile <aws-profile> --ass-tags <app>,<stack>,<stage>
```

Execute `ls` on the specified instance:

```
./ssm cmd -c ls --profile <aws-profile> --instances i-01234567
```

Execute `ls` on multiple specified instances (using the short form of
the arguments):

```
./ssm cmd -f <path-to-script> --profile <aws-profile> -i i-01234567,i-98765432
```

### Execution targets

As seen in the previous section, `ssm` needs to be told which 
instances should execute the provided
command(s). You can do this by specifying instance IDs, or by
specifying App, Stack, and Stage tags.

```
# by ID
... --instances i-0123456,i-9876543
... -i i-0123456,i-9876543

# by tag
... --asset-tags <app>,<stack>,<stage>
... -t <app>,<stack>,<stage>
```

If you provide tags, `ssm` will search for running instances that are
have those tags.


## Development

During development, the program can be run using sbt, either from an
sbt shell or from the CLI in that project.

    $ sbt "run --instances i-0123456 --profile xxx --region xxx --cmd pwd"

    sbt:ssm-scala> run --instances i-0123456 --profile xxx --region xxx --cmd pwd

However, `sbt` traps the program exit so in REPL mode you may find it
easier to create and run an executable instead, for this just run 

```
./generate-executable.sh
```

The result of this script is an executable called `ssm` in the target folder. If you are using a non unix operating system, run `sbt assembly` as you would normally do and then run the ssm.jar file using 

```
java -jar <path-to-jar>/ssm.jar [arguments]
```

## How to use SSM Scala with your own project

To use ssm-scala against the instances of your project, three things need to happen:

1. Update your base image in AMIgo with the **ssm-agent** role.

2. Add the following declaration 
	```
	ManagedPolicyArns: [ "arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM" ]
	```
	to your cloudformation. Example: the [Security-HQ cloudformation](https://github.com/guardian/security-hq/blob/master/cloudformation/security-hq.template.yaml#L86).

3. Download the executable from the [project release page](https://github.com/guardian/ssm-scala/releases). Instructions on usage can be found in the above sections.

