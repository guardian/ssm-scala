package com.gu.ssm.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}

import scala.util.Try

/** Setting the file permissions
  */
object FilePermissions {

  /** Using java 7 nio API to set the permissions.
    *
    * @param file
    *   to act on
    * @param perms
    *   in octal format
    */
  def apply(file: File, perms: String): Unit = {
    val posix = PosixFilePermissions fromString convert(perms)
    val result = Try {
      Files.setPosixFilePermissions(file.toPath, posix)
    } recoverWith {
      // in case of windows
      case _: UnsupportedOperationException =>
        Try {
          file.setExecutable(perms contains PosixFilePermission.OWNER_EXECUTE)
          file.setWritable(perms contains PosixFilePermission.OWNER_WRITE)
        }
    }

    // propagate error
    if (result.isFailure) {
      val e = result.failed.get
      sys.error(
        "Error setting permissions " + perms + " on " + file.getAbsolutePath + ": " + e.getMessage
      )
    }
  }

  /** Converts a octal unix permission representation into a java
    * `PosixFilePermissions` compatible string.
    */
  def convert(perms: String): String = {
    require(
      perms.length == 4 || perms.length == 3,
      s"Permissions must have 3 or 4 digits, got [$perms]"
    )
    // ignore setuid/setguid/sticky bit
    val i = if (perms.length == 3) 0 else 1
    val user = Character getNumericValue (perms charAt i)
    val group = Character getNumericValue (perms charAt i + 1)
    val other = Character getNumericValue (perms charAt i + 2)

    permissionAsString(user) + permissionAsString(group) + permissionAsString(
      other
    )
  }

  def permissionAsString(perm: Int): String = perm match {
    case 0 => "---"
    case 1 => "--x"
    case 2 => "-w-"
    case 3 => "-wx"
    case 4 => "r--"
    case 5 => "r-x"
    case 6 => "rw-"
    case 7 => "rwx"
  }

  /** Enriches string with `oct` interpolator, parsing string as base 8 integer.
    */
  implicit class OctalString(val sc: StringContext) extends AnyVal {
    def oct(args: Any*): Int = Integer.parseInt(sc.s(args: _*), 8)
  }

}
