package in.kevinj.bloomberghelper.common;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class HashFunctions {
	public static final Charset ASCII = Charset.forName("US-ASCII");

	private static ThreadLocal<MessageDigest> sha512digest = new ThreadLocal<MessageDigest>() {
		@Override
		public MessageDigest initialValue() {
			try {
				return MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				return null;
			}
		}
	};

	private static byte[] hashWithDigest(byte[] in, MessageDigest digester) {
		digester.update(in, 0, in.length);
		return digester.digest();
	}

	private static byte[] sha512(byte[] in) {
		return hashWithDigest(in, sha512digest.get());
	}

	public static byte[] sha512(String in) {
		return sha512(in.getBytes(ASCII));
	}

	public static boolean checkSha512Hash(byte[] actualHash, String check) {
		return Arrays.equals(actualHash, sha512(check.getBytes(ASCII)));
	}
}
