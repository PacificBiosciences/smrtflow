package com.pacbio.secondary.common.util.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/**
 * A set of cryptographic utilities for use in simple PBE operations.  
 * Note: these methods do not provide a very high level of security
 * because they rely on a default PBE parameter spec.
 * In many areas this class favors convenience over optimal security.
 *
 * Example of encrypting and decrypting a String using a given password:
 * <pre>
 *    char password = new char[] {'c','h','0','2','@','Q','k'};
 *    byte[] encryptedText = Crypto.encrypt("Text", password);
 *    byte[] clearText = Crypto.decrypt(encryptedText, password);
 * </pre>
 */
public class Crypto {
	private final static String ALGORITHM = "PBEWithMD5AndDES";

	/**
	 * A default PBE parameter spec with default salt and iteration parameters.
	 */
	private static final PBEParameterSpec PARAMETER_SPEC =
			new PBEParameterSpec(
				new byte[] {
					(byte)0xee, (byte)0x22, (byte)0xf1, (byte)0xf1,
					(byte)0xe7, (byte)0xe2, (byte)0xc7, (byte)0x1c},
				15);

	
	private static char[] passkey;

	/**
	 * Decrypt a byte array using a password.
	 * 
	 * @param value encrypted value
	 * @return decrypted value
	 */
	public static byte[] decrypt(byte[] value, char[] password) 
	throws GeneralSecurityException {
		Cipher pbeCipher = Cipher.getInstance(ALGORITHM);
		pbeCipher.init(Cipher.DECRYPT_MODE, 
				SecretKeyFactory.getInstance(ALGORITHM)
					.generateSecret(new PBEKeySpec(password)), 
				PARAMETER_SPEC);		
		return pbeCipher.doFinal(value);		
	}
	
	/**
	 * Encrypt a byte array using a password.	 
	 * 
	 * @param text
	 * @return encrypted value
	 */
	public static byte[] encrypt(byte[] text, char[] password) 
	throws GeneralSecurityException {	
		Cipher pbeCipher = Cipher.getInstance(ALGORITHM);
		pbeCipher.init(Cipher.ENCRYPT_MODE, 
				SecretKeyFactory.getInstance(ALGORITHM)
					.generateSecret(new PBEKeySpec(password)), 
				PARAMETER_SPEC);		
		return pbeCipher.doFinal(text);		
	}

	/**
	 * @return the passkey defined in props file
	 * @throws IOException 
	 */
	public static char[] getPasskey() throws IOException {
		if( passkey == null ) {
			passkey = createPasskey();
		}
		return passkey;
	}

	private static char[] createPasskey() throws IOException {
		Properties props = new Properties();
		InputStream in = null;
		
		try {
			in = Crypto.class.getClassLoader().getResourceAsStream("encryption.properties");
			props.load( in );
			return props.getProperty("secretKeyPassword" ).toCharArray();
		} finally {
			in.close();
		}
	}
}
