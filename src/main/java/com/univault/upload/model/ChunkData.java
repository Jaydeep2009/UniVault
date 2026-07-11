package com.univault.upload.model;
import lombok.*;
@Getter
public class ChunkData {
    private final int serialNumber;
    private final byte[] data;
    private final String checkSum;
    private final long size;

    public ChunkData(int serialNumber, byte[] data, String checkSum) {
        this.serialNumber = serialNumber;
        this.data = data;
        this.checkSum = checkSum;
        this.size = data.length;
    }
}


