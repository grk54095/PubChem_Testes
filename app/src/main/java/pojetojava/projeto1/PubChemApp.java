/**
 * This file is part of the PojetoJava project.
 * The code has the objetive to link JAVA with Pubchem API
 * and is responasble to make a GUI to search for molecules and show results.
 * It uses Swing for the interface and HttpClient for API requests. 
 */


package pojetojava.projeto1;

// Importações do Swing para a interface gráfica
import javax.swing.*;
import java.awt.*; 
import java.util.List; 
import java.util.ArrayList;

// Importações para a lógica da API
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonArray; 
import java.nio.file.Path; 
import java.nio.file.Paths;  
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Importações para o Jmol
import org.jmol.api.JmolViewer;
import org.jmol.api.JmolAdapter;
import org.jmol.adapter.smarter.SmarterJmolAdapter;  



public class PubChemApp {

    // --- Parte 1: Componentes da Interface  ---
    private JFrame frame;
    private JTextField searchField;
    private JButton searchButton;
    private JTextArea resultsArea;
    private JScrollPane scrollPane; // Para adicionar uma barra de rolagem à área de texto

    //---  Componetes para área de download 
    private JTextField downloadField;
    private JButton downloadButton; 
    private List<Molecula> currentResults = new ArrayList<>();

    // --- Parte 2: Ferramentas da Lógica ---
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    // Construtor da classe: é aqui que a aplicação é montada
    public PubChemApp() {
        createUI();
    }

    // Método principal que inicia a aplicação
    public static void main(String[] args) {
        // Garante que a interface gráfica seja criada na thread correta
        SwingUtilities.invokeLater(() -> new PubChemApp());
    }

    // Método para criar e configurar a interface gráfica
    private void createUI() {
        // 1. Criar a Janela Principal 
        frame = new JFrame("PubChem Molecule Search");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // 2. Criar os painéis organizadores 
        JPanel topPanel = new JPanel(new FlowLayout()); // Painel para a busca
        JPanel mainPanel = new JPanel(new BorderLayout()); // Painel principal

        // 3. Criar os componentes
        searchField = new JTextField(30);
        searchButton = new JButton("Search");
        resultsArea = new JTextArea();
        resultsArea.setEditable(false); // O usuário não pode digitar aqui
        scrollPane = new JScrollPane(resultsArea); // Coloca a área de texto dentro de uma barra de rolagem

        // 4. Painel inferior para a função de download ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(new JLabel("Enter item #(1-5) to visualize:"));
        downloadField = new JTextField(5); // Um campo de texto pequeno para o número
        bottomPanel.add(downloadField);
        downloadButton = new JButton("Visualize 3D");
        bottomPanel.add(downloadButton);
        downloadButton.setEnabled(false); // Começa desabilitado

        // 5. Adicionar os componentes aos painéis
        topPanel.add(new JLabel("Molecule Name:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        // 6. Adicionar os painéis à janela
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        frame.add(mainPanel);

        // 7. Método que inicia SwingWorker para busca assíncrona
        searchButton.addActionListener(e -> startSearchWorker()); 
        downloadButton.addActionListener(e -> startVisualize()); 

        // 8. Tornar a janela visível 
        frame.setLocationRelativeTo(null); // Centraliza a janela
        frame.setVisible(true);
    }

    // --- Parte 3: Lógica do Negócio  --- 

    /**
     * Este método inicia um SwingWorker para realizar a busca de forma assíncrona,
     * evitando que a interface gráfica congele durante a operação.
     */ 
    private void startSearchWorker() {
        String compoundName = searchField.getText();
        if (compoundName == null || compoundName.trim().length() < 3) {
            JOptionPane.showMessageDialog(frame, "Enter at least 3 characters.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }

        resultsArea.setText("Searching for suggestions, please wait..."); // Feedback para o usuário
        searchButton.setEnabled(false); // Desabilita o botão durante a busca

        // Cria o SwingWorker 
        PubChemSearchWorker worker = new PubChemSearchWorker(compoundName);
        worker.execute(); // Inicia o worker 
    }

    private void startVisualize() {
        String choiceStr = downloadField.getText(); 
        int choiceInt; 

        try { 
            choiceInt = Integer.parseInt(choiceStr);
            if (choiceInt < 1 || choiceInt > currentResults.size()) {
                throw new NumberFormatException(); // Força o erro se o número estiver fora do intervalo
            } 
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid number from the list.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Pega o CID correspondente da nossa lista de resultados "salva"
        int cidToVisualize = currentResults.get(choiceInt - 1).cid;

        // Abre a janela de visualização
        new VisualizationDialog(frame, cidToVisualize);
    }
    // --- PARTE 4: SWINGWORKERS PARA TAREFAS ASSÍNCRONAS ---     
    // Herança de SwingWorker para busca assíncrona da API
    private class PubChemSearchWorker extends SwingWorker<List<Molecula>, Void> {
        
        private final String partialName;

        public PubChemSearchWorker(String partialName) {
            this.partialName = partialName; 
        } 

        @Override 
        protected List<Molecula> doInBackground() throws Exception {
           // 1 Busca Sugestões 
           List<String> sugestoes = PubChemApp.this.searchSuggestions(partialName); 
           if (sugestoes.isEmpty()) {
               throw new Exception("No suggestions found.");
           }

           // 2 Escolha do Usuário
           StringBuilder suggestionText = new StringBuilder("Please choose a number (1-10):\n");
           for (int i = 0; i < sugestoes.size(); i++) {
               suggestionText.append(i + 1).append(": ").append(sugestoes.get(i)).append("\n");
           }

           String userChoice = JOptionPane.showInputDialog(frame, suggestionText.toString(), "Choose a suggestion", JOptionPane.PLAIN_MESSAGE);
           if (userChoice == null) {
               return new ArrayList<>(); // Usuário cancelou
           }

           int choiceInt = Integer.parseInt(userChoice);
           if (choiceInt < 1 || choiceInt > sugestoes.size()) {
               throw new Exception("Invalid choice.");
           }

           String selectedName= sugestoes.get(choiceInt - 1); 

           // 3 Continua com o nome selecionado 
           int PrincipalCid = PubChemApp.this.findPrincipalCid(selectedName); 
           List<Integer> similarCids = PubChemApp.this.findSimilarCid(PrincipalCid);
           List<Molecula> results = PubChemApp.this.findProperties(similarCids); 

           return results;
        }

        @Override
        protected void done() {
            try{
                List<Molecula> moleculas = get(); // pega do doInBackground
                if (!moleculas.isEmpty()) {
                    displayResults(moleculas); 
                } else {
                    resultsArea.setText("No results found."); 
                }
            } catch (Exception e) {
                resultsArea.setText("Error: " + e.getCause().getMessage()); 
                JOptionPane.showMessageDialog(frame, "Error: " + e.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            searchButton.setEnabled(true); // Reabilita o botão
        }
    } 

    // Método que dedica para Download 
    private class DownloadWorker extends SwingWorker<Path, Void> {
        private final int cidToDownload;

        public DownloadWorker(int cid) {
            this.cidToDownload = cid;
        }

        @Override
        protected Path doInBackground() throws Exception {
            String downloadUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cidToDownload + "/SDF?record_type=3d";
            Path filePath = Paths.get("molecule_cid_" + cidToDownload + ".sdf");

            HttpRequest requestDownload = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build();
            HttpResponse<Path> responseDownload = client.send(requestDownload, HttpResponse.BodyHandlers.ofFile(filePath));

            if (responseDownload.statusCode() != 200) {
                throw new Exception("Download failed with status code: " + responseDownload.statusCode());
            }
            return responseDownload.body(); // Retorna o caminho do arquivo salvo
        }

        @Override
        protected void done() {
            try {
                Path downloadedFile = get();
                JOptionPane.showMessageDialog(frame, "Download complete!\nFile saved as: " + downloadedFile.getFileName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Download failed: " + e.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            // Reabilita os botões após o download
            searchButton.setEnabled(true);
            downloadButton.setEnabled(true);
        }
    }

    // Nova janela para visualização 3D 
    private class VisualizationDialog extends JDialog {
        private final int cid;
        private JmolViewer viewer; 
        private Container jmolPanel;

        public VisualizationDialog(JFrame owner, int cidToVisualize) {
            super(owner, "3D Visualization - CID: " + cidToVisualize, true);
            this.cid = cidToVisualize;

            setSize(600, 600);
            setLayout(new BorderLayout()); 

             // Cria o painel que vai conter o Jmol
           JPanel displayPanel = new JPanel();
            displayPanel.setPreferredSize(new Dimension(600, 550));
            displayPanel.setLayout(new BorderLayout());
            displayPanel.setBackground(Color.WHITE);
            displayPanel.setDoubleBuffered(false);


            // 1. Cria o painel do Jmol
            JmolAdapter adapter = new SmarterJmolAdapter();
            viewer = JmolViewer.allocateViewer(jmolPanel, adapter);
            viewer.evalString("set antialiasDisplay true");
            viewer.evalString("set autoBond true");
            // Guarda referência ao container
            jmolPanel = displayPanel;

            // 2. Cria o painel de botões na parte de baixo
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton saveButton = new JButton("Save SDF File");
            JButton closeButton = new JButton("Close");
            buttonPanel.add(saveButton);
            buttonPanel.add(closeButton);

            // 3. Adiciona os painéis à janela de diálogo
            add(jmolPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH); 

            // 4. Ações dos botões
            closeButton.addActionListener(e -> dispose()); // dispose() fecha um JDialog

            saveButton.addActionListener(e -> {
                // Reutiliza nosso DownloadWorker para salvar o arquivo!
                saveButton.setEnabled(false); // Desabilita enquanto salva
                DownloadWorker worker = new DownloadWorker(cid);
                // Adiciona uma lógica para reabilitar o botão quando o download terminar
                worker.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
                        saveButton.setEnabled(true);
                    }
                });
                worker.execute();
            });

            // 5. Carrega a molécula em segundo plano
            loadMolecule();

            // 6. Finaliza e exibe a janela
            setLocationRelativeTo(owner);
            setVisible(true);
        }

        private void loadMolecule() {
            // Usa um SwingWorker para carregar a molécula e não travar a interface
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/SDF?record_type=3d";
                    String moleculeData = downloadMoleculeData(url);
                    viewer.openStringInline(moleculeData);

                    Thread.sleep(500); // Pequena pausa para garantir que o Jmol processe os dados

                    // Comandos para renderizar corretamente
                    viewer.evalString("background white");   // Fundo branco
                    viewer.evalString("spin off");           // Desliga rotação automática
                    viewer.evalString("wireframe 0.15");     // Mostra as ligações
                    viewer.evalString("spacefill 20%");      // Mostra os átomos
                    viewer.evalString("color cpk");          // Coloração padrão (CPK)
                    viewer.evalString("zoom 100");           // Ajusta o zoom
                    return null;
                }

                @Override
                protected void done() {
                    // Poderíamos tratar erros aqui se necessário
                    System.out.println("Molécula CID " + cid + " carregada no Jmol.");
                    Timer repaintTimer = new Timer(50, e -> jmolPanel.repaint());
                    repaintTimer.start();
                    
                    // Para o timer após 2 segundos
                    Timer stopTimer = new Timer(2000, e -> repaintTimer.stop());
                    stopTimer.setRepeats(false);
                    stopTimer.start();
                }
            }.execute();
        }
        private String downloadMoleculeData(String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new Exception("Failed to download molecule data");
            }
            return response.body();
        }

    } 

    //Método que exibe os resultados na área de texto
    private void displayResults(List<Molecula> moleculas) {
         this.currentResults.clear();
        this.currentResults.addAll(moleculas);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-15s %-50s\n", "#", "CID", "Formula"));
        sb.append("------------------------------------------------------------------\n");
        int index = 1;
        for (Molecula mol : moleculas) {
            sb.append(String.format("%-5s %-15s %-50s\n", index + ".", mol.cid, mol.formula));
            index++;
        }
        resultsArea.setText(sb.toString()); 
        downloadButton.setEnabled(true);
    }

    // --- PARTE 3: CONEXÃO COM A API --- 

    //Método para autocompleted 
    public List<String> searchSuggestions(String textPartial) throws Exception {
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/compound/" + textPartial + "/JSON"; 
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Autocomplete API failed with status: " + response.statusCode());
        }

        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonArray terms = jsonObject.getAsJsonObject("dictionary_terms").getAsJsonArray("compound");

        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            suggestions.add(terms.get(i).getAsString());
        }
        return suggestions;
    }

    // Método para buscar o CID principal 
    public int findPrincipalCid(String nomeComposto) throws Exception {
        String nameCodify = URLEncoder.encode(nomeComposto, StandardCharsets.UTF_8);
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + nameCodify + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API não encontrou o composto (Status: " + response.statusCode() + ")");
        
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        return jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
    }

    // Método para buscar os CIDs similares 
    public List<Integer> findSimilarCid(int cidPrincipal) throws Exception {
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/fastsimilarity_2d/cid/" + cidPrincipal + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API falhou na busca por similaridade (Status: " + response.statusCode() + ")");
        
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        com.google.gson.JsonArray cidArray = jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID");
        
        List<Integer> listaDeCids = new ArrayList<>();
        int count = Math.min(5, cidArray.size());
        for (int i = 0; i < count; i++) {
            listaDeCids.add(cidArray.get(i).getAsInt());
        }
        return listaDeCids;
    }

    // Método para buscar as propriedades de uma lista de CIDs 
public List<Molecula> findProperties(List<Integer> cids) throws Exception {
    List<Molecula> moleculasCompletas = new ArrayList<>();
    for (Integer cid : cids) {
        // 1. URL SIMPLIFICADA: Pede apenas a MolecularFormula
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/property/MolecularFormula/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonObject jsonPropriedade = gson.fromJson(response.body(), JsonObject.class);
            JsonObject dados = jsonPropriedade.getAsJsonObject("PropertyTable").getAsJsonArray("Properties").get(0).getAsJsonObject();
            
            // 2. EXTRAÇÃO SIMPLIFICADA: Pega apenas a fórmula
            String formula = dados.has("MolecularFormula") ? dados.get("MolecularFormula").getAsString() : "N/A";
            
            // 3. CRIAÇÃO DO OBJETO SIMPLIFICADO: Criamos a molécula só com o cid e a fórmula
            moleculasCompletas.add(new Molecula(cid, formula));
        }
        Thread.sleep(1000); // Pausa de 1s para não sobrecarregar a API
    }
    return moleculasCompletas;
    }

}