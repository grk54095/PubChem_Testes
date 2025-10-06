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

public class PubChemApp {

    // --- Parte 1: Componentes da Interface  ---
    private JFrame frame;
    private JTextField searchField;
    private JButton searchButton;
    private JTextArea resultsArea;
    private JScrollPane scrollPane; // Para adicionar uma barra de rolagem à área de texto

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

        // 4. Adicionar os componentes aos painéis
        topPanel.add(new JLabel("Molecule Name:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);

        // 5. Adicionar os painéis à janela
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        frame.add(mainPanel);

        // 6. Método que inicia SwingWorker para busca assíncrona
        searchButton.addActionListener(e -> startSearchWorker());

        // 7. Tornar a janela visível 
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
           StringBuilder suggestionText = new StringBuilder("Please choose a suggestion:\n");
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

    //Método que exibe os resultados na área de texto
    private void displayResults(List<Molecula> moleculas) {
       StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-15s %-50s\n", "#", "CID", "Formula"));
        sb.append("------------------------------------------------------------------\n");
        int index = 1;
        for (Molecula mol : moleculas) {
            sb.append(String.format("%-5s %-15s %-50s\n", index + ".", mol.cid, mol.formula));
            index++;
        }
        resultsArea.setText(sb.toString());
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
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + nomeComposto + "/cids/JSON";
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