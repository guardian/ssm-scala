package com.gu.ssm.utils
import java.io._

import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.security.Key

import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.apache.commons.codec.binary.Base64

object KeyMaker {

  import org.bouncycastle.jce.provider.BouncyCastleProvider
  import java.security.KeyPairGenerator
  import java.security.Security

  def makeKey (file: File, algorithm: String, provider: String): String = {
    Security.addProvider(new BouncyCastleProvider)
    val keyPair = generateKeyPair(algorithm, provider)
    val priv = keyPair.getPrivate
    val pub = keyPair.getPublic
    writePemFile(priv, "RSA PRIVATE KEY", file)
    new AuthorizedKey(pub, "security_ssm-scala").toString
  }

  private def generateKeyPair(algorithm: String, provider: String) = {
    val generator = KeyPairGenerator.getInstance(algorithm, provider)
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
    pemWriter.writeObject(this.pemObject)
    pemWriter.close()
  }
}




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