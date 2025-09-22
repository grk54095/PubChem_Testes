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

public class Teste5 { // CLASSE PRINCIPAL
    public static void main(String[] args) { // MÉTODO PRINCIPAL
        Scanner scanner = new Scanner(System.in); // SCANNER É O INPUT 

        System.out.println("Digite uma molécula: "); 

        String compoundname = scanner.nextLine(); // LÊ A LINHA DIGITADA PELO USUÁRIO
       scanner.close();

        System.out.println("Você digitou: " + compoundname); 

        // Variáveis Globais 
        HttpClient client = HttpClient.newHttpClient(); // CRIA O MENSAGEIRO
        Gson gson = new Gson(); // Cria um tradutor JSON
        int cidPrincipal;

        try {
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + compoundname + "/cids/JSON";
            HttpRequest request = HttpRequest.newBuilder() // É A CAIXA DA MENSAGEM
                    .uri(URI.create(url)) // COLOCA O DESTINATÁRIO NA CAIXA
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

            // FAZ A BUSCA POR SIMILARES
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

            //LOOP PARA PRINTAR OS CIDS SIMILARES
            System.out.println("CIDs similares encontrados: ");  
            int count = Math.min(5, cid_similares.size()); // LIMITA A 5 SIMILARES 
            for (int i = 0; i < count; i++) {
            int cidSimilares = cid_similares.get(i).getAsInt();
                System.out.println((i + 1) + ". CID: " + cidSimilares);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Ocorreu um erro ao fazer a requisição: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Não foi possível encontrar o CID. A molécula '" + compoundname + "' existe ou foi digitada corretamente?");
        }

       

        }
    }


