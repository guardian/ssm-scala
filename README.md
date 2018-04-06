SSM-Scala
=========

SSM-Scala is a command-line tool, written in Scala, for executing commands on EC2 servers using AWS's EC2 Run command. It provides the user with: 

1. standard `ssh` access using short lived RSA keys
2. an _alternative_ to `ssh` for running commands on the target

Both modes apply to servers in AWS accounts to which you have [IAM](https://aws.amazon.com/iam/) access.

Instructions for using SSM Scala in your own project can be found [below](#How-to-use-SSM-Scala-with-your-own-project).

## Installation

If you have Homebrew installed and want to install ssm, do

```
brew install guardian/devtools/ssm
```

and for an upgrade do

```
brew upgrade ssm
```

Otherwise, fetch the most recently released version of the program from the [Github releases page](https://github.com/guardian/ssm-scala/releases/latest) and make sure it is executable (`chmod +x ssm`). You may then want to put it somewhere in your PATH.

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
  -u, --user <value>       Connect to remote host as user (default: ubuntu)
  --newest                 Selects the newest instance if more than one instance was specified
  --oldest                 Selects the oldest instance if more than one instance was specified
  --private                Use private IP address (must be routable via VPN Gateway)
  --raw                    Unix pipe-able ssh connection string
  --bastion <value>        Connect through the given bastion specified by its instance id
  --bastion-port <value>   Connect through the given bastion at a given port
  --bastion-user <value>   Connect to bastion as this user (default: ubuntu)
```

There are two mandatory configuration items.

To specify your AWS profile (for more information see [AWS profiles](https://docs.aws.amazon.com/cli/latest/userguide/cli-multiple-profiles.html)), either of:

- `--profile`
- AWS_PROFILE environment variable

To target the command, either of: 
	
- `-i`, where you specify one or more instance ids, or 
- `-t`, where you specify the app name, the stack and the stage. 

### Execution targets

`ssm` needs to be told which instances should execute the provided command(s). You can do this by specifying instance IDs, or by specifying App, Stack, and Stage tags.

```
# by instance ids
	--instances i-0123456,i-9876543
	-i i-0123456,i-9876543

# by tag
	--tags <app>,<stack>,<stage>
	-t <app>,<stack>,<stage>
```

If you provide tags, `ssm` will search for running instances that are have those tags.

### Examples

Examples of using `cmd` are

```
./ssm cmd -c date --profile security -t security-hq,security,PROD
```
or
```
export AWS_PROFILE=security
./ssm cmd -c date -t security-hq,security,PROD
```

where the `date` command will be ran on all matching instances.

An example of using `repl` is:

```
./ssm repl --profile <aws-profile> -t security-hq,security,PROD
```

The REPL mode causes `ssm` to generate a list of
instances and then wait for commands to be specified.  Each command will be executed on all instances and the user can select the instance to display.

An example of using `ssh` command is:

```
./ssm ssh --profile <aws-profile> -t security-hq,security,PROD
```

This causes `ssm` to generate a temporary ssh
key, and install the public key on a specific instance.  It will then output the command to `ssh` directly to that instance. 
The instance must already have appropriate security groups.

The target for the ssh command will be the first available of: public domain name, then public ip address or private ip address if the --private options was specified.

Note that if the argument `-t <app>,<stack>,<stage>` resolves to more than one instance, the command will stop with an error message. You can circumvent this behaviour and instruct `ssm` to proceed with one single instance using the command line flags `--oldest` and `--newest`, which select either the oldest or newest instances.

### --raw

This flag allows for a pipe-able ssh connection string. For instance

```
ssm ssh --profile security -t security-hq,security,PROD --newest --raw | xargs -0 -o bash -c
```

Will automatically ssh you to the newest instance running security-hq. Note that you still have to manually accept the new ECDSA key fingerprint.

## Bastions

### Introduction

Bastion are proxy servers used as entry point to private networks and ssm scala supports their use. 

In this example we assume that you have a bastion with a public IP address (even though the bastion Ingress rules may restrict it to some IP ranges), identified by aws instance id `i-bastion12345`, and an application server, on a private network with private IP address, and with instance id `i-application-12345`, you would then use ssm to connect to it using 

```
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2022 -i i-application-12345
```

The outcome of this command is a one-liner of the form

```
ssh -A -i /path/to/private/key-file ubuntu@someting.example.com -t -t ssh ubuntu@10.123.123.123;
```

### Handling Ports

You can specify a port that the bastion runs ssh on, with the option `--bastion-port <port number>`, example

```
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2345 -i i-application-12345
``` 


### Using tags to specify the target instance

In the current version of bastion support you will need to specify the bastion using its aws instance id, but you can refer to the application instance using the tag system as in

```
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2022 --tags app,stack,stage
```

together, if the tags may resolve to more than one instance, the `--oldest` and `--newest` flags

```
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2022 --tags app,stack,stage --newest
```

### Bastion users

It is possible to specify the user used for connecting to the bastion, this is done with the `--bastion-user <value>` command line argument.

### Bastions with private IP addresses

When using the standard `ssh` command, the `--private` flag can be used to indicate that the private IP of the target instance should be used for the connection. In the case of bastion connection the target instance is assumed to always be reacheable through a private IP and this flag indicates whether the private IP of the bastion should be used.

## Development

During development, the program can be run using sbt, either from an sbt shell or from the CLI in that project.

    $ sbt "run cmd -c pwd --instances i-0123456 --profile xxx --region xxx"

    sbt:ssm-scala> run cmd -c pwd --instances i-0123456 --profile xxx --region xxx 

However, `sbt` traps the program exit so in REPL mode you may find it easier to create and run an executable instead, for this just run 

```
./generate-executable.sh
```

The result of this script is an executable called `ssm` in the target folder. If you are using a non unix operating system, run `sbt assembly` as you would normally do and then run the ssm.jar file using 

```
java -jar <path-to-jar>/ssm.jar [arguments]
```

## Release a new version

To release a new version of `ssm` perform the two following tasks:

1. Generate a new executable. Run the following at the top of the repository
 
	```
	./generate-executable.sh
	```
	
	Note that this script generates the **tar.gz** file needed for the github release as well as outputting the sha256 hash of that file needed for the homebrew-devtools' update.

2. Increase the version number accordingly and release a new tag at [ssm-scala releases](https://github.com/guardian/ssm-scala/releases). Upload the raw executable (**file**: `ssm`) as well as a **tar.gz** version (**file**: `ssm.tar.gz`).

3. Make a PR to [https://github.com/guardian/homebrew-devtools/blob/master/Formula/ssm.rb](https://github.com/guardian/homebrew-devtools/blob/master/Formula/ssm.rb) to update the new version's details.

## How to use SSM Scala with your own project

To use ssm-scala against the instances of your project, three things need to happen:

1. Update your base image in AMIgo with the **ssm-agent** role.

2. Add the following declaration 

	```
	ManagedPolicyArns: [ "arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM" ]
	```

	under the `AWS::IAM::Role`'s `Properties` to your cloudformation. 
	
	Alternatively, considering that this policy might not work for instances requiring limited set of permissions, you can add (an adaptation of) the following `AWS::IAM::Policy` to your cloudformation file
	
	```
	  ExampleAppSSMRunCommandPolicy:
	    Type: AWS::IAM::Policy
	    Properties:
	      PolicyName: example-app-ssm-run-command-policy
	      PolicyDocument:
	        Statement:
	        # minimal policy to allow to (only) run commands via ssm
	        - Effect: Allow
	          Resource: "*"
	          Action:
	          - ec2messages:AcknowledgeMessage
	          - ec2messages:DeleteMessage
	          - ec2messages:FailMessage
	          - ec2messages:GetEndpoint
	          - ec2messages:GetMessages
	          - ec2messages:SendReply
	          - ssm:UpdateInstanceInformation
	          - ssm:ListInstanceAssociations
	          - ssm:DescribeInstanceProperties
	          - ssm:DescribeDocumentParameters
	      Roles: 
	      - !Ref ExampleAppInstanceRole
	```
	
	Example stolen from the [Security-HQ cloudformation](https://github.com/guardian/security-hq/blob/master/cloudformation/security-hq.template.yaml) file.

3. Download the executable from the [project release page](https://github.com/guardian/ssm-scala/releases). Instructions on usage can be found in the above sections.

Note: SSM needs the target server to have outbound port 443 (ssm-agent's communication with AWS's SSM and EC2 Messages endpoints). 


##License

Copyright (c) 2018 Guardian News & Media. Available under the Apache License.
