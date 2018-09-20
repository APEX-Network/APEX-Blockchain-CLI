/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Wallet.scala
 *
 * @author: shan.huang@chinapex.com: 2018-07-27 下午4:06@version: 1.0
 */

package com.apex.cli

import com.apex.core._
import com.apex.crypto.{Base58Check, BinaryData, Crypto, Fixed8, UInt160, UInt256}
import com.apex.crypto.Ecdsa.PrivateKey
import com.apex.core.script.Script

import scala.collection.mutable.{ArrayBuffer, Set}
//import java.security.SecureRandom

object Wallet {

  private val privKeys = Set.empty[PrivateKey]
//
//  def getBalance(address: String, assetId: UInt256): Fixed8 = {
//    var sum: Fixed8 = Fixed8.Zero
//    val utxoSet = Blockchain.Current.getUTXOByAddress(toScriptHash(address).get)
//    if (utxoSet != None) {
//      for (utxo <- utxoSet.get) {
//        val tx = Blockchain.Current.getTransaction(utxo._1).get
//        if (tx.outputs(utxo._2).assetId == assetId) {
//          sum += tx.outputs(utxo._2).amount
//        }
//      }
//    }
//    sum
//  }
//
//  def getBalance(assetId: UInt256): Fixed8 = {
//    var sum = Fixed8.Zero
//    for (pKey <- privKeys) {
//      sum += getBalance(pKey.publicKey.toAddress, assetId)
//    }
//    sum
//  }
//
//  def findPrivKey(pubKeyHash: UInt160): Option[PrivateKey] = {
//    // fixme: use map or db
//    for (pKey <- privKeys) {
//      if (pubKeyHash.compare(UInt160.fromBytes(pKey.publicKey.hash160)) == 0)
//        return Some(pKey)
//    }
//    None
//  }
//

  def getPrivKey(): PrivateKey = {
    privKeys.head
  }

  def generateNewPrivKey() = {
    privKeys.add(new PrivateKey(BinaryData(Crypto.randomBytes(32))))
  }

  def importPrivKeyFromWIF(wif: String): Boolean = {
    val key = getPrivKeyFromWIF(wif)
    if (key != None) {
      privKeys.add(new PrivateKey(BinaryData(key.get), true))
      true
    }
    else
      false
  }

  def getPrivKeyFromWIF(wif: String): Option[Array[Byte]] = {
    val decode = Base58Check.decode(wif).getOrElse(Array[Byte]())
    if (decode.length == 34) {
      // 1 bytes prefix + 32 bytes data + 1 byte 0x01 (+ 4 bytes checksum)
      if (decode(33) == 0x01.toByte) {
        Some(decode.slice(1, 33))
      } else {
        None
      }
    } else {
      None
    }
  }
//
//  def privKeyToWIF(privateKey: Array[Byte]): String = {
//    assert(privateKey.length == 32)
//    var data = new Array[Byte](34)
//    // 0x80: mainnet
//    data(0) = 0x80.toByte
//    Array.copy(privateKey, 0, data, 1, 32)
//    // 0x01: compressed
//    data(33) = 0x01.toByte
//    Base58Check.encode(data)
//  }
//
//  def toAddress(scriptHash: Array[Byte]): String = {
//    assert(scriptHash.length == 20)
//
//    // "0548" is for the "AP" prefix
//    Base58Check.encode(BinaryData("0548"), scriptHash)
//  }
//
//  def toAddress(scriptHash: UInt160): String = {
//    toAddress(scriptHash.data)
//  }
//
//  def toScriptHash(address: String): Option[UInt160] = {
//    if (address.startsWith("AP") && address.length == 35) {
//      val decode = Base58Check.decode(address).getOrElse(Array[Byte]())
//      if (decode.length == 22) {
//        // 2 bytes prefix + 20 bytes data (+ 4 bytes checksum)
//        Some(UInt160.fromBytes(decode.slice(2, 22)))
//      } else {
//        None
//      }
//    } else {
//      None
//    }
//  }

}

//object Wallet {
//  final val Default: Wallet = new Wallet
//}