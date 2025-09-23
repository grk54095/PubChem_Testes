package pojetojava.projeto1; 

// IMPORTAÇÕES DE BIBLIOTECAS
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient; // IMPORTA O MENSAGEIRO DA MENSAGEM 
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner; 
import java.util.ArrayList; 
import java.util.List; 
import java.nio.file.Path; 
import java.nio.file.Paths; 



public class Teste5 { // CLASSE PRINCIPAL
    public static void main(String[] args) { // MÉTODO PRINCIPAL
        try (Scanner scanner = new Scanner(System.in)) { // SCANNER É O INPUT

            System.out.println("Digite uma molécula: "); 
            String compoundname = scanner.nextLine(); // LÊ A LINHA DIGITADA PELO USUÁRIO
            System.out.println("Você digitou: " + compoundname);
            // Variáveis Globais 
            HttpClient client = HttpClient.newHttpClient(); // CRIA O MENSAGEIRO
            Gson gson = new Gson(); // Cria um tradutor JSON
            int cidPrincipal;

            try {
                // Etapa 1 BUSCA O CID PRINCIPAL
                System.out.println("\n--- Buscando CID Principal ---"); 
                String urlCID = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + compoundname + "/cids/JSON";
                HttpRequest request = HttpRequest.newBuilder() // É A CAIXA DA MENSAGEM
                        .uri(URI.create(urlCID)) // COLOCA O DESTINATÁRIO NA CAIXA
                        .build(); // FINALIZA A CAIXA

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                // envio da mensagem            /dando ok para o client/ é o que vai retornar

                gson.fromJson(response.body(), JsonObject.class);

                // ACESSA OS DADOS DO JSON PARA PEGAR O CID PRINCIPAL
                JsonObject JsonObjectcid = gson.fromJson(response.body(), JsonObject.class);
                cidPrincipal= JsonObjectcid.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
                if (response.statusCode() == 200) {
                    System.out.println("CID principal encontrado: " + cidPrincipal);
                } else {
                    System.out.println("Falha na requisição.");
                }


                // Etapa 2 BUSCA POR SIMILARES
                System.out.println("\n--- Buscando Moleculas Similares ---");
                String url_similares = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/fastsimilarity_2d/cid/" + cidPrincipal + "/cids/JSON";
                HttpRequest request_similares = HttpRequest.newBuilder()
                        .uri(URI.create(url_similares))
                        .build();
                HttpResponse<String> response_similares = client.send(request_similares, HttpResponse.BodyHandlers.ofString());
                
                // PEGA A SEGUNDA RESPOSTA 
                JsonObject jsonObject_similares = gson.fromJson(response_similares.body(), JsonObject.class); 

                // ACESSA LISTA DE CIDS 
                com.google.gson.JsonArray cid_similares = jsonObject_similares.getAsJsonObject("IdentifierList").getAsJsonArray("CID");

                // CRIA LISTA PARA GUARDAR OS CIDS SIMILARES 
                List<Integer> lista_cids_similares = new ArrayList<>(); 
                int counter = Math.min(5, cid_similares.size()); // LIMITA A 5 SIMILARES
                for (int i = 0; i < counter; i++) {
                lista_cids_similares.add(cid_similares.get(i).getAsInt());
                    
                }

                // ETAPA 3 LOOP PARA PROPRIEDADES             
                System.out.println("\n--- Propriedades dos CIDs Similares ---"); 
                int index = 1; 
                for (Integer cid: lista_cids_similares) { 
                    String urlPropriedades = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/property/MolecularFormula/JSON";

                    //REQUISIÇÃO PARA PROPRIEDADES 
                    HttpRequest request_propriedades = HttpRequest.newBuilder()
                            .uri(URI.create(urlPropriedades))
                            .build(); 
                    HttpResponse<String> response_propriedades = client.send(request_propriedades, HttpResponse.BodyHandlers.ofString());
                    if (response_propriedades.statusCode() == 200) {
                        JsonObject jsonObject_propriedades = gson.fromJson(response_propriedades.body(), JsonObject.class);
                        
                        JsonObject dados= jsonObject_propriedades.getAsJsonObject("PropertyTable").getAsJsonArray("Properties").get(0).getAsJsonObject();
                        String formula = dados.has("MolecularFormula") ? dados.get("MolecularFormula").getAsString() : "N/A";
                        System.out.println(index + ". CID: " + cid + " | Fórmula Molecular: " + formula);
                    } else {
                        System.out.println("Falha na requisição para CID: " + cid);
                    } 

                    index++; 

                    //Pausa de 1 segundo entre as requisições 
                    Thread.sleep(1000);
                }


                // ETAPA 4 SELEÇÃO DE UM CID PARA DOWNLOAD 

            if (!lista_cids_similares.isEmpty()) {
                System.out.print("\nDigite o número do item que deseja baixar (1 a " + lista_cids_similares.size() + "): ");
                String escolhaStr = scanner.nextLine(); // Lê a escolha do usuário
                int escolhaInt;

                try {
                    escolhaInt = Integer.parseInt(escolhaStr); // Tenta converter o texto para número

                    // Valida se a escolha está no intervalo correto
                    if (escolhaInt >= 1 && escolhaInt <= lista_cids_similares.size()) {
                        // Mapeia a escolha (ex: 1) para o índice da lista (ex: 0)
                        int cidEscolhido = lista_cids_similares.get(escolhaInt - 1);
                        System.out.println("Você escolheu o CID: " + cidEscolhido);

                        // Monta a URL para o download do arquivo SDF
                        String urlDownload = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cidEscolhido + "/SDF";
                            
                         // Define o nome do arquivo que será salvo
                        Path caminhoDoArquivo = Paths.get("molecula_cid_" + cidEscolhido + ".sdf");

                        System.out.println("Baixando arquivo de " + urlDownload + " para " + caminhoDoArquivo.toAbsolutePath() + "...");

                        // Cria a requisição de download
                        HttpRequest requestDownload = HttpRequest.newBuilder().uri(URI.create(urlDownload)).build();

                        // ENVIA A REQUISIÇÃO E SALVA A RESPOSTA DIRETAMENTE NO ARQUIVO
                        HttpResponse<Path> responseDownload = client.send(requestDownload, HttpResponse.BodyHandlers.ofFile(caminhoDoArquivo));

                        if (responseDownload.statusCode() == 200) {
                            System.out.println("Download concluído com sucesso!");
                            System.out.println("O arquivo '" + caminhoDoArquivo.getFileName() + "' foi salvo na pasta do projeto.");
                        } else {
                                System.out.println("Falha no download. Status: " + responseDownload.statusCode());
                            }
                        } else {
                            System.out.println("Escolha inválida. Por favor, digite um número entre 1 e " + lista_cids_similares.size() + ".");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Entrada inválida. Por favor, digite apenas o número.");
                    }
                }

            } catch (IOException | InterruptedException e) {
                System.out.println("Ocorreu um erro ao fazer a requisição: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Não foi possível encontrar o CID. A molécula '" + compoundname + "' existe ou foi digitada corretamente?");
            }

        

            }
    }
    }


