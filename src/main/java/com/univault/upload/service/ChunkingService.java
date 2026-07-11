package com.univault.upload.service;

import com.univault.common.util.ChecksumUtil;
import com.univault.upload.model.ChunkData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {
    @Value("${univault.chunk.size.bytes:4194304}") // Default to 4 MB if not set
    private int chunkSizeBytes;

    /*
     * Reads an InputStream and splits it into fixed-size chunks,
     * computing an MD5 checksum for each chunk.
     * Does NOT load the whole file into memory at once —
     * only one chunk buffer at a time.
     */
    public List<ChunkData> splitIntoChunks(InputStream inputStream){
        List<ChunkData> chunks = new ArrayList<>();
        byte[] buffer = new byte[chunkSizeBytes];
        int serialNumber = 0;

        try{
            int bytesRead;
            while((bytesRead = readFully(inputStream,buffer)) >0){
                //Trim buffer if this is the last, smaller chunk
                byte[] chunkBytes= (bytesRead==chunkSizeBytes)
                        ? buffer.clone()
                        : trimToSize(buffer,bytesRead);

                String checksum = ChecksumUtil.computeMd5(chunkBytes);
                chunks.add(new ChunkData(serialNumber, chunkBytes, checksum));
                serialNumber++;
            }
        }catch (IOException e) {
            throw new RuntimeException("Failed to read input stream for chunking", e);
        }

        if(chunks.isEmpty()) {
            throw new IllegalArgumentException("Input stream produced no data — empty file?");
        }
        return chunks;
    }

    /*
     * InputStream.read() doesn't guarantee filling the buffer in one call —
     * this loops until the buffer is full or the stream ends.
     */
    private int readFully(InputStream inputStream, byte[] buffer) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < buffer.length) {
            int bytesRead = inputStream.read(buffer, totalBytesRead, buffer.length - totalBytesRead);
            if (bytesRead == -1) {
                break; // End of stream
            }
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    private byte[] trimToSize(byte[] buffer, int size) {
        byte[] trimmed = new byte[size];
        System.arraycopy(buffer, 0, trimmed, 0, size);
        return trimmed;
    }


}
