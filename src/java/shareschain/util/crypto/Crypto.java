
package shareschain.util.crypto;

import shareschain.Shareschain;
import shareschain.util.Convert;
import shareschain.util.Logger;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.RIPEMD160;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

/**
 * 密码类
 */
public final class Crypto {

    private static final boolean useStrongSecureRandom = Shareschain.getBooleanProperty("shareschain.useStrongSecureRandom");

    private static final ThreadLocal<SecureRandom> secureRandom = ThreadLocal.withInitial(() -> {
        try {
            SecureRandom secureRandom = useStrongSecureRandom ? SecureRandom.getInstanceStrong() : new SecureRandom();
            secureRandom.nextBoolean();
            return secureRandom;
        } catch (NoSuchAlgorithmException e) {
            Logger.logErrorMessage("No secure random provider available");
            throw new RuntimeException(e.getMessage(), e);
        }
    });

    private Crypto() {} //never

    public static SecureRandom getSecureRandom() {
        return secureRandom.get();
    }

    public static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            Logger.logMessage("Missing message digest algorithm: " + algorithm);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static MessageDigest sha256() {
        return getMessageDigest("SHA-256");
    }

    public static MessageDigest ripemd160() {
        return new RIPEMD160.Digest();
    }

    public static MessageDigest sha3() {
        return new Keccak.Digest256();
    }

    public static byte[] getKeySeed(String secretPhrase, byte[]... nonces) {
        MessageDigest digest = Crypto.sha256();
        digest.update(Convert.toBytes(secretPhrase));
        for (byte[] nonce : nonces) {
            digest.update(nonce);
        }
        return digest.digest();
    }

    public static byte[] getPublicKey(byte[] keySeed) {
        byte[] publicKey = new byte[32];
        Curve25519.keygen(publicKey, null, Arrays.copyOf(keySeed, keySeed.length));
        return publicKey;
    }


    //把私钥(密码)转换成 bytes数组，在经过 sha256加密算法，生成公钥
    public static byte[] getPublicKey(String secretPhrase) {
        byte[] publicKey = new byte[32];
        Curve25519.keygen(publicKey, null, Crypto.sha256().digest(Convert.toBytes(secretPhrase)));
        return publicKey;
    }

    public static byte[] getPrivateKey(byte[] keySeed) {
        byte[] s = Arrays.copyOf(keySeed, keySeed.length);
        Curve25519.clamp(s);
        return s;
    }

    /**
     * 获取发送人的私钥
     * 将发送人密码经过sha256 算法加密，返回长度为256为的哈希值
     * @param secretPhrase
     * @return
     */
    public static byte[] getPrivateKey(String secretPhrase) {
        byte[] s = Crypto.sha256().digest(Convert.toBytes(secretPhrase));
        Curve25519.clamp(s);
        return s;
    }

    public static void curve(byte[] Z, byte[] k, byte[] P) {
        Curve25519.curve(Z, k, P);
    }

    //利用密码对交易或消息签名加密64位
    public static byte[] sign(byte[] message, String secretPhrase) {
        byte[] P = new byte[32];
        byte[] s = new byte[32];
        MessageDigest digest = Crypto.sha256();
        Curve25519.keygen(P, s, digest.digest(Convert.toBytes(secretPhrase)));

        byte[] m = digest.digest(message);

        digest.update(m);
        byte[] x = digest.digest(s);

        byte[] Y = new byte[32];
        Curve25519.keygen(Y, null, x);

        digest.update(m);
        byte[] h = digest.digest(Y);

        byte[] v = new byte[32];
        Curve25519.sign(v, h, x, s);

        byte[] signature = new byte[64];
        System.arraycopy(v, 0, signature, 0, 32);
        System.arraycopy(h, 0, signature, 32, 32);
        return signature;
    }

    /**
     *签名验证
     * @param signature 签名
     * @param message  消息
     * @param publicKey  公钥
     * @return
     */
    public static boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        try {
            if (signature.length != 64) {//签名长度是否合法
                return false;
            }
            //是否是一个典型的签名 判断前32位
            if (!Curve25519.isCanonicalSignature(signature)) {
                Logger.logDebugMessage("Rejecting non-canonical signature");
                return false;
            }

            if (!Curve25519.isCanonicalPublicKey(publicKey)) {
                Logger.logDebugMessage("Rejecting non-canonical public key");
                return false;
            }

            byte[] Y = new byte[32];
            byte[] v = new byte[32];
            //Logger.logInfoMessage("交易的签名signature-------"+Arrays.toString(signature));
            //Logger.logInfoMessage("公钥byte publicKey-------"+Arrays.toString(publicKey));
            //将签名的前32位赋值给v
            System.arraycopy(signature, 0, v, 0, 32);
            byte[] h = new byte[32];
            //将签名的后32位赋值给h
            System.arraycopy(signature, 32, h, 0, 32);
            Curve25519.verify(Y, v, h, publicKey);

            //指定加密算法sha256
            MessageDigest digest = Crypto.sha256();
            //使用指定的字节数组对摘要进行更新，然后完成摘要计算。计算出byte[] m
            byte[] m = digest.digest(message);
            //使用指定的字节数组更新摘要。,计算出摘要byte[] h2
            digest.update(m);
            byte[] h2 = digest.digest(Y);

            return Arrays.equals(h, h2);
        } catch (RuntimeException e) {
            Logger.logErrorMessage("Error verifying signature", e);
            return false;
        }
    }

    public static byte[] getSharedKey(byte[] myPrivateKey, byte[] theirPublicKey) {
        return sha256().digest(getSharedSecret(myPrivateKey, theirPublicKey));
    }

    /**
     * 获取共享秘钥
     * 1.首先根据发送者的私钥和接受者的公钥进行曲线加密算法，算出一个共享秘钥的初始值
     * 2.再通过随机数随机替换共享秘钥的初始值
     * 3.共享秘钥的初始值再经过sha256加密算出共享秘钥
     * @param myPrivateKey 发送者的私钥
     * @param theirPublicKey 接受者的公钥
     * @param nonce 32位强随机数
     * @return
     */
    public static byte[] getSharedKey(byte[] myPrivateKey, byte[] theirPublicKey, byte[] nonce) {
        byte[] dhSharedSecret = getSharedSecret(myPrivateKey, theirPublicKey);
        for (int i = 0; i < 32; i++) {//用随机数随机替换
            dhSharedSecret[i] ^= nonce[i];
        }
        return sha256().digest(dhSharedSecret);
    }

    /**
     * 利用发送者的私钥和接收人的公钥，经过曲线加密算法获取共享的秘钥
     *
     * @param myPrivateKey 发送者的私钥
     * @param theirPublicKey 接受者的公钥
     * @return
     */
    private static byte[] getSharedSecret(byte[] myPrivateKey, byte[] theirPublicKey) {
        try {
            byte[] sharedSecret = new byte[32];
            Curve25519.curve(sharedSecret, myPrivateKey, theirPublicKey);
            return sharedSecret;
        } catch (RuntimeException e) {
            Logger.logMessage("Error getting shared secret", e);
            throw e;
        }
    }

    public static byte[] aesEncrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = new byte[16];
            secureRandom.get().nextBytes(iv);
            PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
                    new AESEngine()));
            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
            aes.init(true, ivAndKey);
            byte[] output = new byte[aes.getOutputSize(plaintext.length)];
            int ciphertextLength = aes.processBytes(plaintext, 0, plaintext.length, output, 0);
            ciphertextLength += aes.doFinal(output, ciphertextLength);
            byte[] result = new byte[iv.length + ciphertextLength];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(output, 0, result, iv.length, ciphertextLength);
            return result;
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] aesGCMEncrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = new byte[16];
            secureRandom.get().nextBytes(iv);
            GCMBlockCipher aes = new GCMBlockCipher(new AESEngine());
            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
            aes.init(true, ivAndKey);
            byte[] output = new byte[aes.getOutputSize(plaintext.length)];
            int ciphertextLength = aes.processBytes(plaintext, 0, plaintext.length, output, 0);
            ciphertextLength += aes.doFinal(output, ciphertextLength);
            byte[] result = new byte[iv.length + ciphertextLength];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(output, 0, result, iv.length, ciphertextLength);
            return result;
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] aesDecrypt(byte[] ivCiphertext, byte[] key) {
        try {
            if (ivCiphertext.length < 16 || ivCiphertext.length % 16 != 0) {
                throw new InvalidCipherTextException("invalid ivCiphertext length");
            }
            byte[] iv = Arrays.copyOfRange(ivCiphertext, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(ivCiphertext, 16, ivCiphertext.length);
            PaddedBufferedBlockCipher aes = new PaddedBufferedBlockCipher(new CBCBlockCipher(
                    new AESEngine()));
            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
            aes.init(false, ivAndKey);
            byte[] output = new byte[aes.getOutputSize(ciphertext.length)];
            int plaintextLength = aes.processBytes(ciphertext, 0, ciphertext.length, output, 0);
            plaintextLength += aes.doFinal(output, plaintextLength);
            byte[] result = new byte[plaintextLength];
            System.arraycopy(output, 0, result, 0, result.length);
            return result;
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static byte[] aesGCMDecrypt(byte[] ivCiphertext, byte[] key) {
        try {
            if (ivCiphertext.length < 16) {
                throw new InvalidCipherTextException("invalid ivCiphertext length");
            }
            byte[] iv = Arrays.copyOfRange(ivCiphertext, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(ivCiphertext, 16, ivCiphertext.length);
            GCMBlockCipher aes = new GCMBlockCipher(new AESEngine());
            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(key), iv);
            aes.init(false, ivAndKey);
            byte[] output = new byte[aes.getOutputSize(ciphertext.length)];
            int plaintextLength = aes.processBytes(ciphertext, 0, ciphertext.length, output, 0);
            plaintextLength += aes.doFinal(output, plaintextLength);
            byte[] result = new byte[plaintextLength];
            System.arraycopy(output, 0, result, 0, result.length);
            return result;
        } catch (InvalidCipherTextException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static String rsEncode(long id) {
        return Base58.encode(id);
//        return ReedSolomon.encode(id);
    }

    public static long rsDecode(String rsString) {
//        rsString = rsString.toUpperCase(Locale.ROOT);
        try {
            long id = Base58.decode(rsString);
            if (! rsString.equals(Base58.encode(id))) {
                throw new RuntimeException("ERROR: Reed-Solomon decoding of " + rsString
                        + " not reversible, decoded to " + id);
            }
            return id;
        } catch (Exception e) {
            Logger.logDebugMessage("Reed-Solomon decoding failed for " + rsString + ": " + e.toString());
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static boolean isCanonicalPublicKey(byte[] publicKey) {
        return Curve25519.isCanonicalPublicKey(publicKey);
    }

    public static boolean isCanonicalSignature(byte[] signature) {
        return Curve25519.isCanonicalSignature(signature);
    }

}
