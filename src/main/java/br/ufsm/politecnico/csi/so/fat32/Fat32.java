package br.ufsm.politecnico.csi.so.fat32;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Fat32 implements FileSystem {

    private static final int TAM_BLOCO = 64 * 1024;  // 64KB por bloco
    private static final int NUM_BLOCO = 1024;       // Número total de blocos
    private static final int DIRETORIO_BLOCO = 0;    // Bloco reservado para diretório.o bloco de índice 0 será usado exclusivamente para guardar informações dos arquivos e pastas do diretório principal
    private static final int TAM_ENTRADA_DIRETORIO = 19; // 11 (nome) + 4 (tamanho do arquivo)/32bits + 4 (bloco inicial)/32bits

    //capacidade máxima de arquivos no diretório. Pega o tamanho do bloco e divide pelo n de entradas disponiveis.
    //serve para ver se ainda tenho memoria para alocar o arquivo x
    private static final int NUMERO_MAXIMO_ENTRADAS = TAM_BLOCO / TAM_ENTRADA_DIRETORIO;

    // Componentes do sistema
    private final Disco disco;
    private final int[] fat;  // Tabela de alocação de arquivos
    private boolean inicializado;

    public Fat32(Disco disco) throws IOException {
        this.disco = disco;
        this.fat = new int[NUM_BLOCO];
        inicializarSistema();
    }

    private void inicializarSistema() throws IOException {

        //le os dados de um bloco especifico? o diretório
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);
        if (blocoDir[0] == 0) {
            formatarDisco();
        } else {
            carregarFat();
        }
        this.inicializado = true;

        //1. vai fazer uma verificação se o disco já foi inicializado
        //2. vai ler o primeiro bloco, se for 0, significa que o disco não foi inicializado
        //3. se não for 0, chama a função para carregar a FAT
        //4. iniciliaza o disco
    }

    private void formatarDisco() throws IOException {

        //se o diretorio for vazio, crio um novo diretorio com o tamanho do bloco. 64KB
        byte[] dirVazio = new byte[TAM_BLOCO];

        disco.write(DIRETORIO_BLOCO, dirVazio);


        //preenche todas as posições com 0 - FAT livre.
        //Preencher os blocos da FAT como vazios
        Arrays.fill(fat, 0);

        //dizer que o bloco que armazena a FAT é ocupado (-1)
        fat[DIRETORIO_BLOCO] = -1;


        //Calculo quantos blocos vão ser necessários para armazenar a FAT. Coloco como -1 os blocos ocupados pela FAT
        int blocosFAT = (NUM_BLOCO * 4 + TAM_BLOCO - 1) / TAM_BLOCO;

        for (int i = 1; i <= blocosFAT; i++) {
            fat[i] = -1;
        }


        gravarFat();

        //1. preciso dessa função quando for inicializar o Disco;
        //2. crio um array (com todas as posições em 0). Esse bloco vai ser o diretorio, onde os arquivos vão estar;
        //3. Inicializo a FAT com todas as posições em 0 (blocos livres);
        //4. Marco o primeiro bloco da FAT como ocupado, porque é o diretório
        //5. Calculo quantos blocos a FAT vai ocupar com essa formula: NUM_BLOCO * 4 + TAM_BLOCO - 1) / TAM_BLOCO
        //6. Reservo todos os blocos da FAT como ocupados, para não serem sobreescritos por arquivos
    }

    private void carregarFat() throws IOException {

        int blocoFat = 1;

        int offset = 0;

        // Percorre todas as entradas da FAT, distribuídas pelos blocos. LE a FAT
        for (int i = 0; i < NUM_BLOCO; i++) {
            // Verifica se o offset atingiu o tamanho do bloco
            if (offset >= TAM_BLOCO) {
                // Se o bloco foi completamente lido, passa para o próximo bloco
                blocoFat++;
                // Reseta o offset para o próximo bloco
                offset = 0;
            }

            //LE o bloco da FAt e retorna um array com essas informações
            byte[] bloco = disco.read(blocoFat);

            //Esta linha pega os dados do bloco lido e os converte em um valor inteiro (4 bytes). A FAT armazena 4bytes/32bits
            fat[i] = ByteBuffer.wrap(bloco, offset, 4).getInt();
            offset += 4;
        }

        //1.leio os blocos da FAT, começando em 1 pq o bloco 0 é o diretorio
        //2. Auxiliar offset para percorrer os demais blocos
        //3. Vai ler todo o array do bloco x, termina esse array e passa pro proximo
        //4. vai ler aquele bloco como um todo e Usa ByteBuffer.wrap(...).getInt() para extrair 4 bytes como um inteiro
        //A conversão de 4 bytes para um inteiro de 32 bits é essencial porque a FAT precisa armazenar informações complexas sobre blocos de dados.
        // Usar 1 byte por entrada não seria suficiente, já que ele só poderia representar um número de 0 a 255, enquanto 4 bytes permitem um número
        // muito maior de possibilidades.
        //5. passa pros proximos 4 bytes
    }

    @Override
    public void create(String fileName, byte[] data) throws IOException {
        if (!inicializado) throw new IOException("Sistema não inicializado");
        if (data == null) throw new IllegalArgumentException("Dados não podem ser nulos");

        //chamo a função de formatar nome
        String nomeFormatado = formatarNomeArquivo(fileName);
        if (buscarEntradaDiretorio(nomeFormatado) != null) {
            throw new IOException("Arquivo já existe: " + nomeFormatado);
        }

        //crio um inteiro com a quantidade de blocos que preciso
        //data.length me da o tamanho do arquivo que veio pelo byte [] data por parâmetro no método
        int blocosNecessarios = (data.length + TAM_BLOCO - 1) / TAM_BLOCO;


        if (blocosNecessarios > freeSpace()) {
            throw new IOException("Espaço insuficiente no disco");
        }


        //crio um arraay com a quantidade de blocos necessarios
        int[] blocos = alocarBlocos(blocosNecessarios);

        //gravarDados(): escreve os dados nos blocos alocados.
        gravarDados(data, blocos);

        //adicionarEntradaDiretorio(): adiciona o arquivo ao diretório.
        adicionarEntradaDiretorio(nomeFormatado, data.length, blocos[0]);

        //gravarFat(): salva a FAT atualizada no disco.
        gravarFat();

        //1. vai fazer uma verificação se o disco foi inicializado e se os dados não sao null;
        //2. vai criar um arquivo com o nome formatado
        //3. vai calcular quantos blocos serão necessarios e arrendor para mais, caso preciso.
        // data.length vai dizer o tamanho do dado + o tamanho do bloco
        //4. verificação de espaço disponivel
        //5. aloca blocos livres e retorna um array com eles, para que a função gravarDados() escreva esses dados no bloco bloco alocado do disco
        // 6. adiciona o arquivo no diretorio
        //7. atualiza a FAT
    }


    @Override
    public void append(String fileName, byte[] data) throws IOException {
        if (!inicializado) throw new IOException("Sistema não inicializado");
        //verifica se os dados que vieram por parametro não são null
        if (data == null || data.length == 0) return;

        //busca o arquivo no diretorio pelo nome. Passa para o metodo buscarEntradaDiretorio
        EntradaDiretorio entrada = buscarEntradaDiretorio(fileName);
        if (entrada == null) {
            throw new IOException("Arquivo não encontrado: " + fileName);
        }

        //calcula o espaço necessário para os dados adicionados
        int blocosNecessarios = (data.length + TAM_BLOCO - 1) / TAM_BLOCO;

        //verifica se tem espaço no disco
        if (blocosNecessarios > freeSpace()) {
            throw new IOException("Espaço insuficiente para adicionar dados");
        }

        //percorre do primeiro (getStarterBlock) até o ultimo bloco.
        //Ocupados são 1 e livre 0
        int ultimoBloco = entrada.getStarterBlock();
        while (fat[ultimoBloco] > 0) {
            ultimoBloco = fat[ultimoBloco];
        }

        // Preenche o espaço restante no último bloco
        //Usa o espaço livre no último bloco para não desperdiçar memória
        int espacoLivre = TAM_BLOCO - (entrada.getFileSize() % TAM_BLOCO);
        int offset = 0;

        //se tiver espaço livre no ultimo bloco, preenche
        if (espacoLivre > 0 && espacoLivre < TAM_BLOCO) {

            //le os dados do ultimo bloco do arquivo e coloca num array do tipo byte
            //aproveitamento do espaço livre
            byte[] blocoExistente = disco.read(ultimoBloco);
            int bytesParaEscrever = Math.min(espacoLivre, data.length);

            //copia parte dos dados para o espaço livre no ultimo bloco
            System.arraycopy(data, 0, blocoExistente, TAM_BLOCO - espacoLivre, bytesParaEscrever);

            //salva o ultimo bloco no disco
            disco.write(ultimoBloco, blocoExistente);

            //salva o restante do conteudo para ser salvo nos próximos blocos
            offset += bytesParaEscrever;
        }

        //Caso não caibam no ultimo bloco e precise de mais blocos
        // Aloca novos blocos se necessário e grava os dados restantes
        //Se houver dados adicionais para gravar, aloca mais blocos e liga-os ao último bloco
        if (offset < data.length) {
            int[] novosBlocos = alocarBlocos((data.length - offset + TAM_BLOCO - 1) / TAM_BLOCO);
            gravarDados(Arrays.copyOfRange(data, offset, data.length), novosBlocos);
            fat[ultimoBloco] = novosBlocos[0]; // Liga o último bloco ao novo
        }

        // Atualiza o diretório e a FAT
        //Atualiza o tamanho do arquivo e escreve a FAT no disco
        entrada.setFileSize(entrada.getFileSize() + data.length);
        atualizarEntradaDiretorio(entrada);
        gravarFat();

        //1. verifica se o disco foi inicializado e se os dados não são null
        //2. Busca a entrada do arquivo no diretório para verificar se o arquivo já existe.
        //3. Vai fazer uma verificação para ver se existe espaço necessario no disco. Similar a função create
        //4. Percorre a FAT a partir do bloco inicial do arquivo até o último bloco ocupado, identificando o último bloco de dados
        //5. Quando chegar no ultimo bloco ocupado, vai colocar mais dados nele, e caso necessário alocar mais blocos.
        //6. Os blocos alocados não precisam estar em sequência, mas são escolhidos entre os blocos livres disponíveis
        //7. Atualiza a ligação entre os blocos antigos e novos na FAT, conectando o último bloco ocupado ao novo bloco alocado
        //8. Atualiza o diretorio e a FAT
    }

    @Override
    public byte[] read(String fileName, int offset, int limit) throws IOException {
        //manda por parametro o nome, posição inicial do bloco, e a quantidade de bytes a ler
        if (!inicializado) throw new IOException("Sistema não inicializado");

        //buscar o arquivo no diretorio
        EntradaDiretorio entrada = buscarEntradaDiretorio(fileName);
        if (entrada == null) {
            throw new IOException("Arquivo não encontrado: " + fileName);
        }

//        offset < 0: Isso verifica se o offset (posição inicial de leitura) é negativo.
//        Como um offset negativo não faria sentido em termos de posições dentro de um arquivo, esse caso é invalidado.
//        offset >= entrada.getFileSize(): Aqui, o código verifica se o offset é maior ou igual ao tamanho do arquivo.
//        Ou seja, você não pode começar a leitura a partir de uma posição que ultrapassa o final do arquivo.
        if (offset < 0 || offset >= entrada.getFileSize()) {
            throw new IOException("Offset inválido: " + offset);
        }

        //Aqui, o cálculo dos bytes a serem lidos é feito para garantir que a quantidade de dados lidos não ultrapasse o limite solicitado, nem ultrapasse o tamanho do arquivo.
//        limit == -1: Se o valor do limit for -1, isso significa que você deseja ler até o final do arquivo a partir do offset fornecido. Portanto, a quantidade de bytes a serem lidos será simplesmente o tamanho do arquivo (entrada.getFileSize()) menos o offset, ou seja, você quer ler todos os bytes restantes.
//        Math.min(limit, entrada.getFileSize() - offset): Caso contrário, se o limit for um valor válido (não -1), o código vai calcular a quantidade de dados a serem lidos considerando o limit dado e o espaço restante do arquivo.
//        entrada.getFileSize() - offset calcula o número de bytes restantes a partir do offset até o final do arquivo.
//        Math.min(limit, entrada.getFileSize() - offset) garante que o número de bytes lidos não será maior do que o número de bytes restantes no arquivo nem maior do que o limit especificado. Ou seja, ele vai buscar o menor valor entre o limit e o número de bytes restantes, evitando que o código tente ler além do arquivo ou ultrapasse o limite imposto.
        int bytesParaLer = (limit == -1) ? entrada.getFileSize() - offset : Math.min(limit, entrada.getFileSize() - offset);
        ByteArrayOutputStream output = new ByteArrayOutputStream();


//        Lê os blocos do arquivo a partir do starter block. Em cada bloco, lê a quantidade necessária de dados, respeitando o offset
//        dentro do bloco e o limite de bytes a serem lidos.
        int blocoAtual = entrada.getStarterBlock();
        int bytesLidos = 0;
        int offsetNoBloco = offset % TAM_BLOCO;

        while (blocoAtual > 0 && bytesLidos < bytesParaLer) {
            byte[] bloco = disco.read(blocoAtual);
            int bytesDesteBloco = Math.min(TAM_BLOCO - offsetNoBloco, bytesParaLer - bytesLidos);

            output.write(bloco, offsetNoBloco, bytesDesteBloco);

            bytesLidos += bytesDesteBloco;
            blocoAtual = fat[blocoAtual];
            offsetNoBloco = 0;
        }

        return output.toByteArray();

//1. Verifica se o sistema de arquivos foi inicializado.
//2. Busca a entrada do arquivo no diretório para garantir que o arquivo existe.
// offset é uma posição
//3. Verifica se o offset (posição de leitura) é válido — ou seja, não pode ser negativo nem maior ou igual ao tamanho do arquivo.
//4. Calcula quantos bytes devem ser lidos, respeitando o limite pedido e o tamanho restante do arquivo.
//5. Começa a leitura a partir do bloco inicial do arquivo, seguindo os blocos indicados pela FAT.
//6. Lê cada bloco necessário, começando do offset correto no primeiro bloco, e vai até completar os bytes necessários ou chegar ao fim do arquivo.
    }

    @Override
    public void remove(String fileName) throws IOException {
        if (!inicializado) throw new IOException("Sistema não inicializado");

        //buscar o arquivo no diretorio
        EntradaDiretorio entrada = buscarEntradaDiretorio(fileName);
        if (entrada == null) {
            throw new IOException("Arquivo não encontrado: " + fileName);
        }

        //pego o primeiro bloco do arquivo e percorro até o bloco não ser 0. Estão ocupados (1).
        //Vou marcando os blocos com 0 (livre)
        int blocoAtual = entrada.getStarterBlock();
        while (blocoAtual > 0) {
            int proximoBloco = fat[blocoAtual];
            fat[blocoAtual] = 0;
            blocoAtual = proximoBloco;
        }

        removerEntradaDiretorio(entrada);
        gravarFat();

        //1. verificação de inicialização do disco
        //2. busca pelo arquivo no diretorio. EntradaDiretorio, seria o arquivo
        //3. vai percorrer um laço no primeiro bloco do arquivo e marcar eles como 0 na FAT
        //4. remove o arquivo do diretorio e atualiza a FAT
    }

    @Override
    public int freeSpace() {
        //calcular a quantidade de blocos livres disponíveis no disco
        if (!inicializado) {
            return 0;
        }

        int blocosLivres = 0;

        for (int i = 1; i < fat.length; i++) {
            if (fat[i] == 0) blocosLivres++;
        }
        return blocosLivres * TAM_BLOCO;

        //1. verificação de inicialização de disco
        //2. laço percorrendo a FAT, na posição 1 porque o zero é o diretorio
        //3. contabiliza na variavel blocosLivres os blocos que estão marcados como 0, ou seja, livres
        //4. retorna a quantidade de blocos livres * tamanho do bloco
    }

    public List<String> listarArquivos() throws IOException {
        if (!inicializado) throw new IOException("Sistema não inicializado");

        //crio um array com os arquivos do diretorio
        List<String> arquivos = new ArrayList<>();



        //le o diretorio. Posição 0
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);


        //percorre todas as entradas do diretorio
        for (int i = 0; i < NUMERO_MAXIMO_ENTRADAS; i++) {

            //desloca para a entrada de cada arquivo no diretorio. Primeiro bloco de cada arquivo
            int offset = i * TAM_ENTRADA_DIRETORIO;


            //se for !=0 significa que a entrada está ocupada
            if (blocoDir[offset] != 0) {
                // // Extrai nome e extensão, adiciona na lista
                String nome = new String(blocoDir, offset, 8).trim();
                String extensao = new String(blocoDir, offset + 8, 3).trim();
                arquivos.add(nome + (extensao.isEmpty() ? "" : "." + extensao));
            }
        }
        return arquivos;

        //1. verificação de inicialização do disco
        //2. cria uma lista para armazenar os nomes dos arquivos encontrados
        //3. lê o bloco 0, que é o bloco reservado para o diretório
        //4. percorre o bloco do diretório, usando o número máximo de entradas como limite
        //5. para cada possível entrada do diretório, verifica se ela está ocupada
        //6. extrai o nome (8 bytes) e a extensão (3 bytes) do arquivo, remove espaços em branco
        //7. adiciona o nome completo do arquivo na lista (com "." se tiver extensão)
        //8. retorna a lista com os nomes dos arquivos encontrados
    }

    // ========== MÉTODOS AUXILIARES ========== //


    private String formatarNomeArquivo(String nome) {

        nome = nome.trim().toUpperCase();

        //separo o  arquivo em nome e extensão. "\\" pq ponto é caratectere especial. Assim ele sabe que é para separar pelo ponto
        String[] partes = nome.split("\\.");


        //o nome, posição 0 no array que foi separado. Uso um regex para permitir caracteres de A-Z e de 0 - 9 e substituir tudo que nao for isso por nada. Corto esse caractere
        String nomeBase = partes[0].replaceAll("[^A-Z0-9]", "");
        String extensao = partes.length > 1 ? partes[1].replaceAll("[^A-Z0-9]", "") : "";


        //8 caracteres de nome e 3 de extensão
        nomeBase = nomeBase.length() > 8 ? nomeBase.substring(0, 8) : nomeBase;
        extensao = extensao.length() > 3 ? extensao.substring(0, 3) : extensao;

        return nomeBase + (extensao.isEmpty() ? "" : "." + extensao);

        //1. padronização do nome
        //2. separo a string em partes usando o "." como referencia. Tem um array com duas strings
        //3. pega a parte da extensão e remove tudo que nao for "[^A-Z0-9]"
        //4. verifica se a extensão é maior que um caractere: significa que a extensão foi passada
        //5. nome do arquivo vai ser os primeiros 8 caracteres
        //6. nome da extensão os primeiros 3 caracteres da extensão
        //7. vou retornar o nome + a extensão. Se ela não for vazia coloco "." + extensao
    }

    private int[] alocarBlocos(int quantidade) throws IOException {

        //quantidade que estou recebendo: os blocos necessarios para gravar um arquivo

        //crio um array com a quantidade de blocos que vou precisar ocupar no disco
        int[] blocos = new int[quantidade];

        int encontrados = 0;


        //percorre a FAT no indice 1 e verifica o bloco está livre (encontrados = 0)
        for (int i = 1; i < fat.length && encontrados < quantidade; i++) {
            if (fat[i] == 0) {
                //armazena no  int[] blocos o numero no bloco livre
                blocos[encontrados++] = i;
                fat[i] = -1;
            }
        }

        if (encontrados < quantidade) {
            throw new IOException("Espaço insuficiente no disco");
        }


        // Percorre o array int[] blocos (blocos livres alocados) e faz com que cada bloco aponte para o próximo na FAT.
        for (int i = 0; i < blocos.length - 1; i++) {
            fat[blocos[i]] = blocos[i + 1];
        }

        //marca o ultimo bloco com -1 (fim da lista FAT)
        fat[blocos[blocos.length - 1]] = -1;
        return blocos;

        //1.cria um array de blocos com a quantidade necessária passada por parametro
        //2. cria uma variavel para marcar os blocos livres encontrados
        //3. vai percorrer a FAT (a partir da posição 1) e caso a FAT tenha um bloco livre (valor 0), vai alocar ele (endereço de memoria dele) dele no array criado
        //4. marca aquele bloco na FAT como ocupado (-1)
        //5. Laço paraconectar os blocos da FAT com os novos blocos (ponteiro apontando para novos blocos)
        //6. retorna o array de blocos alocados
    }

    private void gravarDados(byte[] data, int[] blocos) throws IOException {

        //recebo um byte de dados (o arquivo a ser gravado) e o array de blocos livres que veem da função alocar blocos

        //array int[] blocos na posição 0
        int offset = 0;

        for (int bloco : blocos) {
            // Calcula quantos bytes ainda faltam para escrever, limitando ao tamanho do bloco
            int bytesParaEscrever = Math.min(TAM_BLOCO, data.length - offset);

            // Cria um array para armazenar os dados do bloco atual
            byte[] blocoDados = new byte[TAM_BLOCO];

            // Copia os bytes do arquivo (data), a partir do offset, para o blocoDados
            System.arraycopy(data, offset, blocoDados, 0, bytesParaEscrever);

            // Escreve o blocoDados no disco, no bloco atual
            disco.write(bloco, blocoDados);

            // Atualiza o offset para a próxima parte dos dados
            offset += bytesParaEscrever;
        }

        //1. recebe um array com dados e um array com a posição desses blocos na memoria
        //2. usa uma variavel para percorrer os blocos dos dados
        //3. Percorre o array de blocos para determinar, em cada iteração, quantos bytes ainda faltam gravar e quantos cabem no bloco atual.
        //4. Calcula a quantidade de dados por bloco. Sempre arredondando para mais
        //5. cria um novo array com o tamanho do bloco 64 * 1024, declarado no começo
        //6. usa o arrayCopy para copiar os dados do array passado por parametro pro novo array criado
        //7. escreve no disco com o índice do bloco e o conteúdo de blocoDados
    }

    private void gravarFat() throws IOException {


        // Quantas entradas inteiras (4 bytes cada) cabem em um bloco
        int entradasPorBloco = TAM_BLOCO / 4;

        // Calcula quantos blocos serão necessários para armazenar toda a FAT
        //4 pq está em bits e precisso em bytes
        int blocosFAT = (NUM_BLOCO * 4 + TAM_BLOCO - 1) / TAM_BLOCO;

        // Percorre cada bloco necessário para salvar a FAT no disco
        for (int i = 0; i < blocosFAT; i++) {
            // a fat é composta por varios blocos. Calcular quantos blocos a FAT vai ter
            // Cria um array de bytes para armazenar os dados de um bloco da FAT
            byte[] blocoFat = new byte[TAM_BLOCO];

            // Percorre as entradas da FAT que cabem nesse bloco. Por exemplo, bloco 1 aponta para o 2
            for (int j = 0; j < entradasPorBloco; j++) {

                // Calcula o índice da entrada da FAT que corresponde à posição j no bloco i da FAT a ser gravada
                //J é o indice dentro da FAT. J é a  posição atual, no for, que estou na FAT
                int indiceFat = i * entradasPorBloco + j;

                //indiceFat entrada atual que está sendo copiada para a memoria
                if (indiceFat < NUM_BLOCO) {
                    //Preenche a FAT: copia os valores da FAT para o array, 1 inteiro (4 bytes) por vez
                    //Converter os valores de inteiros em um array de Bytes
                    System.arraycopy(intToBytes(fat[indiceFat]), 0, blocoFat, j * 4, 4);
                }
            }

            // Grava o bloco da FAT no disco, começando a partir do bloco 1
            disco.write(1 + i, blocoFat); // Blocos FAT começam no bloco 1
        }

        //1.Grava a tabela FAT no disco
        //2. A quantidade de entradas que UM UNICO bloco vai poder armazenar: TAM_BLOCO / 4 (4bytes = 32 bits);
        //3. Calcula a quantidade de blocos necessários para armazenar a FAT
        //4. Laço que vai criar um array para cada bloco que será armazenado na FAT
        //5.Preenche a FAT: copia os valores da FAT para o array, 1 inteiro (4 bytes) por vez
        //6. Converter os valores de inteiros em um array de Bytes
        //7. Escreve cada bloco no disco: grava cada bloco no disco usando o índice correspondente.
    }

    private EntradaDiretorio buscarEntradaDiretorio(String fileName) throws IOException {
        String nomeBusca = formatarNomeArquivo(fileName);
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);

        for (int i = 0; i < NUMERO_MAXIMO_ENTRADAS; i++) {
            int offset = i * TAM_ENTRADA_DIRETORIO;

            // Verifica se a entrada está em uso
            if (blocoDir[offset] != 0) {
                String nome = new String(blocoDir, offset, 8).trim();
                String extensao = new String(blocoDir, offset + 8, 3).trim();
                String nomeCompleto = nome + (extensao.isEmpty() ? "" : "." + extensao);

                if (nomeCompleto.equals(nomeBusca)) {
                    int tamanho = ByteBuffer.wrap(blocoDir, offset + 11, 4).getInt();
                    int blocoInicial = ByteBuffer.wrap(blocoDir, offset + 15, 4).getInt();
                    return new EntradaDiretorio(nomeCompleto, tamanho, blocoInicial);
                }
            }
        }
        return null;

        //1. recebe um arquivo (tipo do arquivo EntradaDiretorio)
        //2. formata o nome para busca (ficar igual ao nome do arquivo salvo)
        //3. Lê o bloco do diretório (blocoDir)
        //4. Percorre as entradas do diretório: verifica cada entrada até o número máximo permitido
        //5. Verifica se a entrada tem dados válidos
        //6. Extrai o nome e a extensão
        //7. Comparação entre nome extraido e nome fornecido
        //8. Le os dados com o ByteBuffer
        //9. Retorna o arquivo tipo do arquivo EntradaDiretorio
    }

    private void adicionarEntradaDiretorio(String fileName, int fileSize, int starterBlock) throws IOException {
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);
        String[] partes = fileName.split("\\.");
        String nomeBase = partes[0];
        String extensao = partes.length > 1 ? partes[1] : "";

        for (int i = 0; i < NUMERO_MAXIMO_ENTRADAS; i++) {
            int offset = i * TAM_ENTRADA_DIRETORIO;

            if (blocoDir[offset] == 0) {
                byte[] nomeBytes = Arrays.copyOf(nomeBase.getBytes(), 8);
                System.arraycopy(nomeBytes, 0, blocoDir, offset, Math.min(nomeBase.length(), 8));

                if (!extensao.isEmpty()) {
                    byte[] extBytes = Arrays.copyOf(extensao.getBytes(), 3);
                    System.arraycopy(extBytes, 0, blocoDir, offset + 8, Math.min(extensao.length(), 3));
                }

                System.arraycopy(intToBytes(fileSize), 0, blocoDir, offset + 11, 4);

                System.arraycopy(intToBytes(starterBlock), 0, blocoDir, offset + 15, 4);

                disco.write(DIRETORIO_BLOCO, blocoDir);
                return;
            }
        }
        throw new IOException("Diretório cheio");

        //1. recebe um arquivo, um tamanho e o bloco inicial dele para gravar
        //2. Lê o bloco do diretório (blocoDir)
        //3. Divide o nome do arquivo em nome base e extensão
        //4. Percorre as entradas do diretório até o número máximo de entradas
        //5. Encontra a entrada vazia: verifica se o primeiro byte da entrada é zero (livre)
        //6. Preenche os campos da entrada: Nome (8 bytes); Extensão (3 bytes); Tamanho (4 bytes); Bloco inicial (4 bytes)
        //7. Grava o bloco de diretório no disco.
    }

    private void atualizarEntradaDiretorio(EntradaDiretorio entrada) throws IOException {
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);
        String[] partes = entrada.getFileName().split("\\.");

        for (int i = 0; i < NUMERO_MAXIMO_ENTRADAS; i++) {
            int offset = i * TAM_ENTRADA_DIRETORIO;

            // Encontra a entrada do arquivo
            String nome = new String(blocoDir, offset, 8).trim();
            String extensao = new String(blocoDir, offset + 8, 3).trim();
            String nomeCompleto = nome + (extensao.isEmpty() ? "" : "." + extensao);

            if (nomeCompleto.equals(entrada.getFileName())) {
                // Atualiza tamanho e bloco inicial
                System.arraycopy(intToBytes(entrada.getFileSize()), 0, blocoDir, offset + 11, 4);
                System.arraycopy(intToBytes(entrada.getStarterBlock()), 0, blocoDir, offset + 15, 4);
                disco.write(DIRETORIO_BLOCO, blocoDir);
                return;
            }
        }
        throw new IOException("Entrada não encontrada para atualização");

        //1. Recebe uma entrada do diretório (EntradaDiretorio) para atualizar
        //2. Lê o bloco do diretório (blocoDir).
        //3. Divide o nome do arquivo (da entrada) em nome base e extensão
        //4. Percorre as entradas do diretório até o número máximo de entradas
        //5. Verifica se encontrou a entrada correspondente ao nome do arquivo
        //6. Atualiza a entrada:
        //7. Tamanho: copia o novo tamanho da entrada para o bloco de diretório
        //8. Bloco inicial: copia o novo bloco inicial.
        //9. Grava o bloco atualizado no disco
    }

    private void removerEntradaDiretorio(EntradaDiretorio entrada) throws IOException {
        byte[] blocoDir = disco.read(DIRETORIO_BLOCO);

        for (int i = 0; i < NUMERO_MAXIMO_ENTRADAS; i++) {
            int offset = i * TAM_ENTRADA_DIRETORIO;

            // Verifica se é a entrada correta
            String nome = new String(blocoDir, offset, 8).trim();
            String extensao = new String(blocoDir, offset + 8, 3).trim();
            String nomeCompleto = nome + (extensao.isEmpty() ? "" : "." + extensao);

            if (nomeCompleto.equals(entrada.getFileName())) {
                // Limpa a entrada (preenche com zeros)
                Arrays.fill(blocoDir, offset, offset + TAM_ENTRADA_DIRETORIO, (byte) 0);
                disco.write(DIRETORIO_BLOCO, blocoDir);
                return;
            }
        }

        //1. Recebe uma entrada do diretório (EntradaDiretorio) para atualizar
        //2. Lê o bloco do diretório (blocoDir).
        //3. Percorre as entradas do diretório até o número máximo de entradas
        //4. Compara o nome completo (nome + extensão) da entrada com o nome fornecido
        //5. Limpa a entrada: Se encontrar a entrada, preenche o espaço ocupado pela entrada com zeros
        //6. Grava o bloco de diretório atualizado no disco

    }

    private byte[] intToBytes(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value
        };
    }

    //a função gera um array de 4 bytes que representa o valor inteiro de 4 bytes de forma binária
//    A conversão de um int para bytes é necessária porque os sistemas de arquivos, redes e armazenamento
//    trabalham com dados binários. Um int ocupa 4 bytes, e a conversão permite armazenar ou transmitir esse valor de
//    forma eficiente

    //Armazenamento: No disco, os valores inteiros precisam ser gravados como bytes
    //Transmissão: Para enviar dados pela rede, é preciso convertê-los para bytes.




    public int contarBlocosOcupados() throws IOException {
        byte[] blocoFAT = disco.read(1); // bloco 1 é a FAT
        int ocupados = 0;

        for (int i = 2; i < Disco.NUM_BLOCO; i++) {
            int pos = i * 4;
            if (pos + 4 > blocoFAT.length) break;

            int valor = ((blocoFAT[pos] & 0xFF) << 24) |
                    ((blocoFAT[pos + 1] & 0xFF) << 16) |
                    ((blocoFAT[pos + 2] & 0xFF) << 8) |
                    (blocoFAT[pos + 3] & 0xFF);

            if (valor != 0) {
                ocupados++;
            }
        }

        return ocupados;
    }
}