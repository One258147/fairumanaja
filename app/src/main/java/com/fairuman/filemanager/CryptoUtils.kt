package com.fairuman.filemanager

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 文件 AES 加密 / 解密工具（v1.9.0 新增：书签菜单里的“文件加密 / 文件解密”用它）。
 *
 * 算法：AES/CBC/PKCS5Padding，密钥用 PBKDF2WithHmacSHA256（65536 次迭代、256 位）
 * 从密码 + 16 字节随机盐派生；每个文件再用一个随机的 16 字节 IV。
 *
 * 加密文件格式（全部按顺序拼接）：
 *   魔数 "FAIRUENC1"（9 字节 ASCII）+ 盐（16 字节）+ IV（16 字节）+ 密文
 *
 * 读写都是流式（8KB 缓冲），不把整个文件读进内存，大文件也不会 OOM。
 * 加密成功后删除原文件，解密成功后删除 .enc 文件；中途失败会删掉不完整的输出文件。
 */
object CryptoUtils {

    /** 文件头魔数，9 字节 ASCII，用于识别是不是本工具加密出来的文件。 */
    private val MAGIC = "FAIRUENC1".toByteArray(Charsets.US_ASCII)

    /** 加密后的文件后缀。 */
    const val ENC_SUFFIX = ".enc"

    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_BITS = 256
    private const val BUFFER_SIZE = 8192

    /** 用密码 + 盐派生 256 位 AES 密钥。 */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /** 从流里读满 [len] 字节，读不够（文件太短）返回 false。 */
    private fun readFully(input: FileInputStream, buf: ByteArray, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = input.read(buf, read, len - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    /**
     * 加密 [input]，生成同目录下的 `原名.enc` 文件。
     * 成功后删除原文件并返回加密后的文件；失败时删除不完整的 .enc 并抛异常。
     */
    fun encrypt(input: File, password: String): File {
        val output = File(input.absolutePath + ENC_SUFFIX)
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
            FileInputStream(input).use { fis ->
                FileOutputStream(output).use { fos ->
                    // 先写文件头：魔数 + 盐 + IV，再写密文。
                    fos.write(MAGIC)
                    fos.write(salt)
                    fos.write(iv)
                    CipherOutputStream(fos, cipher).use { cos ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (fis.read(buf).also { n = it } >= 0) {
                            cos.write(buf, 0, n)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 失败不留半成品。
            output.delete()
            throw e
        }
        // 加密成功才删原文件。
        input.delete()
        return output
    }

    /**
     * 解密 [encFile]，写回原名（去掉末尾 ".enc"）。
     * 文件头魔数不对抛 [IllegalArgumentException]；密码错时解密抛
     * [BadPaddingException]（不吞掉，调用方据此区分“密码错”和其它失败）。
     * 成功后删除 .enc 文件并返回解密出的文件；失败时删除不完整输出并抛异常。
     */
    fun decrypt(encFile: File, password: String): File {
        val output = File(originalName(encFile.absolutePath))
        try {
            FileInputStream(encFile).use { fis ->
                // 校验文件头魔数。
                val magic = ByteArray(MAGIC.size)
                if (!readFully(fis, magic, MAGIC.size) || !magic.contentEquals(MAGIC)) {
                    throw IllegalArgumentException("不是 Fairumanējā 加密文件：${encFile.name}")
                }
                val salt = ByteArray(SALT_LEN)
                val iv = ByteArray(IV_LEN)
                if (!readFully(fis, salt, SALT_LEN) || !readFully(fis, iv, IV_LEN)) {
                    throw IllegalArgumentException("加密文件损坏：文件头不完整：${encFile.name}")
                }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
                FileOutputStream(output).use { fos ->
                    CipherInputStream(fis, cipher).use { cis ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (cis.read(buf).also { n = it } >= 0) {
                            fos.write(buf, 0, n)
                        }
                    }
                }
            }
        } catch (e: BadPaddingException) {
            // 密码错：直接抛给调用方区分，不要吞掉。
            output.delete()
            throw e
        } catch (e: java.io.IOException) {
            // CipherInputStream 会把解密失败的 BadPaddingException 包进 IOException，
            // 拆出来原样抛出，让调用方仍能认出是“密码错”。
            val cause = e.cause
            output.delete()
            if (cause is BadPaddingException) throw cause
            throw e
        } catch (e: Exception) {
            output.delete()
            throw e
        }
        // 解密成功才删 .enc 文件。
        encFile.delete()
        return output
    }

    /**
     * 判断 [file] 是不是本工具加密出来的文件：文件名以 ".enc" 结尾，
     * 且前 9 字节是魔数。文件不存在或读不到都返回 false，不抛异常。
     */
    fun isEncrypted(file: File): Boolean {
        if (!file.name.endsWith(ENC_SUFFIX)) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(MAGIC.size)
                readFully(fis, magic, MAGIC.size) && magic.contentEquals(MAGIC)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 去掉文件名末尾的 ".enc"（没有就不变），得到解密后的原名。 */
    fun originalName(encName: String): String =
        if (encName.endsWith(ENC_SUFFIX)) encName.dropLast(ENC_SUFFIX.length) else encName
}
