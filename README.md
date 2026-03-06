SSM
===

`ssm` is a bash script for opening an interactive shell on an EC2 instance using the AWS SSM Session Manager. It wraps `aws ssm start-session` with tag-based instance discovery, using the Guardian convention of `App`, `Stack`, and `Stage` tags.

## Prerequisites

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- AWS credentials configured for the target account

## Usage

```
ssm [ssh] -p <profile> [-r <region>] (-i <instance-id> | -t <app[,stack[,stage]]>) [--newest | --oldest]
```

The `ssh` subcommand is accepted but optional, for compatibility with the original ssm-scala tool.

### Arguments

| Flag | Description |
|------|-------------|
| `-p`, `--profile` | **(Required)** The AWS profile to use for authentication |
| `-r`, `--region` | AWS region (default: `eu-west-1`) |
| `-i`, `--instance` | Connect directly to this EC2 instance ID |
| `-t`, `--tags` | Discover an instance by `App[,Stack[,Stage]]` tags |
| `--newest` | When multiple instances are found, select the most recently launched |
| `--oldest` | When multiple instances are found, select the least recently launched |

Exactly one of `--instance` or `--tags` must be provided.

## Examples

Connect directly to a known instance ID:
```bash
ssm -p myaccount -i i-1234567890abcdef0
```

Connect to an instance by App tag only:
```bash
ssm -p myaccount -t my-app
```

Connect to an instance with App, Stack and Stage tags:
```bash
ssm -p myaccount -t my-app,my-stack,PROD
```

Specify a non-default region:
```bash
ssm -p myaccount -r eu-west-2 -t my-app,my-stack,PROD
```

If multiple matching instances are found, the script will list them and exit with an error. Use `--newest` or `--oldest` to select automatically:
```bash
ssm -p myaccount -t my-app,my-stack,PROD --newest
```

## Tag-based instance discovery

The `-t` / `--tags` argument accepts up to three comma-separated values, corresponding to the following EC2 tags:

1. `App` — the application name
2. `Stack` — the stack name
3. `Stage` — the deployment stage (e.g. `CODE`, `PROD`)

Only **running** instances are considered. If more than one instance matches and neither `--newest` nor `--oldest` is specified, the script will print the matching instances (with their `App`, `Stack`, `Stage` tags and launch time) and exit, asking you to disambiguate.

## Setting up SSM on your instances

For SSM Session Manager to work, the target EC2 instance needs:

1. The `AmazonSSMManagedInstanceCore` IAM policy attached to its instance role (or equivalent permissions — see below)
2. The SSM Agent running (pre-installed on Amazon Linux 2, Amazon Linux 2023, and recent Ubuntu AMIs)
3. Outbound port 443 open in its security group (for the SSM and EC2 Messages endpoints)

Minimal IAM policy for the instance role:

```yaml
InstanceSSMPolicy:
  Type: AWS::IAM::Policy
  Properties:
    PolicyName: example-ssm-session-policy
    PolicyDocument:
      Statement:
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
      - !Ref InstanceRole
```

## License

Copyright (c) 2018 Guardian News & Media. Available under the Apache License.
