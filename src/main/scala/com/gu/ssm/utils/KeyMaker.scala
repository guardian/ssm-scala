package com.gu.ssm.utils
import java.io._

import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.security.Key

import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey

object KeyMaker {

  import org.bouncycastle.jce.provider.BouncyCastleProvider
  import java.security.KeyPairGenerator
  import java.security.Security

  def makeKey (file: File): String = {
    Security.addProvider(new BouncyCastleProvider)
    val keyPair = generateRSAKeyPair
    val priv = keyPair.getPrivate
    val pub = keyPair.getPublic
    writePemFile(priv, "RSA PRIVATE KEY", file)
    new AuthorizedKey(pub, "security-magic").toString
  }

  private def generateRSAKeyPair = {
    val generator = KeyPairGenerator.getInstance("RSA", "BC")
    generator.initialize(2048)
    val keyPair = generator.generateKeyPair
    keyPair
  }

  private def writePemFile(key: Key, description: String, file: File): Unit = {
    new PemFile(key, description, file).write
  }
}

class PemFile(val key: Key, val description: String, val file: File) {
  private val pemObject = new PemObject(description, key.getEncoded)

  def write(): Unit = {
    val pemWriter = new PemWriter(new OutputStreamWriter(new FileOutputStream(file)))
    try
      pemWriter.writeObject(this.pemObject)
    finally pemWriter.close()
    println("Wrote key to file (" + file.getCanonicalFile.toString) + ")"
  }
}

import org.apache.commons.codec.binary.Base64


class AuthorizedKey(val key: Key, val description: String) {
  override def toString: String = {
    val rsaPublicKey = key.asInstanceOf[BCRSAPublicKey]
      val byteOs: ByteArrayOutputStream = new ByteArrayOutputStream
      val dos = new DataOutputStream(byteOs)
      dos.writeInt ("ssh-rsa".getBytes.length)
      dos.write ("ssh-rsa".getBytes)
      dos.writeInt (rsaPublicKey.getPublicExponent.toByteArray.length)
      dos.write (rsaPublicKey.getPublicExponent.toByteArray)
      dos.writeInt (rsaPublicKey.getModulus.toByteArray.length)
      dos.write (rsaPublicKey.getModulus.toByteArray)
      val publicKeyEncoded = new String (Base64.encodeBase64 (byteOs.toByteArray) )
      "ssh-rsa " + publicKeyEncoded + " " + description
  }
}

  import org.apache.commons.codec.binary.Base64
  import java.io.ByteArrayOutputStream

//  val rsaPublicKey = key.asInstanceOf[RSAPublicKey]
//  val byteOs: ByteArrayOutputStream = new ByteArrayOutputStream
//  val dos: Nothing = new Nothing (byteOs)
//  dos.writeInt ("ssh-rsa".getBytes.length)
//  dos.write ("ssh-rsa".getBytes)
//  dos.writeInt (rsaPublicKey.getPublicExponent.toByteArray.length)
//  dos.write (rsaPublicKey.getPublicExponent.toByteArray)
//  dos.writeInt (rsaPublicKey.getModulus.toByteArray.length)
//  dos.write (rsaPublicKey.getModulus.toByteArray)
//  publicKeyEncoded = new String (Base64.encodeBase64 (byteOs.toByteArray) )
//  "ssh-rsa " + publicKeyEncoded + " " + user