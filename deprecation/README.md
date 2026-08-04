# Using AWS SSM commands from a docker container to restrict credentials scope

NB This is a worked example, not a production-ready solution.  It is intended to demonstrate how to use AWS SSM commands

## Setup

Build a small docker image which includes some scripts, the AWS cli tool and, crucially, the SSM session manager plugin for AWS.

```
docker build -t aws-shell .
```

## Run

```
docker run -it aws-shell bash
```

These can be done in one clean step using the `scripts/container` script.

## Configuring

Grab your AWS credentials as `aws configure` commands.  Make sure the profile name is `default` or set `AWS_PROFILE`.
Paste them into the docker shell.  The region is already set to eu-west-1, but can be overridden on the command line.

## Examples

### Starting a remote SSM session

```
docker run -it aws-shell bash
```
...paste in your creds...

```
> session myApp myStack myStage
Connecting to i-0c7f9cb1234567890

Starting session with SessionId: justin.rowles-rycskjdsfjkgflkbgdflksdfakl
```

### Starting a tunnel to a remote host

#### Ports

There will be three ports in play.

 * Container port exposed by docker
 * SSM port inside the container, exposed by AWS SSM
 * Remote port on the remote host, mediated by a tunnel to the remote instance

The following command starts the container, listening on 9000, which is forwarded to 9000 internally.  A `socat`
process is then automatically started which forwards CONTAINER_PORT internally to SSM_PORT.

```
docker run -it -e CONTAINER_PORT=9000 -e SSM_PORT=9001 -e REMOTE_PORT=9000 -p 9000:9000 aws-shell bash
```
...paste in your creds...
```
host-tunnel myApp myStack myStage theirHostName
```
to open a tunnel on the oldest instance with those tags from SSM_PORT to &lt;theirHostName&gt;:REMOTE_PORT

A similar approach can be used to get to remote RDS hosts, looking them up with tags, using `rds-tunnel`.

At this point, you can run a client to the remote host communicating on localhost:CONTAINER_PORT.  This
client can be anything - psql, curl, ftp...

# Notes

The socat listener is required solely because the AWS cli does not bind to all interfaces.  This may change.
