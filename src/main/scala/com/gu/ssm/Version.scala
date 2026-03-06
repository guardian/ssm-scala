package com.gu.ssm

/** Version information for the ssm CLI tool.
  *
  * These settings are baked into the native binary at build time via environment variables.
  */
object Version {

  /** The release version of this ssm binary.
    *
    * This is set at build time via the SSM_RELEASE environment variable and gets baked into the
    * native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Defaults to "dev" for local development builds (running via sbt or JVM packaging).
    */
  val release: String = sys.env.getOrElse("SSM_RELEASE", "dev")

  /** The target architecture for which the ssm binary was built.
    *
    * This is set at build time via the SSM_ARCHITECTURE environment variable and gets baked into
    * the native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Returns None for local development builds (running via sbt or JVM packaging).
    */
  val architecture: Option[String] = sys.env.get("SSM_ARCHITECTURE")

  /** The branch from which this ssm binary was built.
    *
    * This is set at build time via the SSM_BRANCH environment variable and gets baked into the
    * native binary during GraalVM compilation.
    *
    * This environment variable is allowlisted in the build.sbt settings for the cli project.
    *
    * Returns None for local development builds (running via sbt or JVM packaging).
    */
  val branch: Option[String] = sys.env.get("SSM_BRANCH")
}
