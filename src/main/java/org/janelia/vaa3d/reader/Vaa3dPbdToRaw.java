package org.janelia.vaa3d.reader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Convert a compressed v3dpbd file to an uncompressed v3draw file
 * @author brunsc
 *
 */
public class Vaa3dPbdToRaw {

    /**
     * @param args
     */
    public static void main(String[] args) {
        String fileNameIn = args[0];
        String fileNameOut = args[1];
        try {
            FileInputStream in = new FileInputStream(fileNameIn);
            FileOutputStream out = new FileOutputStream(fileNameOut);
            
            V3dRawImageStream v3d = new V3dRawImageStream(in);
            v3d.writeHeader(out, V3dRawImageStream.Format.FORMAT_PENG_RAW);
            int sliceCount = v3d.getDimension(2) * v3d.getDimension(3); // z * c
            for (int s = 0; s < sliceCount; ++s) {
                v3d.loadNextSlice();
                ByteBuffer bb = v3d.getCurrentSlice().getByteBuffer();
                out.write(bb.array(), 0, bb.capacity());
            }
            
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
