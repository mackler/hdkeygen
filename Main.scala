package org.mackler.hdkeygen

import com.google.bitcoin
import bitcoin.core.Base58
import bitcoin.crypto.{
  HDKeyDerivation, DeterministicHierarchy,
  DeterministicKey, ChildNumber, KeyCrypterScrypt
}

import org.spongycastle.crypto.params.KeyParameter

import com.google.common.collect.ImmutableList

import collection.JavaConversions._

import java.io.{BufferedReader,File,FileInputStream,InputStreamReader}
import java.nio.charset.Charset

object Main {

  final val GPG_EXECUTABLE = "/usr/bin/gpg"

  case class Config(
    firstAccount: Int     = 0,
    count: Int       = 1,
    seedFile: File   = new File("seed.aes"),
    verbose: Boolean = false,
    debug: Boolean   = false,
    seedData: String = "",
    brainWallet: Boolean = false
  )

  lazy val base32 = new CrockfordBase32

  final val SEED_COMMAND =
    "  dd if=/dev/random count=1 bs=32 | gpg --symmetric --cipher-algo AES256 --output seed.aes"

  // for parsing command-line sitches and options:
  val parser = new scopt.OptionParser[Config](BuildInfo.name) {
    head("Hierarchical determinstic wallet key generator", BuildInfo.version)
    opt[Int]('a', "account") action { (x, c) =>
      c.copy(firstAccount = x) } text("Account number of the first key (starts at 0)")
    opt[Int]('c', "count") action { (x, c) =>
      c.copy(count = x) } text("The number of keys to generate (default 1)")
    opt[File]('s', "seedfile") valueName("<file>") action { (x, c) =>
      c.copy(seedFile = x) } text("name of the file containing the encrypted seed")
    opt[Unit]('b', "brain") action { (_, c) =>
      c.copy(brainWallet = true) } text("brain wallet mode")
    opt[Unit]('v',"verbose") action { (_, c) =>
      c.copy(verbose = true) } text("display what is happening")
    opt[Unit]('d',"debug") hidden() action { (_, c) =>
      c.copy(debug = true) } text("Show debugging info (security sensitive)")
    help("help") text("prints this usage text")
    arg[String]("<seed data>") optional() action { (x, c) =>
      c.copy(seedData = x) } text("Encrypted seed data, Crockford base32 encoded")
    note(s"\nYou can generate the seed like this:\n\n$SEED_COMMAND\n")
  }

  def main(args: Array[String]) {

    /* First we parse the command-line.
     * parser.parse returns an Option[C] */
    val(firstAccount,count,seedFile,seedData,brainWallet,verbose,debug) =
    parser.parse(args, Config()) map { c =>
      (c.firstAccount, c.count, c.seedFile, c.seedData, c.brainWallet, c.verbose, c.debug)
    } getOrElse {
      // arguments are bad; error message will have been displayed
      sys.exit(1)
    }

    /* Now we have processed the command-line options.
     * Next, we need to determine the secret seed of the hierarchy */

    val seed: Array[Byte] = if (brainWallet) {
      if (seedData.length > 0) System.err.println("Warning: ignoring seed data")
      if (seedFile.exists) System.err.println(s"Warning: ignoring file ${seedFile.getName}")
      print("Brain-wallet phrase (will echo): ")
      val phrase = (new BufferedReader(
        new InputStreamReader(System.in, Charset.forName("UTF-8"))
      )).readLine()
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.update(phrase.getBytes("UTF-8"))
      md.digest
    } else if (seedData.length > 0) {
      if (seedFile.exists) System.err.println(s"Warning: ignoring file ${seedFile.getName}")
      val encryptedBytes = base32.decode(seedData.replaceAll("[\\s]",""))
      decrypt(encryptedBytes, verbose)
    } else if (! seedFile.exists) {
      print("ERROR: seed file not found\nEither create a new seed like this:\n" +
	    SEED_COMMAND + """
	      |or else relocate an existing, encrypted seed file to seed.aes
              |or else pass a crockford base32 encoded encrypted seed as an argument
              |or else run in brain-wallet mode
              |""".stripMargin)
      sys.exit(2)
      null
    } else getSeed(seedFile, verbose, debug)

    /* Now we have the secret seed.
     * Next, ask the user to choose a spending pass-phrase for
     * the derived account(s), and then derive from that an
     * AES key for encrypting the account key(s). */

/*    print("Choose a spending pass-phrase (will echo): ")
    val spendingPhrase = (new BufferedReader(
      new InputStreamReader(System.in, Charset.forName("UTF-8"))
    )).readLine()
    val keyCrypter = new KeyCrypterScrypt
    val aesKey: KeyParameter = keyCrypter.deriveKey(spendingPhrase)  */

    /* Next, we determine the HD key at the root of the hierarchy */

    val masterPrivateKey: DeterministicKey = HDKeyDerivation createMasterPrivateKey seed
    if(debug) dumpKey(masterPrivateKey, "Master Node:")
    if(verbose) println("Master node " + masterPrivateKey.getPathAsString +
			" has fingerprint " +
                        bytesToLong(masterPrivateKey.getFingerprint).toString )

    val privHier = new DeterministicHierarchy(masterPrivateKey)

    /* Now a loop to generate the files for the user.  One iteration per account.
     * The account-numbers come from the command-line option, defaulting to 1
     */
    (firstAccount to ( firstAccount + (count - 1) ) ) foreach { accountNum =>
      val accountKey: DeterministicKey = privHier.deriveChild(
	EMPTY_PATH,
	NOT_RELATIVE,
	CREATE_PARENT,
	new ChildNumber(accountNum, HARDENED_DERIVATION)
      )
      val accountFingerprint: String = bytesToLong(accountKey.getFingerprint).toString

      // This is the external chain public key data the web server will have:
      val externChainPub: DeterministicKey = privHier.deriveChild(
	accountKey.getPath,
	NOT_RELATIVE,
	CREATE_PARENT,
	new ChildNumber(EXTERNAL_CHAIN, NORMAL_DERIVATION)
      ).getPubOnly

      if (debug) {
        dumpKey(accountKey, "Account private key")
        dumpKey(externChainPub, "External chain key")
      }

      /* Everything generated, now write the files */

      val fileTag = accountNum.toString + '-' + accountFingerprint + '_'
/*      val accountPrefix = "account" + fileTag
      val externalPrefix = "externalChain" + fileTag
      val accountPrivKeyFilename         = accountPrefix + "privateKey"
      val accountChaincodeFilename       = accountPrefix + "chaincode"
      val externalChainPubKeyFilename    = externalPrefix + "publicKey"
      val externalChainChaincodeFilename = externalPrefix + "chaincode" */
      val accountPrivFilename = "account" + fileTag + "priv.aes"
      val chainPubFilename = "externalChain" + fileTag + "pub.hdk"

      println(s"Writing files for account $accountNum; fingerprint is ${
	bytesToLong(accountKey.getFingerprint).toString}")

/*      writeEncryptedFile(accountKey.getPrivKeyBytes, accountPrivKeyFilename)
      println("  " + accountPrivKeyFilename)
      writeFile(accountKey.getChainCode, accountChaincodeFilename)
      println("  " + accountChaincodeFilename)

      writeFile(externChainPub.getPubKey, externalChainPubKeyFilename)
      println("  " + externalChainPubKeyFilename)
      writeFile(externChainPub.getChainCode, externalChainChaincodeFilename)
      println("  " + externalChainChaincodeFilename) */

      /* If BIP-38 is ever implemented in bitcoinj, we might use that instead of GPG */
      writeEncryptedFile (
        bytes    = accountKey.serializePrivate,
        filename = accountPrivFilename
      )
      println("  " + accountPrivFilename)
      writeFile(externChainPub.serializePublic, chainPubFilename)
      println("  " + chainPubFilename)
    }
  }

  private def bytesToLong(bytes: Array[Byte]): Long = {
    require(bytes.length == 4)
    java.nio.ByteBuffer.wrap(Array[Byte](0,0,0,0) ++ bytes).getLong
  }

  /** Read and decrypt an existing seed file, returning the unencrypted seed data */
  def getSeed(file: File, verbose: Boolean, debug: Boolean): Array[Byte] = {
    if (verbose) println(s"reading existing seed file ${file.getName}")

    try {
      def remaining(fis: FileInputStream, thusFar: Array[Byte]): Array[Byte] = {
	fis.read match {
	  case -1 => thusFar
	  case b => remaining(fis, thusFar :+ b.toByte)
	}
      }
      val fis = new FileInputStream(file)
      val encrypted = remaining(fis,Array[Byte]())
      fis.close()

      val process = Runtime.getRuntime.exec( Array (GPG_EXECUTABLE, "--decrypt", file.getName) )
      val seed = readOutput(process, verbose)
      if(debug) println(s"Seed data (NOT encrypted): ${base32.encodeToString(seed)}")

      println("Encrypted seed data:\n" +
	base32.encodeToString(encrypted).
	grouped(32).toList.map(r => r.grouped(4).toList.mkString("-")).mkString("\n")
      )

      seed
    } catch {
      case e: Throwable => println(s"Failed to read seed file: ${e.getMessage}")
      sys.exit(1)
    }
  }

  /* Takes an array of bytes, returns an array of bytes */
  private def decrypt(input: Array[Byte], verbose: Boolean): Array[Byte] = try {
    val process = Runtime.getRuntime.exec( Array (GPG_EXECUTABLE, "--decrypt") )
    val os = process.getOutputStream()
    os.write(input)
    os.close()
    readOutput(process, verbose)
  } catch {
    case e: Exception => System.err.println(s"Failed to decrypt key: ${e.getMessage}")
    sys.exit(1)
  }

  /** Read the output of another process, for example, gpg decrypting the seed */
  private def readOutput(process: java.lang.Process, verbose: Boolean): Array[Byte] = {
    val returnValue = process.waitFor()

    def readInput(is: java.io.InputStream): Array[Byte] = {
      def readRest(sofar: Array[Byte], is: java.io.InputStream): Array[Byte] = {
	is.read match {
	  case -1 => sofar
	  case c => readRest (sofar :+ c.toByte, is)
	}
      }
      readRest(Array[Byte](), is)
    }

    if (returnValue == 0) {
      val bytes = readInput(process.getInputStream)
      if (verbose) println(s"Seed is ${bytes.length * 8} bits")
      return bytes
    } else {
      throw new Exception(new String(readInput(process.getErrorStream)))
      null
    }
  }

  def writeEncryptedFile(bytes: Array[Byte], filename: String) {
    try {
      val process = Runtime.getRuntime.exec( Array (
	GPG_EXECUTABLE, "--symmetric", "--cipher-algo", "AES256", "--output", filename
      ) )
      val os = process.getOutputStream()
      os.write(bytes)
      os.close()
      process.waitFor match {
	case 0 =>
	case ec => throw new Exception(s"gpg returned error code $ec")
      }
    } catch {
      case e: Exception => System.err.println(s"Failed to save encrypted file: ${e.getMessage}")
      sys.exit(1)
    }

  }

  def writeFile(bytes: Array[Byte], filename: String) {
    try {
      val fos = new java.io.FileOutputStream(filename)
      fos.write(bytes)
      fos.close()
    } catch {
      case e: Throwable => println(s"Failed to write file $filename: ${e.getMessage}")
      sys.exit(1)
    }
  }

  /** Debugging method: prints interesting details about the given key
    * to stdout */
  private def dumpKey(key: DeterministicKey, label: String) {
    println(label)
    println(s"  $key")
    println(s"  Fingerprint: ${bytesToLong(key.getFingerprint).toLong}")
    println(s"  pub: ${key.serializePubB58}")
    if (key.hasPrivKey) println(s"  priv: ${key.serializePrivB58}")
    else println("  no private key")

    val path = key.getPath
    println(s"  path is ${key.getPathAsString}")
  }

  /* Some constants */
  final val EMPTY_PATH = ImmutableList.of[ChildNumber]()
  /* to make reading the HD functions easier */
  private final val HARDENED_DERIVATION = true
  private final val NORMAL_DERIVATION = false
  private final val RELATIVE = true
  private final val NOT_RELATIVE = false
  private final val CREATE_PARENT = true
  private final val DONT_CREATE_PARENT = false
  private final val EXTERNAL_CHAIN = 0
  private final val INTERNAL_CHAIN = 1

}
