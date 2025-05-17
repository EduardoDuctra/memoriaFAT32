package br.ufsm.politecnico.csi.so.fat32;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Disco disco = new Disco();
        try {
            disco.init();
            Fat32 fat32 = new Fat32(disco);

            String caminhoLocal = new File(System.getProperty("user.dir"), "src/main/resources/arquivos/trabalho.pdf").getPath();
            String caminhoLocal_2 = new File(System.getProperty("user.dir"), "src/main/resources/arquivos/Algoritmos_Prova01.txt").getPath();

            carregarArquivo(fat32, caminhoLocal, "PDFimport");
            carregarArquivo(fat32, caminhoLocal_2, "TXTimport");

            menu(fat32);
        } catch (IOException e) {
            System.err.println("Erro ao inicializar: " + e.getMessage());
        }
    }

    public static void menu(Fat32 fat32) throws IOException {
        Scanner sc = new Scanner(System.in);
        int opcao;

        do {
            System.out.println("\n=== Sistema de Arquivos FAT32 ===");
            System.out.println("1. Criar arquivo");
            System.out.println("2. Listar arquivos");
            System.out.println("3. Ler arquivo");
            System.out.println("4. Adicionar conteúdo");
            System.out.println("5. Excluir arquivo");
            System.out.println("6. Mostrar uso da memória");
            System.out.println("7. Exportar arquivo");
            System.out.println("8. Sair");
            System.out.print("Opção: ");

            opcao = sc.nextInt();
            sc.nextLine(); // Limpa buffer

            switch (opcao) {
                case 1 -> criarArquivo(fat32, sc);
                case 2 -> listarArquivos(fat32);
                case 3 -> lerArquivo(fat32, sc);
                case 4 -> adicionarConteudo(fat32, sc);
                case 5 -> excluirArquivo(fat32, sc);
                case 6 -> mostrarUsoMemoria(fat32);
                case 7 -> exportarArquivo(fat32, sc);
                case 8 -> System.out.println("Encerrando sistema...");
                default -> System.out.println("Opção inválida!");
            }
        } while (opcao != 8);

        sc.close();
    }

    private static void criarArquivo(Fat32 fat32, Scanner sc) throws IOException {
        System.out.println("\n--- Criar Arquivo ---");
        System.out.print("Nome do arquivo (max 8 caracteres): ");
        String nome = sc.nextLine();

        System.out.print("Extensão (max 3 caracteres): ");
        String ext = sc.nextLine();

        String nomeCompleto = nome.trim() + "." + ext.trim();
        System.out.print("Conteúdo: ");
        String conteudo = sc.nextLine();

        fat32.create(nomeCompleto, conteudo.getBytes());
        System.out.println("Arquivo criado com sucesso!");
    }

    private static void listarArquivos(Fat32 fat32) throws IOException {
        System.out.println("\n--- Arquivos Disponíveis ---");
        List<String> arquivos = fat32.listarArquivos();

        if (arquivos.isEmpty()) {
            System.out.println("Nenhum arquivo encontrado.");
            return;
        }

        for (int i = 0; i < arquivos.size(); i++) {
            System.out.println((i + 1) + ". " + arquivos.get(i));
        }
    }

    private static int selecionarArquivo(Fat32 fat32, Scanner sc, String acao) throws IOException {
        List<String> arquivos = fat32.listarArquivos();

        if (arquivos.isEmpty()) {
            System.out.println("Nenhum arquivo para " + acao + ".");
            return -1;
        }

        System.out.print("\nDigite o número do arquivo para " + acao + ": ");
        int numero = sc.nextInt();
        sc.nextLine();

        if (numero < 1 || numero > arquivos.size()) {
            System.out.println("Número inválido!");
            return -1;
        }

        return numero - 1;
    }

    private static void lerArquivo(Fat32 fat32, Scanner sc) throws IOException {
        System.out.println("\n--- Ler Arquivo ---");
        int indice = selecionarArquivo(fat32, sc, "leitura");

        if (indice >= 0) {
            String nomeArquivo = fat32.listarArquivos().get(indice);
            byte[] conteudo = fat32.read(nomeArquivo, 0, -1);
            System.out.println("\nConteúdo:\n" + new String(conteudo));
        }
    }

    private static void adicionarConteudo(Fat32 fat32, Scanner sc) throws IOException {
        System.out.println("\n--- Adicionar Conteúdo ---");
        int indice = selecionarArquivo(fat32, sc, "adição");

        if (indice >= 0) {
            String nomeArquivo = fat32.listarArquivos().get(indice);
            System.out.print("Digite o conteúdo a adicionar: ");
            String conteudo = sc.nextLine();

            fat32.append(nomeArquivo, conteudo.getBytes());
            System.out.println("Conteúdo adicionado com sucesso!");
        }
    }

    private static void excluirArquivo(Fat32 fat32, Scanner sc) throws IOException {
        System.out.println("\n--- Excluir Arquivo ---");
        int indice = selecionarArquivo(fat32, sc, "exclusão");

        if (indice >= 0) {
            String nomeArquivo = fat32.listarArquivos().get(indice);
            System.out.print("Confirmar exclusão de " + nomeArquivo + "? (S/N): ");
            String confirmacao = sc.nextLine();

            if (confirmacao.equalsIgnoreCase("S")) {
                fat32.remove(nomeArquivo);
                System.out.println("Arquivo excluído com sucesso!");
            } else {
                System.out.println("Operação cancelada.");
            }
        }
    }

    private static void carregarArquivo(Fat32 fat32, String caminhoLocal, String nomeFAT32) throws IOException {
        System.out.println("\n--- Carregar Arquivo para FAT32 ---");

        File arquivoLocal = new File(caminhoLocal);

        if (!arquivoLocal.exists()) {
            System.out.println("Arquivo não encontrado.");
            return;
        }

        try {
            byte[] conteudo = Files.readAllBytes(arquivoLocal.toPath());
            fat32.create(nomeFAT32, conteudo);
            System.out.println("Arquivo carregado para o sistema FAT32 com sucesso!");
        } catch (IOException e) {
            System.out.println("Caminho inválido ou nome do arquivo já existente");
        }
    }

    private static void mostrarUsoMemoria(Fat32 fat32) throws IOException {
        int totalBlocos = Disco.NUM_BLOCO - 2;
        int blocosOcupados = fat32.contarBlocosOcupados();
        int blocosLivres = totalBlocos - blocosOcupados;

        int totalKB = totalBlocos * (Disco.TAM_BLOCO / 1024);
        int usadoKB = blocosOcupados * (Disco.TAM_BLOCO / 1024);
        int livreKB = blocosLivres * (Disco.TAM_BLOCO / 1024);

        System.out.println("\n=== Uso de Memória ===");
        System.out.println("Memória Total:     " + totalKB + " KB");
        System.out.println("Memória Ocupada:   " + usadoKB + " KB");
        System.out.println("Memória Disponível:" + livreKB + " KB");
    }

    private static void exportarArquivo(Fat32 fat32, Scanner sc) throws IOException {
        System.out.println("\n--- Exportar Arquivo ---");

        int indice = selecionarArquivo(fat32, sc, "exportação");

        if (indice >= 0) {
            String nomeArquivo = fat32.listarArquivos().get(indice);
            byte[] conteudo = fat32.read(nomeArquivo, 0, -1);

            String caminhoDestino = new File(System.getProperty("user.dir"), "src/main/resources/arquivos/pdfExport.pdf").getPath();
            File destino = new File(caminhoDestino);

            try {
                Files.write(destino.toPath(), conteudo);
                System.out.println("Arquivo exportado com sucesso para: " + caminhoDestino);
            } catch (IOException e) {
                System.err.println("Erro ao exportar arquivo: " + e.getMessage());
            }
        }
    }
}
