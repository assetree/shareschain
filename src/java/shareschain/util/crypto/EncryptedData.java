
package shareschain.util.crypto;

import shareschain.ShareschainExceptions;
import shareschain.util.Convert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EncryptedData {

    public static final EncryptedData EMPTY_DATA = new EncryptedData(new byte[0], new byte[0]);

    /**
     * 返回加密后的消息(EncryptedData对象)包括加密后的消息和随机数
     * 1.获取一个安全的32位的随机数
     * 2.获取共享的秘钥
     * 3.将压缩的消息，经过共享秘钥进行aes加密并返回
     * @param plaintext  压缩之后的消息
     * @param secretPhrase 发送人密码
     * @param theirPublicKey 接收人的公钥
     * @return
     */
    public static EncryptedData encrypt(byte[] plaintext, String secretPhrase, byte[] theirPublicKey) {
        if (plaintext.length == 0) {
            return EMPTY_DATA;
        }
        byte[] nonce = new byte[32];
        //生成强随机数
        Crypto.getSecureRandom().nextBytes(nonce);
        /**
         * 获取共享的秘钥
         * 首先通过发送者的密码经过sha256算法计算出发送者的私钥
         * 再通过发送者的私钥 + 接受者的公钥+nonce强安全32位的随机数计算出共享的秘钥
         */
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, nonce);
        //将压缩的消息，经过共享秘钥进行aes加密
        byte[] data = Crypto.aesEncrypt(plaintext, sharedKey);
        return new EncryptedData(data, nonce);
    }

    public static EncryptedData readEncryptedData(ByteBuffer buffer, int length, int maxLength)
            throws ShareschainExceptions.NotValidExceptions {
        if (length == 0) {
            return EMPTY_DATA;
        }
        if (length > maxLength) {
            throw new ShareschainExceptions.NotValidExceptions("Max encrypted data length exceeded: " + length);
        }
        byte[] data = new byte[length];
        buffer.get(data);
        byte[] nonce = new byte[32];
        buffer.get(nonce);
        return new EncryptedData(data, nonce);
    }

    public static EncryptedData readEncryptedData(byte[] bytes) {
        if (bytes.length == 0) {
            return EMPTY_DATA;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readEncryptedData(buffer, bytes.length - 32, Integer.MAX_VALUE);
        } catch (ShareschainExceptions.NotValidExceptions e) {
            throw new RuntimeException(e.toString(), e); // never
        }
    }

    public static int getEncryptedDataLength(byte[] plaintext) {
        if (plaintext.length == 0) {
            return 0;
        }
        return Crypto.aesEncrypt(plaintext, new byte[32]).length;
    }

    public static int getEncryptedSize(byte[] plaintext) {
        if (plaintext.length == 0) {
            return 0;
        }
        return getEncryptedDataLength(plaintext) + 32;
    }

    private final byte[] data;
    private final byte[] nonce;

    public EncryptedData(byte[] data, byte[] nonce) {
        this.data = data;
        this.nonce = nonce;
    }

    public byte[] decrypt(String secretPhrase, byte[] theirPublicKey) {
        if (data.length == 0) {
            return data;
        }
        byte[] sharedKey = Crypto.getSharedKey(Crypto.getPrivateKey(secretPhrase), theirPublicKey, nonce);
        return Crypto.aesDecrypt(data, sharedKey);
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public int getSize() {
        return data.length + nonce.length;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(nonce.length + data.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(data);
        buffer.put(nonce);
        return buffer.array();
    }

    @Override
    public String toString() {
        return "data: " + Convert.toHexString(data) + " nonce: " + Convert.toHexString(nonce);
    }

}
