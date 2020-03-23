# Enabling SSM Tunnel

It is possible to tunnel SSH traffic through AWS systems manager and avoid needing to open up port 22 at all on your instances. This is strongly encouraged! To get it working you'll need to do the following stuff:

## In AWS

Update the permissions of your instances so that they are allowed to do these things:

```
- ssm:UpdateInstanceInformation
- ssmmessages:CreateControlChannel
- ssmmessages:CreateDataChannel
- ssmmessages:OpenControlChannel
- ssmmessages:OpenDataChannel
```

See [here](https://github.com/guardian/deploy-tools-platform/blob/master/cloudformation/nexus.template.yaml#L118) for an example complete policy.

You'll also need to ensure you're using a recent AMI that has at least version 2.3.672.0 of systems manager - this is now in our base images so using a recent amigo AMI should do the job. 

## On your machine

Upgrade your local version of ssm and awscli:

	brew upgrade ssm
	brew upgrade awscli

You'll also need to install the systems manager plugin on your machine:

	brew cask install session-manager-plugin

You can then SSH using SSM and the --ssm-tunnel command:

	ssm ssh -x -i i-0937fe9baa578095b -p deployTools --ssm-tunnel

(Useful tip - you can find the instance id using prism, e.g. `prism -f instanceName amigo`)

## Post setup

Once you've confirmed this is working you can remove any security group rules allowing access on port 22.

## More info
Check out the original PR: https://github.com/guardian/ssm-scala/pull/111 for further details on how this works.
