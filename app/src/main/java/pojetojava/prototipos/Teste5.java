/** 
 This file is part of the PojetoJava project. 
 The code has the objetive to link JAVA with Pubchem API 
 and download molecular files.
 */





package pojetojava.prototipos; 

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

            System.out.println("Enter a molecule name:"); 
            String compoundName = scanner.nextLine(); // LÊ A LINHA DIGITADA PELO USUÁRIO
            System.out.println("Searching for: " + compoundName);
            // Variáveis Globais 
            HttpClient client = HttpClient.newHttpClient(); // CRIA O MENSAGEIRO
            Gson gson = new Gson(); // Cria um tradutor JSON
            int principalCid;

            try {
                // Etapa 1 BUSCA O CID PRINCIPAL
                System.out.println("\n--- Searching for Principal CID ---");
                String urlCID = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + compoundName + "/cids/JSON";
                HttpRequest request = HttpRequest.newBuilder() // É A CAIXA DA MENSAGEM
                        .uri(URI.create(urlCID)) // COLOCA O DESTINATÁRIO NA CAIXA
                        .build(); // FINALIZA A CAIXA

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                // envio da mensagem            /dando ok para o client/ é o que vai retornar

                gson.fromJson(response.body(), JsonObject.class);

                // ACESSA OS DADOS DO JSON PARA PEGAR O CID PRINCIPAL
                JsonObject jsonObjectCid = gson.fromJson(response.body(), JsonObject.class);
                principalCid = jsonObjectCid.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
                if (response.statusCode() == 200) {
                    System.out.println("Principal CID found: " + principalCid);
                } else {
                    System.out.println("Request failed.");
                }


                // Etapa 2 BUSCA POR SIMILARES
                System.out.println("\n--- Searching for Similar Molecules ---");
                String urlSimilares = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/fastsimilarity_3d/cid/" + principalCid + "/cids/JSON";
                HttpRequest requestSimilares = HttpRequest.newBuilder()
                        .uri(URI.create(urlSimilares))
                        .build();
                HttpResponse<String> responseSimilares = client.send(requestSimilares, HttpResponse.BodyHandlers.ofString());

                // PEGA A SEGUNDA RESPOSTA
                JsonObject jsonObjectSimilares = gson.fromJson(responseSimilares.body(), JsonObject.class);

                // ACESSA LISTA DE CIDS 
                com.google.gson.JsonArray similarCids = jsonObjectSimilares.getAsJsonObject("IdentifierList").getAsJsonArray("CID");

                // CRIA LISTA PARA GUARDAR OS CIDS SIMILARES 
                List<Integer> SimilarCidsList = new ArrayList<>(); 
                int counter = Math.min(5, similarCids.size()); // LIMITA A 5 SIMILARES
                for (int i = 0; i < counter; i++) {
                    SimilarCidsList.add(similarCids.get(i).getAsInt());
                }

                // ETAPA 3 LOOP PARA PROPRIEDADES             
                System.out.println("\n--- Properties of Similar CIDs ---"); 
                int index = 1; 
                for (Integer cid: SimilarCidsList) { 
                    String urlPropriedades = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/property/MolecularFormula/JSON";

                    //REQUISIÇÃO PARA PROPRIEDADES 
                    HttpRequest request_propriedades = HttpRequest.newBuilder()
                            .uri(URI.create(urlPropriedades))
                            .build(); 
                    HttpResponse<String> responsePropriedades = client.send(request_propriedades, HttpResponse.BodyHandlers.ofString());
                    if (responsePropriedades.statusCode() == 200) {
                        JsonObject jsonObjectPropriedades = gson.fromJson(responsePropriedades.body(), JsonObject.class);

                        JsonObject dados= jsonObjectPropriedades.getAsJsonObject("PropertyTable").getAsJsonArray("Properties").get(0).getAsJsonObject();
                        String formula = dados.has("MolecularForm") ? dados.get("MolecularForm").getAsString() : "N/A";
                        System.out.println(index + ". CID: " + cid + " | Molecular Form: " + formula);
                    } else {
                        System.out.println("Request failed for CID: " + cid);
                    } 

                    index++; 

                    //Pausa de 1 segundo entre as requisições 
                    Thread.sleep(1000);
                }


                // ETAPA 4 SELEÇÃO DE UM CID PARA DOWNLOAD 

            if (!SimilarCidsList.isEmpty()) {
                System.out.print("\nWrite a number of the item you want to download (1 to " + SimilarCidsList.size() + "): ");
                String selectStr = scanner.nextLine(); // Lê a escolha do usuário
                int selectInt;

                try {
                    selectInt = Integer.parseInt(selectStr); // Tenta converter o texto para número

                    // Valida se a escolha está no intervalo correto
                    if (selectInt >= 1 && selectInt <= SimilarCidsList.size()) {
                        // Mapeia a escolha (ex: 1) para o índice da lista (ex: 0)
                        int selectedCid = SimilarCidsList.get(selectInt - 1);
                        System.out.println("You chose: " + selectedCid);

                        // Monta a URL para o download do arquivo SDF
                        String urlDownload = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + selectedCid + "/SDF?record_type=3d";

                         // Define o nome do arquivo que será salvo
                        Path filePath = Paths.get("molecula_cid_" + selectedCid + ".sdf");

                        System.out.println("Downloading " + urlDownload + " to " + filePath.toAbsolutePath() + "...");

                        // Cria a requisição de download
                        HttpRequest requestDownload = HttpRequest.newBuilder().uri(URI.create(urlDownload)).build();

                        // ENVIA A REQUISIÇÃO E SALVA A RESPOSTA DIRETAMENTE NO ARQUIVO
                        HttpResponse<Path> responseDownload = client.send(requestDownload, HttpResponse.BodyHandlers.ofFile(filePath));

                        if (responseDownload.statusCode() == 200) {
                            System.out.println("Download completed");
                            System.out.println("The file '" + filePath.getFileName() + "' has been saved in the project folder.");
                        } else {
                                System.out.println("Download failed. Status: " + responseDownload.statusCode());
                            }
                        } else {
                            System.out.println("Invalid choice. Please enter a number between 1 and " + SimilarCidsList.size() + ".");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input. Please enter a number.");
                    }
                }

            } catch (IOException | InterruptedException e) {
                System.out.println("An error occurred while making the request: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Could not find the CID. Does the molecule '" + compoundName + "' exist or was it typed correctly?");
            }

        

            }
    }
    }



// CÓDIGO ERRADO DO JMOL 
    // Nova janela para visualização 3D 
    //private class VisualizationDialog extends JDialog {
        //private final int cid;
        //private JmolViewer viewer; 
        //private Container jmolPanel;

        //public VisualizationDialog(JFrame owner, int cidToVisualize) {
            //super(owner, "3D Visualization - CID: " + cidToVisualize, true);
            //this.cid = cidToVisualize;

            //setSize(600, 600);
            //setLayout(new BorderLayout()); 

             // Cria o painel que vai conter o Jmol
          // JPanel displayPanel = new JPanel();
            //displayPanel.setPreferredSize(new Dimension(600, 550));
           // displayPanel.setLayout(new BorderLayout());
            //displayPanel.setBackground(Color.WHITE);
            //displayPanel.setDoubleBuffered(false);


            // 1. Cria o painel do Jmol
            //JmolAdapter adapter = new SmarterJmolAdapter();
            //viewer = JmolViewer.allocateViewer(jmolPanel, adapter);
            //viewer.evalString("set antialiasDisplay true");
            //viewer.evalString("set autoBond true");
            // Guarda referência ao container
            //jmolPanel = displayPanel;

            // 2. Cria o painel de botões na parte de baixo
            //JPanel buttonPanel = new JPanel(new FlowLayout());
            //JButton saveButton = new JButton("Save SDF File");
            //JButton closeButton = new JButton("Close");
            //buttonPanel.add(saveButton);
            //buttonPanel.add(closeButton);

            // 3. Adiciona os painéis à janela de diálogo
            //add(jmolPanel, BorderLayout.CENTER);
            //add(buttonPanel, BorderLayout.SOUTH); 

            // 4. Ações dos botões
            //closeButton.addActionListener(e -> dispose()); // dispose() fecha um JDialog

            //saveButton.addActionListener(e -> {
                // Reutiliza nosso DownloadWorker para salvar o arquivo!
                //saveButton.setEnabled(false); // Desabilita enquanto salva
                //DownloadWorker worker = new DownloadWorker(cid);
                // Adiciona uma lógica para reabilitar o botão quando o download terminar
               // worker.addPropertyChangeListener(evt -> {
                    //if ("state".equals(evt.getPropertyName()) && SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
                        //saveButton.setEnabled(true);
                   // }
                //});
                //worker.execute();
            //});

            // 5. Carrega a molécula em segundo plano
           // loadMolecule();

            // 6. Finaliza e exibe a janela
            //setLocationRelativeTo(owner);
            //setVisible(true);
        //}

        //private void loadMolecule() {
            // Usa um SwingWorker para carregar a molécula e não travar a interface
            //new SwingWorker<Void, Void>() {
                //@Override
                //protected Void doInBackground() throws Exception {
                    //String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/SDF?record_type=3d";
                    //String moleculeData = downloadMoleculeData(url);
                    //viewer.openStringInline(moleculeData);

                    //Thread.sleep(500); // Pequena pausa para garantir que o Jmol processe os dados

                    // Comandos para renderizar corretamente
                    //viewer.evalString("background white");   // Fundo branco
                   //viewer.evalString("spin off");           // Desliga rotação automática
                    //viewer.evalString("wireframe 0.15");     // Mostra as ligações
                    //viewer.evalString("spacefill 20%");      // Mostra os átomos
                   //viewer.evalString("color cpk");          // Coloração padrão (CPK)
                    //viewer.evalString("zoom 100");           // Ajusta o zoom
                    //return null;
                //}

                //@Override
                //protected void done() {
                    // Poderíamos tratar erros aqui se necessário
                    //System.out.println("Molécula CID " + cid + " carregada no Jmol.");
                    //Timer repaintTimer = new Timer(50, e -> jmolPanel.repaint());
                    //repaintTimer.start();
                    
                    // Para o timer após 2 segundos
                    //Timer stopTimer = new Timer(2000, e -> repaintTimer.stop());
                    //stopTimer.setRepeats(false);
                    //stopTimer.start();
               // }
           // }.execute();
       // }

