package org.janelia.vaa3d.reader;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class TestVaa3d_Reader {

    @Test
    public void testPositiveControl() {
        // Does unit testing work at all?
        assertTrue(true);
    }
    
    @Test
    public void testInstantiateStream() {
        // Simple sanity check that I can read a test file from resources folder
        InputStream testStream = ClassLoader.class.getResourceAsStream("/test_strip8.v3draw");
        long byteCount = countStreamBytes(testStream);
        assertEquals(59, byteCount);
    }
    
    @Test
    public void testVaa3dRawDataStreamSize() {
        assertEquals(16, countResourceDataBytes("/test_strip8.v3draw")); // 16 data bytes after 43 byte header was loaded in constructor

    }

    @Test
    public void testVaa3dPbdDataStreamSize() {
        assertEquals(16, countResourceDataBytes("/test_strip8.v3dpbd")); // 16 data bytes after 43 byte header was loaded in constructor
    }

    @Test
    public void testVaa3dPbdRawEqualSizes() {
        assertEquals(countResourceDataBytes("/test_strip8.v3dpbd"), countResourceDataBytes("/test_strip8.v3draw"));
    }
    
    @Test
    public void testVaa3dPbdRawSameDataRead1() {
        InputStream raw = new V3dRawImageStream(ClassLoader.class.getResourceAsStream("/test_strip8.v3draw")).getDataInputStream();
        InputStream pbd = new V3dRawImageStream(ClassLoader.class.getResourceAsStream("/test_strip8.v3dpbd")).getDataInputStream();
        int rawData = 0;
        int pbdData = 0;
        while (rawData >= 0) {
            try {
                assertEquals(rawData, pbdData);
                rawData = raw.read();
                pbdData = pbd.read();
            } catch (IOException e) {}
        }
    }
    
    @Test
    public void testVaa3dPbdRawSameDataRead1024() {
        final int bufSize = 1024;
        byte[] buffer1 = new byte[bufSize];
        byte[] buffer2 = new byte[bufSize];
        InputStream raw = new V3dRawImageStream(ClassLoader.class.getResourceAsStream("/test_strip8.v3draw")).getDataInputStream();
        InputStream pbd = new V3dRawImageStream(ClassLoader.class.getResourceAsStream("/test_strip8.v3dpbd")).getDataInputStream();
        try {
            int bytesRead1, bytesRead2;
            do {
                bytesRead1 = raw.read(buffer1, 0, bufSize);
                bytesRead2 = pbd.read(buffer2, 0, bufSize);
                assertEquals(bytesRead1, bytesRead2);
                for (int b = 0; b < bytesRead1; ++b) {
                    assertEquals(buffer1[b], buffer2[b]);
                }
            } while (bytesRead1 > 0);
        } catch (IOException e) {}
    }
    
    private static long countResourceDataBytes(String resourceName) {
        InputStream is = new V3dRawImageStream(ClassLoader.class.getResourceAsStream(resourceName)).getDataInputStream();
        return countStreamBytes(is);
    }
    
    private static long countStreamBytes(InputStream stream) {
        byte[] buffer= new byte[1024];
        long byteCount = 0;
        try {
            int readCount = stream.read(buffer);
            while (readCount > 0) {
                byteCount += readCount;
                readCount = stream.read(buffer);
            }
        } catch (IOException e) {}
        return byteCount;
    }
    
}
