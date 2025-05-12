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


## First time here, just show me the SSH thing real quick

The readme is quite detailed (and shows how to do many more things than what will be shown in this section) but you are probably reading it because you just want to ssh to a box. Here is what you need to do:

1. Install ssm. How to do so was explained in the previous section.
2. Ensure that you have the Janus credentials of the account you want to work with. We are going to assume `frontend` in this section for the examples.
2. Identify the instance number of the box you want to reach. It can be found in the AWS developer console. Instance numbers look like this `i-00032c76140bc9140`.
3. At your console type

	```
	ssm ssh -i i-00032c76140bc9140 -p frontend
	```

	and more generally

	```
	ssm ssh -i <instance-id> -p <accountName>
	ssm ssh -i <instance-id> -p <accountName>
	```

5. And that's it! If all went well you have been ssh'ed to the box.


## Known issues

If you get an error about Futures timed out after 25 seconds, then the SSM permissions may not be right, or you might need to recycle the instance since adding the permissions.

If the disk on which the keyfile is stored is full, then ssm-scala cannot add the public key identity prior to logging in to the box.  This is often found to be the case, and also can apparently cause the AWS SSM agent to stop.

One potential workaround for this is rebooting the box using the EC2 console (may clear down logs, for example).

## Usage

The automatically generated help section for `ssm` is

```
Usage: ssm [cmd|repl|ssh|scp] [options] <args>...

  -p, --profile <value>    The AWS profile name to use for authenticating this execution
  -i, --instances <value>  Specify the instance ID(s) on which the specified command(s) should execute
  -t, --tags <value>       Search for instances by tag. If you provide less than 3 tags assumed order is app,stage,stack. e.g. '--tags riff-raff,prod' or '--tags grafana' Upper/lowercase variations will be tried.
  -r, --region <value>     AWS region name (defaults to eu-west-1)
  --verbose                enable more verbose logging
  --use-default-credentials-provider
                           Use the default AWS credentials provider chain rather than profile credentials. This option is required when running within AWS itself.
Command: cmd [options]
Execute a single (bash) command, or a file containing bash commands
  -u, --user <value>       Execute command on remote host as this user (default: ubuntu)
  -c, --cmd <value>        A bash command to execute
  -f, --file <value>       A file containing bash commands to execute
Command: repl
Run SSM in interactive/repl mode
Command: ssh [options]
Create and upload a temporary ssh key
  -u, --user <value>       Connect to remote host as this user (default: ubuntu)
  --port <value>           Connect to remote host on this port
  --newest                 Selects the newest instance if more than one instance was specified
  --oldest                 Selects the oldest instance if more than one instance was specified
  --private                Use private IP address (must be routable via VPN Gateway)
  --raw                    Unix pipe-able ssh connection string. Note: disables automatic execution. You must use 'eval' to execute this due to nested quoting
  -x, --execute            [Deprecated - new default behaviour] Makes ssm behave like a single command (eg: `--raw` with automatic piping to the shell)
  -d, --dryrun             Generate SSH command but do not execute (previous default behaviour)
  -A, --agent              Use the local ssh agent to register the private key (and do not use -i); only bastion connections
  -a, --no-agent           Do not use the local ssh agent
  -b, --bastion <value>    Connect through the given bastion specified by its instance id; implies -A (use agent) unless followed by -a.
  -B, --bastion-tags <value>
                           Connect through the given bastion identified by its tags; implies -a (use agent) unless followed by -A.
  --bastion-port <value>   Connect through the given bastion at a given port.
  --bastion-user <value>   Connect to bastion as this user (default: ubuntu).
  --host-key-alg-preference <value>
                           The preferred host key algorithms, can be specified multiple times - last is preferred (default: ecdsa-sha2-nistp256, ssh-rsa)
  --ssm-tunnel             [deprecated]
  --no-ssm-proxy           Do not connect to the host proxying via AWS Systems Manager - go direct to port 22. Useful for  instances running old versions of systems manager (< 2.3.672.0)
  --tunnel <value>         Forward traffic from the given local port to the given host and port on the remote side. Accepts the format `localPort:host:remotePort`, e.g. --tunnel 5000:a.remote.host.com:5000
  --rds-tunnel <value>     Forward traffic from a given local port to a RDS database specified by tags. Accepts the format `localPort:tags`, where `tags` is a comma-separated list of tag values, e.g. --rds-tunnel 5000:app,stack,stage
Command: scp [options] [:]<sourceFile>... [:]<targetFile>...
Secure Copy
  -u, --user <value>       Connect to remote host as this user (default: ubuntu)
  --port <value>           Connect to remote host on this port
  --newest                 Selects the newest instance if more than one instance was specified
  --oldest                 Selects the oldest instance if more than one instance was specified
  --private                Use private IP address (must be routable via VPN Gateway)
  --raw                    Unix pipe-able scp connection string
  -x, --execute            [Deprecated - new default behaviour] Makes ssm behave like a single command (eg: `--raw` with automatic piping to the shell)
  -d, --dryrun             Generate SCP command but do not execute (previous default behaviour)
  --ssm-tunnel             [deprecated]
  --no-ssm-proxy           Do not connect to the host proxying via AWS Systems Manager - go direct to port 22. Useful for instances running old versions of systems manager (< 2.3.672.0)
  [:]<sourceFile>...       Source file for the scp sub command. See README for details
  [:]<targetFile>...       Target file for the scp sub command. See README for details
```

There are two mandatory configuration items.

To specify your AWS profile (for more information see [AWS profiles](https://docs.aws.amazon.com/cli/latest/userguide/cli-multiple-profiles.html)), either of:

- `--profile`
- AWS_PROFILE environment variable

To target the command, either of:

- `-i`, where you specify one or more instance ids, or
- `-t`, where you specify the app name, the stack and the stage.

### "Tainted" Instances

When accessing to an instance the user is greeted with a message of the form

```
This instance should be considered tainted.
It was accessed by 1234567890:alice.smith at Fri Apr 27 08:36:58 BST 2018
```

This message highlights the fact that access is being logged and that the next person will see that the current user has been there. The current wording of **considered tainted** highlights the fact that the user has no idea what has happened during previous ssh sessions and raises awareness of the implications of accessing a box.

### "Too many authentication failures"

This is the result of having too many keys in your agent and exceeding the servers configured authentication attempts. This is fixed as of 0.9.7 as we disable the use of the agent using `IdentitiesOnly`.

If you still see "Too many authentication failures" then please raise an issue. You can work around it by running `ssh-add -D` to remove all keys from your agent.

### --raw usage

If you need to add extra parameters to the SSH command then you can use `--raw`. In it's simplest form the following are equivalent:
```bash
ssm ssh -i i-0123456789abcdef0 -p composer
```

and

```bash
eval $(ssm ssh -i i-0123456789abcdef0 -p composer --raw)
```

This helps to undertake actions such as construct tunnels. For example to access a remote postgres server:

```bash
eval $(ssm ssh -i i-0123456789abcdef0 -p composer --raw) -L 5432:my-postgres-server-hostname:5432
```

Note the use of `eval` in these examples - this is required in order to correctly parse the nested quotes that are output as part of the raw command. If you don't use `eval` then you are likely to see an error message such as `ssh: Could not resolve hostname yes": nodename nor servname provided, or not known`.

### Execution targets

`ssm` needs to be told which instances should execute the provided command(s). You can do this by specifying instance IDs, or by specifying App, Stack, and Stage tags.

```
# by instance ids
	--instances i-0123456,i-9876543
	-i i-0123456,i-9876543

# by tag
	--tags <app>,<stage>,<stack>
	-t <app>,<stage>,<stack>
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

The REPL mode causes `ssm` to generate a list of instances and then wait for commands to be specified. Each command will be executed on all instances and the user can select the instance to display.

An example of using `ssh` command is:

```
./ssm ssh --profile <aws-profile> -t security-hq,security,PROD
```

This causes `ssm` to generate a temporary ssh key, and install the public key on a specific instance. It will then output the command to `ssh` directly to that instance. The instance must already have appropriate security groups.

The target for the ssh command will be the public IP address if there is one, otherwise the private IP address. The `--private` flag overrides this behavior and defaults to the private IP address.

Note that if the argument `-t <app>,<stack>,<stage>` resolves to more than one instance, the command will stop with an error message. You can circumvent this behaviour and instruct `ssm` to proceed with one single instance using the command line flags `--oldest` and `--newest`, which select either the oldest or newest instances.

### --raw

This flag allows for a pipe-able ssh connection string. For instance

```
ssm ssh --profile security -t security-hq,security,PROD --newest --raw | xargs -0 -o bash -c
```

Will automatically ssh you to the newest instance running security-hq. Note that you still have to manually accept the new ECDSA key fingerprint.

### -d, --dryrun

Generate SSH command but do not execute (previous default behaviour)

```
ssm ssh --profile security -t security-hq,security,PROD --newest --dryrun
```

Example output:

```
========= i-0566a4df63c0c35bb =========
# Dryrun mode. The command below will remain valid for 30 seconds:

ssh -o "IdentitiesOnly yes" -o "UserKnownHostsFile ...
```

### -x, --execute

DEPRECATED - flag is now the default behaviour. This flag makes ssm behave like ssh. The raw output is automatically piped to `xargs -0 -o bash -c`. You would then do

```
ssm ssh --profile security -t security-hq,security,PROD --newest --execute
```

instead of the example given in the previous `--raw` section.

### --tunnel

This flag forwards traffic from a local port through the instance to the specified hostname and port. For example,

```
ssm ssh --profile security -t security-hq,security,PROD --newest --tunnel 5000:example.com:6000
```

would forward all traffic on your machine through the remote instance to example.com:6000.

### ---rds-tunnel

Similar to `tunnel`, this flag forwards traffic from a local port to an AWS RDS database specified by the given tags. For example,

```
ssm ssh --profile security -t security-hq,security,PROD --newest --rds-tunnel 5000:example-db,security,CODE
```

would try to find a single RDS instance with the tags `example-db,security,CODE`, and forward traffic from port 5000 to that RDS instance via the remote instance.

## Disabling SSM Tunnel
**By default, SSM proxies your connection via AWS systems manager**, which saves you from opening up port 22, connecting to
the VPN, or using bastion hosts. This requires a recent version of systems manager to be runnning on your machine and
the target machine. You can still connect the old way via port 22 using the flag `--no-ssm-proxy`

## Enabling SSM Tunnel

It is strongly encouraged to connect using the default SSM tunnel behaviour. To get this working you'll need to do the following stuff:

### In AWS

Update the permissions of your instances so that they are allowed to do these things:

```
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
              - ssmmessages:CreateControlChannel
              - ssmmessages:CreateDataChannel
              - ssmmessages:OpenControlChannel
              - ssmmessages:OpenDataChannel
```

See [here](https://github.com/guardian/deploy-tools-platform/blob/master/cloudformation/nexus.template.yaml#L118) for an example complete policy.

You'll also need to ensure you're using a recent AMI that has at least version 2.3.672.0 of systems manager - this is now in our base images so using a recent amigo AMI should do the job.

Once these permissions are added, the `ssm-agent` service running on the boxes will need to be restarted before connecting. This will happen as boxes are cycled – e.g. by redeploying your app – or you can restart an agent manually with `sudo snap restart amazon-ssm-agent.amazon-ssm-agent`.

### On your machine

Upgrade your local version of ssm and awscli:

```
  brew upgrade ssm
  brew upgrade awscli
```

You'll also need to install the systems manager plugin on your machine:

```
  brew cask install session-manager-plugin
```

You can then SSH using SSM with the default arguments:

```
  ssm ssh -i i-0937fe9baa578095b -p deployTools
 ```

(Useful tip - you can find the instance id using prism, e.g. `prism -f instanceName amigo`)

### Post setup

Once you've confirmed this is working you can remove any security group rules allowing access on port 22.

### More info

Check out the original PR: https://github.com/guardian/ssm-scala/pull/111 for further details on how this works.


## Bastions

Bastion are proxy servers used as entry point to private networks and ssm scala supports their use.

**You may not need a bastion server at all! Prefer to use an SSM tunnel (see above) where possible.**

### Introduction

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
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2022 --tags app,stage,stack
```

together, if the tags may resolve to more than one instance, the `--oldest` and `--newest` flags

```
ssm ssh --profile <profile-name> --bastion i-bastion12345 --bastion-port 2022 --tags app,stage,stack --newest
```

### Using tags to specify the bastion instance

If you do not know the id of the current bastion, but it is tagged correctly, it is also possible to use:

```
ssm ssh --profile <profile-name> --bastion-tags <app,stage,stack> --bastion-port 2022 -i i-application-12345
```

This will respect any --newest / --oldest switches, although it is anticipated that there will usually only be one bastion. It will always use the public IP address of the bastion.

### Bastion users

It is possible to specify the user used for connecting to the bastion, this is done with the `--bastion-user <value>` command line argument.

### Bastions with private IP addresses

When using the standard `ssh` command, the `--private` flag can be used to indicate that the private IP of the target instance should be used for the connection. In the case of bastion connection the target instance is assumed to always be reacheable through a private IP and this flag indicates whether the private IP of the bastion should be used.

### Bastions with private keys problems

There's been occurences of bastions connections strings of the form

```
ssh -A -i /path/to/temp/private/key -t -t ubuntu@bastion-hostname \
    -t -t ssh -t -t ubuntu@target-ip-address;
```
not working, because the private file was not found for the second ssh connection, leading to a "Permission denied (publickey)" error message.

When this happens the user can use the `-a`, `--agent` flag that performs a registration of the private key at the local ssh agent. With this flag, ssm command

```
ssm ssh --profile <account-name> --bastion <instanceId1> \
    -i <instanceId2> --agent
```

returns

```
ssh-add /path/to/temp/private/key && \
    ssh -A ubuntu@bastion-hostname \
    -t -t ssh ubuntu@target-ip-address;
```


## Secure Copy

**ssm** support the **scp** sub command for the secure transfer of files and directories.

### Introduction

An example of usage is

```
./ssm scp -p account -t app,stage,stack /path/to/file1 :/path/to/file1
```

Which outputs

```
# simplified version
scp -i /path/to/identity/file.tmp /path/to/file1 ubuntu@34.242.32.40:/path/to/file2;
```

Otherwise

```
./ssm scp -p account -t app,stage,stack :/path/to/file1 /path/to/file2
```

outputs

```
# simplified version
scp -i /path/to/identity/file.tmp ubuntu@34.242.32.40:/path/to/file1 /path/to/file2 ;
```

The convention is: the first (left hand side) file is always the source and the second (right hand side) is always the target and the colon, indicates which one is on the remote server.

## Development

During development, the program can be run using sbt, either from an sbt shell or from the CLI in that project.

    $ sbt "run cmd -c pwd --instances i-0123456 --profile xxx --region xxx"

    sbt:ssm-scala> run cmd -c pwd --instances i-0123456 --profile xxx --region xxx

However, `sbt` traps the program exit so in REPL mode you may find it easier to create and run an executable instead, for this just run

```bash
./generate-executable.sh
```

The result of this script is an executable called `ssm` in the target folder. If you are using a non unix operating system, run `sbt assembly` as you would normally do and then run the ssm.jar file using

```
java -jar <path-to-jar>/ssm.jar [arguments]
```

## Release a new version

To release a new version of `ssm` perform the two following tasks:

1. Update the version number in `build.sbt`

2. Generate a new executable. Run the following at the top of the repository
   ```bash
   ./generate-executable.sh
   ```
	Note that this script generates the **tar.gz** file needed for the github release as well as outputting the sha256 hash of that file needed for the homebrew-devtools' update.

3. Create and merge a PR with the new version number (Eg. #459).

4. Create a new tag locally and push it:
   ```
   git tag v[version-number]
   git push origin v[version-number]
   ```

5. Go to the GitHub repository at https://github.com/guardian/ssm-scala/releases

6. Draft a new release

7. Upload the binary assets:
   * The raw executable file (target/scala-X.Y.Z/ssm)
   * The tarball (ssm.tar.gz)

8. Publish the release

9. Make a PR to [https://github.com/guardian/homebrew-devtools/blob/master/Formula/ssm.rb](https://github.com/guardian/homebrew-devtools/blob/master/Formula/ssm.rb) to update the new version's details.


## How to use SSM Scala with your own project

To use ssm-scala against the instances of your project, the following needs to happen:

1. Add permissions with a policy like:

    ```yaml
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
            - ssmmessages:CreateControlChannel
            - ssmmessages:CreateDataChannel
            - ssmmessages:OpenControlChannel
            - ssmmessages:OpenDataChannel
        Roles:
        - !Ref ExampleAppInstanceRole
    ```

	Example stolen from the [Security-HQ cloudformation](https://github.com/guardian/security-hq/blob/master/cloudformation/security-hq.template.yaml) file.

2. Download the executable from the [project release page](https://github.com/guardian/ssm-scala/releases). Instructions on usage can be found in the above sections.

Note: SSM needs the target server to have outbound port 443 (ssm-agent's communication with AWS's SSM and EC2 Messages endpoints).


##License

Copyright (c) 2018 Guardian News & Media. Available under the Apache License.
