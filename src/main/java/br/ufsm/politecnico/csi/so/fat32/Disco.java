package br.ufsm.politecnico.csi.so.fat32;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Disco {

    public static final int TAM_BLOCO = 64 * 1024; //cada bloco tem 64 KB
    public static final int NUM_BLOCO = 1024; //1024 blocos disponiveis
    private RandomAccessFile raf; //classe que permite ler e escrever arquivos de forma nao sequancial

    public Disco() {}


//Objetivo da função: criar um arquivo tipo File
//se nao existe cria um arquivo File com o tamanho NUM_BLOCO * TAM_BLOCO
//raf = new RandomAccessFile(f, "rws"); isso da acesso de escrita, leitura e atualizar

    public boolean init() throws IOException {
        File f = new File("virtual_disk.fs");  // Arquivo específico para o sistema FAT32
        boolean exists = f.exists();
        raf = new RandomAccessFile(f, "rws");

        if(!exists) {
            // Inicializa um disco vazio
            raf.setLength(NUM_BLOCO * TAM_BLOCO);
            // Inicializa FAT e diretório vazios
            byte[] emptyBlock = new byte[TAM_BLOCO];
            raf.write(emptyBlock);  // Bloco 0 (diretório)
            raf.write(emptyBlock);  // Bloco 1 (FAT)
        }
        return exists;
    }

    // Objetivo da função: leitura
// Faz verificação se o tamanho do numBloco é válido
    public byte[] read(int numBloco) throws IOException {
        if (numBloco < 0 || numBloco >= NUM_BLOCO) {
            throw new IllegalArgumentException("Número de bloco inválido");
        }

        raf.seek(numBloco * TAM_BLOCO); // posiciona o cursor no começo do bloco e depois faz a leitura
        byte[] read = new byte[TAM_BLOCO];  // Cria um array de bytes com o tamanho de um bloco

//        O método raf.read(byte[], offset, length) tenta ler até length bytes, mas não é garantido que ele consiga tudo em uma única chamada.
        int totalLido = 0; //quantos bytes foram lidos
        while (totalLido < TAM_BLOCO) { //le todos os bytes do bloco
            int bytesLidos = raf.read(read, totalLido, TAM_BLOCO - totalLido);
            if (bytesLidos == -1) break; // fim de arquivo
            totalLido += bytesLidos;
        }

        return read;
    }

    //Objetivo da função: escrita em um arquivo especifico
    //    Faz verificação se o tamanho do numBloco é valido
    public void write(int numBloco, byte[] data) throws IOException {
        if(numBloco >= 0 && numBloco < NUM_BLOCO){ //Verifica se numBloco está dentro do intervalo válido
            if (data != null && data.length <= TAM_BLOCO) { //Verifica se o array data não é null e se o tamanho está dentro do limite (<= TAM_BLOCO
                this.raf.seek((long) numBloco * TAM_BLOCO); //Usa seek para posicionar no início do bloco correto. Converte para long para evitar estouro de int
                this.raf.write(data); //Usa write(data) para escrever os dados.
            } else {
                throw new IllegalArgumentException("Dados inválidos.");
            }
        } else {
            throw new IllegalArgumentException("Número de bloco inválido.");
        }
    }

    //serve para calcular quantos blocos você precisa para armazenar um arquivo
    public int calcularBlocosNecessarios(int tamanhoArquivo) {
        return (tamanhoArquivo + TAM_BLOCO - 1) / TAM_BLOCO;
        //Arredondar pra cima, garantindo blocos suficientes.
        // Se tiver qualquer "restinho", ele força o arredondamento pra cima.
    }
}
