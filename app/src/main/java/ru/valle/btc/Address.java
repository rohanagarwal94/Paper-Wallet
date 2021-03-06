package ru.valle.btc;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import static ru.valle.btc.BTCUtils.decodeBase58;

public final class Address {
    static final int TYPE_MAINNET = 0;
    static final int TYPE_TESTNET = 111;
    static final int TYPE_P2SH = 5;
    static final int TYPE_P2SH_TESTNET = 196;
    static final int TYPE_NONE = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_NONE, TYPE_MAINNET, TYPE_TESTNET, TYPE_P2SH, TYPE_P2SH_TESTNET})
    @interface KeyhashType {
    }

    final Transaction.Script.WitnessProgram witnessProgram;
    @KeyhashType
    final int keyhashType;
    final byte[] hash160;
    private final boolean testNet;

    Address(String address) throws BitcoinException {
        if (address == null) {
            throw new BitcoinException(BitcoinException.ERR_BAD_FORMAT, "Null address");
        }
        if (address.length() > 3) {
            String prefix = address.substring(0, 2).toLowerCase(Locale.ENGLISH);
            if (prefix.equals("bc") || prefix.equals("tc")) {
                testNet = prefix.equals("tc");
                witnessProgram = Bech32.decodeSegwitAddress(prefix, address);
                hash160 = null;
                keyhashType = TYPE_NONE;
                return;
            }
        }
        byte[] decodedAddress = decodeBase58(address);
        if (decodedAddress == null || decodedAddress.length < 6) {
            throw new BitcoinException(BitcoinException.ERR_BAD_FORMAT, "Bad address");
        }
        keyhashType = decodedAddress[0] & 0xff;
        if (keyhashType == TYPE_MAINNET || keyhashType == TYPE_TESTNET || keyhashType == TYPE_P2SH || keyhashType == TYPE_P2SH_TESTNET) {
            if (BTCUtils.verifyDoubleSha256Checksum(decodedAddress)) {
                witnessProgram = null;
                hash160 = new byte[20];
                System.arraycopy(decodedAddress, 1, hash160, 0, hash160.length);
                testNet = keyhashType == TYPE_TESTNET || keyhashType == TYPE_P2SH_TESTNET;
            } else {
                throw new BitcoinException(BitcoinException.ERR_BAD_FORMAT, "Bad address");
            }
        } else {
            throw new BitcoinException(BitcoinException.ERR_WRONG_TYPE, "Unsupported address type " + (decodedAddress[0] & 0xff));
        }
    }

    Address(boolean testNet, Transaction.Script.WitnessProgram witnessProgram) {
        this.witnessProgram = witnessProgram;
        keyhashType = TYPE_NONE;
        hash160 = null;
        this.testNet = testNet;
    }

    @Override
    public String toString() {
        if (keyhashType != TYPE_NONE) {
            return ripemd160HashToAddress((byte) keyhashType, hash160);
        }
        try {
            return Bech32.encodeSegwitAddress(testNet ? "bc" : "tc", witnessProgram.version, witnessProgram.program);
        } catch (BitcoinException e) {
            return null;
        }
    }

    private static Address decode(String address) {
        try {
            return new Address(address);
        } catch (BitcoinException ignored) {
            return null;
        }
    }

    public static boolean verify(String address) {
        return decode(address) != null;
    }

    static String publicKeyToAddress(byte[] publicKey) {
        return publicKeyToAddress(false, publicKey);
    }

    static String publicKeyToAddress(boolean testNet, byte[] publicKey) {
        return ripemd160HashToAddress(testNet, BTCUtils.sha256ripemd160(publicKey));
    }

    static String publicKeyToP2wkhAddress(boolean testNet, byte[] publicKey) {
        if (publicKey.length > 33) {
            return null; //key should be compressed
        }
        try {
            return Bech32.encodeSegwitAddress(testNet ? "bc" : "tc", 0, BTCUtils.sha256ripemd160(publicKey));
        } catch (BitcoinException unexpected) {
            throw new RuntimeException(unexpected);
        }
    }

    static String publicKeyToP2shP2wkhAddress(boolean testNet, byte[] publicKey) {
        if (publicKey.length > 33) {
            return null; //key should be compressed
        }
        return ripemd160HashToP2shAddress(testNet, BTCUtils.sha256ripemd160(new Transaction.Script.WitnessProgram(0, BTCUtils.sha256ripemd160(publicKey)).getBytes()));
    }

    static String ripemd160HashToAddress(boolean testNet, byte[] hashedPublicKey) {
        byte version = (byte) (testNet ? TYPE_TESTNET : TYPE_MAINNET);
        return ripemd160HashToAddress(version, hashedPublicKey);
    }

    static String ripemd160HashToP2shAddress(boolean testNet, byte[] hashedPublicKey) {
        byte version = (byte) (testNet ? TYPE_P2SH_TESTNET : TYPE_P2SH);
        return ripemd160HashToAddress(version, hashedPublicKey);
    }

    private static String ripemd160HashToAddress(byte version, byte[] hashedPublicKey) {
        try {
            //4 - Add version byte in front of RIPEMD-160 hash (0x00 for Main Network)
            byte[] addressBytes = new byte[1 + hashedPublicKey.length + 4];
            addressBytes[0] = version;
            System.arraycopy(hashedPublicKey, 0, addressBytes, 1, hashedPublicKey.length);
            //5 - Perform SHA-256 hash on the extended RIPEMD-160 result
            //6 - Perform SHA-256 hash on the result of the previous SHA-256 hash
            MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
            digestSha.update(addressBytes, 0, addressBytes.length - 4);
            byte[] check = digestSha.digest(digestSha.digest());
            //7 - Take the first 4 bytes of the second SHA-256 hash. This is the address checksum
            //8 - Add the 4 checksum bytes from point 7 at the end of extended RIPEMD-160 hash from point 4. This is the 25-byte binary Bitcoin Address.
            System.arraycopy(check, 0, addressBytes, hashedPublicKey.length + 1, 4);
            return BTCUtils.encodeBase58(addressBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
