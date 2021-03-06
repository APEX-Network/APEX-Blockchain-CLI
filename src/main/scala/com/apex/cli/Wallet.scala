package com.apex.cli

import java.io._
import java.nio.file.{Files, Paths}
import java.util.Calendar
import com.apex.cli.Account.checkWalletStatus
import com.apex.crypto.Crypto
import javax.crypto.BadPaddingException
import org.apache.commons.net.util.Base64
import scala.collection.mutable
import scala.io.Source

/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Contract.scala
 *
 * @author: whitney.wei@chinapex.com: 18-12-20 @version: 1.0
 */
class Wallet(val n: String, val p: Array[Byte], val accounts: Seq[Account]) extends com.apex.common.Serializable {

  def serialize(os: DataOutputStream) = {
    import com.apex.common.Serializable._
    os.writeString(n)
    os.writeByteArray(p)
    os.writeSeq(accounts)
  }
}

object Wallet {
  var Default: Wallet = null

  def deserialize(is: DataInputStream): Wallet = {
    import com.apex.common.Serializable._
    val n = is.readString()
    val keys = is.readByteArray()
    val accounts = is.readSeq(Account.deserialize)
    new Wallet(n, keys, accounts)
  }

  def fromBytes(data: Array[Byte]): Wallet = {
    val bs = new ByteArrayInputStream(data)
    val is = new DataInputStream(bs)
    deserialize(is)
  }

  private val regex = """^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{8,}$""".r

  def checkPassword(password: String): Boolean = {
    regex.pattern.matcher(password).matches()
  }

  def enterPassword(): String = {
    val cnsl = System.console()
    var yes = true
    var password = ""

    while (yes) {
      password = cnsl.readPassword("please enter password: ").mkString
      if (!Wallet.checkPassword(password)) {
        println("Incorrect password format, password length must be greater than or equal to 8 and contain letters and numbers.")
        val c = cnsl.readLine("do you want to re-enter the password[Y/N]?")
        if (!c.toUpperCase().equals("Y")) {
          yes = false
          password = ""
        }
      } else {
        yes = false
      }
    }
    password
  }

}

class WalletCache(val n: String, val p: Array[Byte],
                  var lastModify: Long = Calendar.getInstance().getTimeInMillis,
                  var activate: Boolean = true, var implyAccount: String = "",
                  var accounts: Seq[Account] = Seq[Account]()) {

}

object WalletCache {

  val walletCaches: mutable.HashMap[String, WalletCache] = new mutable.HashMap[String, WalletCache]()
  var activityWallet: String = ""

  def get(n: String): WalletCache = {
    if (walletCaches.contains(n))
      walletCaches.get(n).get
    else null
  }

  def getActivityWallet(): WalletCache = {
    get(activityWallet)
  }

  def isExist(n: String): Boolean = {
    if (walletCaches.contains(n)) true
    else false
  }

  def remove(n: String) {
    walletCaches.remove(n)
    if (activityWallet.equals(n)) activityWallet = ""
  }

  def size(): Int = {
    walletCaches.size
  }

  def reActWallet: Unit = {
    if (WalletCache.getActivityWallet() != null)
      WalletCache.getActivityWallet().lastModify = Calendar.getInstance().getTimeInMillis
  }

  def checkTime(): Boolean = {
    val walletCache = get(WalletCache.activityWallet)
    val between = Calendar.getInstance().getTimeInMillis - walletCache.lastModify
    val minute = between / 1000 / 60
    /*val hour = between / 1000 / 3600
    val day = between / 1000 / 3600 / 24
    val year = between / 1000 / 3600 / 24 / 365*/

    if (minute < 5) true
    else false
  }

  def newWalletCache(wallet: Wallet): mutable.HashMap[String, WalletCache] = {

    if (!walletCaches.contains(wallet.n)) walletCaches.put(wallet.n, new WalletCache(wallet.n, wallet.p, accounts = wallet.accounts))
    setActivate(wallet.n)
    walletCaches
  }

  def setActivate(n: String): Unit = {

    for (key <- walletCaches.keys) {
      val walletCache = walletCaches.get(key).get
      if (!walletCache.accounts.isEmpty) walletCache.implyAccount = walletCache.accounts(0).n
      if (n.equals(key)) {
        walletCache.activate = true
        walletCache.lastModify = Calendar.getInstance().getTimeInMillis
        WalletCache.activityWallet = key
      } else {
        walletCache.activate = false
      }
    }
  }

  val filePath = System.getProperty("user.home") + "/cli_wallet/"

  def fileExist(name: String): Boolean = {
    val path = filePath + name + ".wallet"

    Files.exists(Paths.get(path))
  }

  def getFileList(): Array[File] = {
    new File(filePath).listFiles().filter(!_.isDirectory).filter(f => f.getName.endsWith("wallet"))
  }

  def readWallet(name: String): String = {
    val path = filePath + name + ".wallet"
    val file = Source.fromFile(path)
    val walletContent = file.getLines.mkString
    file.close()
    walletContent
  }

  def writeActWallet: Unit = {
    val walletCache = WalletCache.getActivityWallet()
    WalletCache.writeWallet(walletCache.n, walletCache.p, walletCache.accounts)
  }

  def writeWallet(name: String, key: Array[Byte], accounts: Seq[Account]): Wallet = {

    val file = new File(filePath)
    // 判断文件夹是否存在，不存在则创建
    if (!file.exists() && !file.isDirectory()) {
      file.mkdir()
      val exportFile = new File(filePath + "export")
      exportFile.mkdir()
    }

    val path = filePath + name + ".wallet"

    val wallet = new Wallet(name, key, accounts)

    val bs = new ByteArrayOutputStream()
    val os = new DataOutputStream(bs)

    wallet.serialize(os)

    val iv: Array[Byte] = new Array(16)
    key.copyToArray(iv, 0, 16)

    // 加密用户输入的密码
    val encrypted1 = Crypto.AesEncrypt(bs.toByteArray, key, iv)

    val fw = new FileWriter(path)
    val encodeBase64 = Base64.encodeBase64(encrypted1)
    fw.write(new String(encodeBase64))
    fw.close()

    wallet
  }

  def exportAccount(privkey: String, fileName: String): Unit = {
    val path = filePath + "export/" + fileName

    val writer = new PrintWriter(new File(path))

    writer.write(privkey)
    writer.close()
  }

}

class WalletCommand extends CompositeCommand {

  override val cmd: String = "wallet"
  override val description: String = "Operate a wallet, user accounts must add to one wallet before using it"
  override val composite: Boolean = true

  override val subCommands: Seq[Command] = Seq(
    new WalletNewCommand,
    new WalletCloseCommand,
    new WalletActivateCommand,
    new WalletActCommand,
    new WalletListCommand
  )


  def loadWallet(name: String, inputPwd: String) {
    // 获取文件内容
    val walletContent = WalletCache.readWallet(name)

    // 加密用户输入密码，并解密文件
    val key = Crypto.sha256(inputPwd.getBytes("UTF-8"))
    val iv: Array[Byte] = new Array(16)
    key.copyToArray(iv, 0, 16)

    var dec: Array[Byte] = new Array[Byte](1000)
    // 解密文件内容
    val base64decoder = Base64.decodeBase64(walletContent)
    dec = Crypto.AesDecrypt(base64decoder, key, iv)
    // 将对象反序列化
    val wallet = Wallet.fromBytes(dec)

    WalletCache.newWalletCache(wallet)
  }

  class WalletNewCommand extends Command {

    override val cmd: String = "new"
    override val description: String = "create a new wallet"

    override val paramList: ParameterList = ParameterList.create(
      new StringParameter("name", "n", "Wallet's name.")
    )

    override def execute(params: List[String]): Result = {

      try {
        val name = paramList.params(0).asInstanceOf[StringParameter].value

        if (WalletCache.fileExist(name)) InvalidParams("Wallet [" + name + "] already exists, please type a different name")
        else {
          val password = Wallet.enterPassword()
          if (password.isEmpty) Help("Welcome to CLI, type \"help\" for command list:")
          else {
            val key = Crypto.sha256(password.getBytes("UTF-8"))

            val wallet = WalletCache.writeWallet(name, key, Seq.empty)
            WalletCache.newWalletCache(wallet)
            Success("wallet create success\n")
          }
        }
      } catch {
        case e: BadPaddingException => InvalidParams("Invalid password\n")
        case e: Throwable => Error(e)
      }
    }

  }

  @Deprecated
  class WalletLoadCommand extends Command {

    override val cmd: String = "load"
    override val description: String = "load an existed wallet"

    override val paramList: ParameterList = ParameterList.create(
      new StringParameter("name", "n", "Wallet's name.")
    )

    override def execute(params: List[String]): Result = {

      try {
        val name = paramList.params(0).asInstanceOf[StringParameter].value

        if (!WalletCache.fileExist(name)) InvalidParams("Wallet [" + name + "] does not exist\n")
        else if (WalletCache.get(name) != null)
          InvalidParams("Wallet [" + name + "] already exists, please type a different name")
        else {
          val password = Wallet.enterPassword()
          if (password.isEmpty) Help("Welcome to CLI, type \"help\" for command list:")
          else {
            loadWallet(name, password)
            Success("wallet load success\n")
          }
        }
      } catch {
        case e: BadPaddingException => InvalidParams("Invalid password\n")
        case e: Throwable => Error(e)
      }
    }
  }

  class WalletCloseCommand extends Command {

    override val cmd: String = "close"
    override val description: String = "Close the currently active wallet"

    override def execute(params: List[String]): Result = {

      try {
        val name = WalletCache.activityWallet
        WalletCache.remove(name)
        Success("wallet close success\n")
      } catch {
        case e: Throwable => Error(e)
      }
    }
  }

  class WalletActivateCommand extends Command {

    override val cmd: String = "activate"
    override val description: String = "Activate an existing wallet, only one wallet can be activated at a time"

    override val paramList: ParameterList = ParameterList.create(
      new StringParameter("name", "n", "Wallet's name.")
    )

    override def execute(params: List[String]): Result = {

      try {
        val name = paramList.params(0).asInstanceOf[StringParameter].value

        // 判断要激活的钱包是否存在

        val password = Wallet.enterPassword()
        if (password.isEmpty) Help("Welcome to CLI, type \"help\" for command list:")
        else {
          loadWallet(name, password)
          // 设置钱包的状态
          WalletCache.setActivate(name)
          Success("wallet activate success\n")

        }
      } catch {
        case e: BadPaddingException => InvalidParams("Invalid password\n")
        case e: Throwable => Error(e)
      }
    }
  }

  class WalletActCommand extends WalletActivateCommand {
    override val cmd: String = "act"
  }

  class WalletListCommand extends Command {

    override val cmd: String = "list"
    override val description: String = "list existing wallets"

    override def execute(params: List[String]): Result = {
      try {
        var walletListFile = WalletCache.getFileList()
        if (walletListFile.size == 0) {
          println("No found wallet in local, please use 'wallet new -n xxx' to create a wallet first.")
        } else {
          println("Wallet  --  Activated")
          walletListFile.foreach { i =>
            val filename = i.getName
            if (filename.endsWith(".wallet")) {
              val walletname = filename.substring(0, filename.lastIndexOf("."))
              print(walletname + "  --  ")

              val wallet = WalletCache.get(walletname)
              if (wallet == null || !wallet.activate) print("False")
              else  print("True")

              println("")
            }
          }
        }
        Success("")
      } catch {
        case e: Throwable => {
          e.printStackTrace()
          Error(e)
        }
      }
    }
  }

}
