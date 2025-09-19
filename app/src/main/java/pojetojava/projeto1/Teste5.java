package pojetojava.projeto1; 

// 2. As importações, incluindo as do Gson que o Gradle vai fornecer.
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient; // IMPORTA O MENSAGEIRO DA MENSAGEM 
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner; 

public class Teste5 { // Classe principal 
    public static void main(String[] args) { // Método principal
        Scanner scanner = new Scanner(System.in); // SCANNER É O INPUT 

        System.out.println("Digite uma molécula: "); 

        String compoundname = scanner.nextLine(); // LÊ A LINHA DIGITADA PELO USUÁRIO
       scanner.close();

        System.out.println("Você digitou: " + compoundname); 

        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + compoundname + "/cids/JSON"; 

        try {
            HttpClient client = HttpClient.newHttpClient(); // CRIA O MENSAGEIRO
            HttpRequest request = HttpRequest.newBuilder() // É A CAIXA DA MENSAGEM
                    .uri(URI.create(url)) // COLOCA O DESTINATÁRIO NA CAIXA
                    .build(); // FINALIZA A CAIXA

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // envio da mensagem            /dando ok para o client/ é o que vai retornar

            Gson gson = new Gson(); // Cria um tradutor JSON
            JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
            // AQUI É ONDE O JSON É LIDO

            // Acessa os dados dentro do JSON para pegar o CID principal
            int cidPrincipal= jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
            if (response.statusCode() == 200) {
                System.out.println("Requisição bem-sucedida!");
            } else {
                System.out.println("Falha na requisição.");
            }

            System.out.println("\n--- Resposta da API ---");
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Body: " + cidPrincipal); // Recebimento da mensagem
        } catch (IOException | InterruptedException e) {
            System.out.println("Ocorreu um erro ao fazer a requisição: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Não foi possível encontrar o CID. A molécula '" + compoundname + "' existe ou foi digitada corretamente?");
        }
    }
}

