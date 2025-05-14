package br.ufsm.politecnico.csi.so.fat32;

import java.io.IOException;

public interface FileSystem {


    void create(String fileName, byte[] data) throws IOException;

    void append(String fileName, byte[] data) throws IOException;

    byte[] read(String fileName, int offset, int limit) throws IOException;

    void remove(String fileName) throws IOException;

    int freeSpace();

}
