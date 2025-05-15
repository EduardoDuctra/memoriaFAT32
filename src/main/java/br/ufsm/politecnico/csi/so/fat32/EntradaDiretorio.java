package br.ufsm.politecnico.csi.so.fat32;

public class EntradaDiretorio {
    private String fileName;
    private int fileSize;
    private int starterBlock;

    //cada arquivo tem um nome, tamanho e bloco inicial
    public EntradaDiretorio(String fileName, int fileSize, int starterBlock) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.starterBlock = starterBlock;
    }

    public EntradaDiretorio() {

    }


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public int getStarterBlock() {
        return starterBlock;
    }

}
