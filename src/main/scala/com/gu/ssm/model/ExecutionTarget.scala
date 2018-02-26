package com.gu.ssm.model

import java.time.Instant


sealed trait ExecutionTarget

case class InstanceId(id: String) extends AnyVal

case class InstanceIds(ids: List[InstanceId])
  extends ExecutionTarget

case class Instance(id: InstanceId, publicIpAddressOpt: Option[String], launchInstant: Instant)
  extends ExecutionTarget

case class EMRClusterId(id: String)
  extends ExecutionTarget

case class AppStackStage(app: String, stack: String, stage: String)
  extends ExecutionTarget