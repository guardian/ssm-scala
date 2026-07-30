# Purpose

AWS SSM has been sufficient to achieve virtually all ssh type needs for some time.  However, it has two
issues we should address.

## AWS CLI is more powerful

When it was first written, in 2018, by the then security team, fifty percent of whom are now back on the security team,
SSM Scala closed a gap in the AWS CLI.  It required instances to be permitted access via the ssh port (22) and managed 
discovery, then it created an ssh key, and put it on the instance, allowing the user to ssh in.  It also allowed for
port forwarding, and other useful ssh features.

The AWS CLI has since been updated to include almost the same functionality, but better.  Instances no longer need to
be permitted access via the ssh port, and the AWS CLI can now do port forwarding by host name.

SSM Scala is no longer worth the maintenance cost.  It won't be deleted, but you should consider moving away from it.

![ssm-scala-tombstone.png](ssm-scala-tombstone.png)

## More paranoia around credentials

The AWS credentials used to get a session on a remote instance inherently provide a lot of power.  Once you have a
session on an instance, you can use the instance's profile to access quite a lot of information, and any secrets
the instance can read.

For example: 
```
# Fetch the user data script
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/user-data
```
or even
```
# Fetch the private cert
aws --region eu-west-1 s3 cp s3://my-bucket-of-secrets/my-stack/PROD/my-secure-app/secret-cert.json
```
or EVEN
```
curl -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/iam/security-credentials/[role-name]
```

Incidentally, there is now a dedicated `.ssh` permission in Janus, which gives just enough permissions to discover
and access an instance within the account, without providing the excessive power of `.dev`

Even with those reduced credentials, we should aim use the same approach as devcontainers, and not put "bare" 
credentials on our laptops (where they can potentially be exfiltrated by attackers) at all.

This is a simple setup to deliver containerised ssh to instances, with discover built in.  You can use this, or take
the principles and build your own, but the important thing is to avoid putting credentials on your laptop.

# Setup

Build a small docker image which includes some scripts, the AWS cli tool and, crucially, the SSM session manager plugin for AWS.

```
docker build -t aws-shell .
```

# Run

```
docker run -it aws-shell bash
```

These can be done in one clean step using the `scripts/container` script.

# Configuring

Grab your AWS credentials as `aws configure` commands.  Make sure the profile name is `default` or set `AWS_PROFILE`.
Paste them into the docker shell.  The region is already set to eu-west-1, but can be overridden on the command line.

# Examples

## Starting a remote SSM session

```
docker run -it aws-shell bash
```
...paste in your creds...

```
> session myApp myStack myStage
Connecting to i-0c7f9cb1234567890

Starting session with SessionId: justin.rowles-rycskjdsfjkgflkbgdflksdfakl
```

## Starting a tunnel to a remote host

### Ports

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

A similar approach can be used to get to remote RDS hosts, looking them up with tags, using `rds-tunnel.

At this point, you can run a client to the remote host communicating on localhost:CONTAINER_PORT.  This
client can be anything - psql, curl, ftp...

# Notes

The socat listener is required solely because the AWS cli does not bind to all interfaces.  This may change.
