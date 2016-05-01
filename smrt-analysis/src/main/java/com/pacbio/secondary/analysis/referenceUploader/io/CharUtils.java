/**
 * 
 */
package com.pacbio.secondary.analysis.referenceUploader.io;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for character operations. The character set is UTF8.
 * 
 * @author jmiller
 */
public class CharUtils {

	protected static final byte NOT_IUPAC = 33;
	protected static final byte HEADER_START = 62;
	protected static final byte CR = 13;
	protected static final byte LF = 10;
	protected static final byte TAB = 9;
	protected static final byte DOT = 46;
//	protected static final byte DASH = 45;
	protected static final byte PIPE = 124;
	protected static final byte COLON = 58;
	protected static final byte DOUBLE_QUOTE = 34;
	
	protected static final byte g = 103;
	protected static final byte G = 71;
	protected static final byte a = 97;
	protected static final byte A = 65;
	protected static final byte t = 116;
	protected static final byte T = 84;
	protected static final byte c = 99;
	protected static final byte C = 67;
	protected static final byte u = 117;
	protected static final byte U = 85;
	protected static final byte r = 114;
	protected static final byte R = 82;
	protected static final byte y = 121;
	protected static final byte Y = 89;
	protected static final byte s = 115;
	protected static final byte S = 83;
	protected static final byte w = 119;
	protected static final byte W = 87;
	protected static final byte k = 107;
	protected static final byte K = 75;
	protected static final byte m = 109;
	protected static final byte M = 77;
	protected static final byte b = 98;
	protected static final byte B = 66;
	protected static final byte d = 100;
	protected static final byte D = 68;
	protected static final byte h = 104;
	protected static final byte H = 72;
	protected static final byte v = 118;
	protected static final byte V = 86;
	protected static final byte n = 110;
	protected static final byte N = 78;
	
	//byte order mark
	public static final int BOM = 65279;
	
	
	
	private static Set<Byte> supportedSeqBytes;
	
	static {
		supportedSeqBytes = new HashSet<Byte>();
		supportedSeqBytes.add(CR);
		
		supportedSeqBytes.add(LF);
		supportedSeqBytes.add(TAB);
		supportedSeqBytes.add(DOT);
		supportedSeqBytes.add(PIPE);

		supportedSeqBytes.add(g);
		supportedSeqBytes.add(G);
		supportedSeqBytes.add(a);
		supportedSeqBytes.add(A);
		supportedSeqBytes.add(t);
		supportedSeqBytes.add(T);
		supportedSeqBytes.add(c);
		supportedSeqBytes.add(C);
		supportedSeqBytes.add(u);
		supportedSeqBytes.add(U);
		supportedSeqBytes.add(r);
		supportedSeqBytes.add(R);
		supportedSeqBytes.add(y);
		supportedSeqBytes.add(Y);
		supportedSeqBytes.add(s);
		supportedSeqBytes.add(S);
		supportedSeqBytes.add(w);
		supportedSeqBytes.add(W);
		supportedSeqBytes.add(k);
		supportedSeqBytes.add(K);
		supportedSeqBytes.add(m);
		supportedSeqBytes.add(M);
		supportedSeqBytes.add(b);
		supportedSeqBytes.add(B);
		supportedSeqBytes.add(d);
		supportedSeqBytes.add(D);
		supportedSeqBytes.add(h);
		supportedSeqBytes.add(H);
		supportedSeqBytes.add(v);
		supportedSeqBytes.add(V);
		supportedSeqBytes.add(n);
		supportedSeqBytes.add(N);
	}
	
	

	/**
	 * @param currentByte
	 * @return true if currentByte == 62 == '.'
	 */
	public static boolean isHeaderStart(byte b) {
		return b == HEADER_START;
	}

	/**
	 * @param currentByte
	 * @return String representation of byte
	 */
	public static String getChar(byte b) {
		return new String(new byte[] { b });
	}

	/**
	 * Return a byte that could be encountered when streaming through a contig.
	 * This includes a header start symbol, since, in a multi-seq source file,
	 * you may run into one at any time.
	 * 
	 * Other fasta symbols, such as a pipe, should not be encountered within contig, so this methid 
	 * 
	 * <pre>
	 * For example, 'a' returns 'A'.
	 * 'A' returns 'A'. 
	 * 'z' returns NOT_IUPAC.
	 * '>' return the correct byte.
	 * </pre>
	 * 
	 * @param b
	 *            byte from within a contig of a source fasta file
	 * @return b byte that gets written to a contig in the multiSeq fasta file, if it's a legal sequence symbol. Else returns {@value #NOT_IUPAC}
	 */
	public static byte getContigIupacByte(byte b) {
		if (isHeaderStart(b))
			return HEADER_START;
		
		if( supportedSeqBytes.contains(b) ) {
			return b;
		}
		return NOT_IUPAC;
	}

}
