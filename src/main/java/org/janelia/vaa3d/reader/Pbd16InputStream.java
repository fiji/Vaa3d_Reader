/*
Copyright (c) 2012, Christopher M. Bruns and Howard Hughes Medical Institute
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.janelia.vaa3d.reader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;


/**
 * Decompresses a binary InputStream using Sean Murphy's fast PBD 
 * pack-bits plus difference encoding.
 * 
 * Overrides the FilterInputStream.read method.
 * Adapted from ImageLoader.cpp in Vaa3d project.
 * Used by V3dRawImageStream class.
 * 
 * @author Christopher M. Bruns
 *
 */
public class Pbd16InputStream extends PbdInputStream 
{
	private ByteOrder byteOrder= ByteOrder.BIG_ENDIAN;
	
	private byte[] nibbleBytes = new byte[2];
	private ByteBuffer nibbleByteBuffer = ByteBuffer.wrap(nibbleBytes);
	private ShortBuffer nibbleShortBuffer;
	
    private byte[] singleShortBytes = new byte[2];
    private ByteBuffer singleShortByteBuffer = ByteBuffer.wrap(singleShortBytes);
    private ShortBuffer singleShortShortBuffer;
    
	private short repeatValue;
	private short decompressionPrior;
	private byte d0,d1,d2,d3;
	// private byte sourceChar;
	private byte carryOver;
	private byte oooooool = 1;
	// private byte oooooolo = 2;
	private byte ooooooll = 3;
	// private byte oooooloo = 4;
	// private byte ooooolol = 5;
	// private byte ooooollo = 6;
	private byte ooooolll = 7;

	// differenceCache is used in case read method ends mid-difference run.
	private Deque<Short> shortValueCache = new ArrayDeque<Short>();
	
	// TODO Support reading one byte at a time, by cacheing half-shorts
	private boolean haveCachedNibble = false;
	
	public Pbd16InputStream(InputStream in, ByteOrder byteOrder) {
		super(in);
		this.byteOrder = byteOrder;
		nibbleByteBuffer.order(byteOrder);
		nibbleShortBuffer = nibbleByteBuffer.asShortBuffer();
		
        singleShortByteBuffer.order(byteOrder);
        singleShortShortBuffer = singleShortByteBuffer.asShortBuffer();
	}

	// for debugging
	/*
	private short checkValue(short v) {
		if (v < 0) {
			// System.out.println("fizz");
		}
		if (v > 5000) {
			// System.out.println("buzz");			
		}
		return v;
	}
	*/
	
	@Override
	public int read(byte[] b, int off0, int len0) 
	throws IOException
	{
		if (len0 < 1) return 0;
		
		int bytesRead = 0;
		
		// Second half-short
		// Is there a leftover byte (half-short) from last time?
		int off, len;
		if (haveCachedNibble) {
		    // Write one byte to output
		    b[off0] = nibbleBytes[1];
		    off = off0 + 1;
		    len = len0 - 1;
		    haveCachedNibble = false;
		    bytesRead += 1;
		    if (len == 0)
		        return bytesRead;
		}
		else {
            off = off0;
            len = len0;		    
		}
		
		ByteBuffer byteOut = ByteBuffer.wrap(b, off, len);
		byteOut.order(this.byteOrder);
		ShortBuffer out = byteOut.asShortBuffer();
		
        int shortValue = getNextShort();
        
        if ( (shortValue < 0) && (bytesRead == 0) )
            return shortValue; // -1 means read failed
        
        // Special case for read of length 1
        if ( !out.hasRemaining() ) {
            if ( cacheNibble(shortValue) ) {
                b[off0 + len0 - 1] = nibbleBytes[0];
                bytesRead += 1;
            }
        }
        
        while ( out.hasRemaining() && (shortValue >= 0) )
        {
            out.put((short)shortValue);
            bytesRead += 2;
            if (out.hasRemaining())
                shortValue = getNextShort();
        }
        
        // First half-short
        // Sometimes we might have room for just one more byte,
        // the first byte of a two-byte short
        if ((len0 - bytesRead) == 1) {
            shortValue = getNextShort();
            if ( cacheNibble(shortValue) ) {
                b[off0 + len0 - 1] = nibbleBytes[0];
                bytesRead += 1;
            }
        }
		
		return bytesRead;
	}
	
	/** Save one short data value that must be divided between two read calls
	 * 
	 * @param value
	 * @return number of bytes read
	 */
	private boolean cacheNibble(int value) {
	    if (value < 0) {
	        haveCachedNibble = false;
	        return false; // don't cache invalid values
	    }
        nibbleShortBuffer.rewind();
        nibbleShortBuffer.put((short)value);
        // b[off0 + len0 - 1] = nibbleBytes[0];
        haveCachedNibble = true;
	    return true;
	}
	
	/**
	 * 
	 * @return -1 means no more data
	 * @throws IOException 
	 */
	private int getNextShort() throws IOException {
	    while (true) {            
            if (! shortValueCache.isEmpty() )
                return shortValueCache.pollFirst();
	        
            if (state == State.STATE_BEGIN)
            {
                // Read one byte
                int code = in.read(); // unsigned
                if (code < 0)  // read failed, end of stream?
                    return code;
                assert(code >= 0);
                if (code < 32) { // literal 0-31
                    state = State.STATE_LITERAL;
                    leftToFill = code + 1;
                }
                else if (code < 80) { // Difference 3-bit 32-79
                    state = State.STATE_DIFFERENCE;
                    leftToFill = code - 31;
                }
                else if (code < 223) { // Repeat 223-255
                    throw new IOException("Received unimplemented code of " + code);
                }
                else { // Repeat 223-255
                    state = State.STATE_REPEAT;
                    leftToFill = code - 222;
                    in.read(singleShortBytes, 0, 2);
                    repeatValue = singleShortShortBuffer.get(0);
                }
            }
            else if (state == State.STATE_LITERAL)
            {
                for (int s = 0; s < leftToFill; ++s) {
                    in.read(singleShortBytes, 0, 2);
                    short value = singleShortShortBuffer.get(0);
                    shortValueCache.add(value);
                }
                state = State.STATE_BEGIN;
                leftToFill = 1;
                decompressionPrior = shortValueCache.peekLast();
            }
            else if (state == State.STATE_DIFFERENCE)
            {
                while (leftToFill > 0)
                {
                    //
                    // 332
                    d0=d1=d2=d3=0;
                    int sourceChar2 = in.read();
                    // sourceChar=(byte)sourceChar2;
                    d0 = (byte)(sourceChar2 >>> 5);
                    short value = (short)(decompressionPrior+(d0<5?d0:4-d0));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1] << " d0=" << d0;
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d1 = (byte)((sourceChar2 >>> 2) & ooooolll);
                    value = (short)(value + (d1<5?d1:4-d1));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d2 = (byte)((sourceChar2) & ooooooll);
                    carryOver=d2;
    
                    // 1331
                    d0=d1=d2=d3=0;
                    sourceChar2 = in.read();
                    // sourceChar=(byte)sourceChar2;
                    carryOver <<= 1;
                    d0 = (byte)((sourceChar2 >>> 7) | carryOver);
                    value = (short)(value + (d0<5?d0:4-d0));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d1 = (byte)((sourceChar2 >>> 4) & ooooolll);
                    value = (short)(value + (d1<5?d1:4-d1));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d2 = (byte)((sourceChar2 >>> 1) & ooooolll);
                    value = (short)(value + (d2<5?d2:4-d2));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d3 = (byte)((sourceChar2) & oooooool);
                    carryOver=d3;
    
                    // 233
                    d0=d1=d2=d3=0;
                    sourceChar2 = in.read();
                    // sourceChar=(byte)sourceChar2;
                    carryOver <<= 2;
                    d0 = (byte)((sourceChar2 >>> 6) | carryOver);
                    value = (short)(value + (d0<5?d0:4-d0));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d1 = (byte)((sourceChar2 >>> 3) & ooooolll);
                    value = (short)(value + (d1<5?d1:4-d1));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    d2 = (byte)((sourceChar2) & ooooolll);
                    value = (short)(value + (d2<5?d2:4-d2));
                    shortValueCache.add(value); // out.put(value);
                    // out.put(checkValue(value));
                    //if (debug) qDebug() << "debug: position " << (dp-1) << " diff value=" << target16Data[dp-1];
                    leftToFill--;
                    if (leftToFill==0) {
                        break;
                    }
                    // Does this statement ever get executed?
                    decompressionPrior = shortValueCache.peekLast();
                }
                if (! shortValueCache.isEmpty())
                    decompressionPrior = shortValueCache.peekLast();
                if (leftToFill < 1)
                    state = State.STATE_BEGIN;
            }
            else if (state == State.STATE_REPEAT)
            {
                for (int j = 0; j < leftToFill; ++j)
                    shortValueCache.add(repeatValue);
                leftToFill = 0;
                state = State.STATE_BEGIN;
                decompressionPrior = repeatValue;
            }
            else {
                throw new IOException("Unexpected state");
            }
	    }
	}
}
