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
// Importações para a lógica da aplicação
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
// Importações para o download de arquivo
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


public class PubChemApp {

    // --- Componentes da Interface (a parte visual) ---
    private JFrame frame;
    private JTextField searchField;
    private JButton searchButton;
    private JTextArea resultsArea;
    private JTextField downloadField;
    private JButton downloadButton;
    private JLabel downloadLabel;

    // --- "Memória" e Ferramentas da Aplicação ---
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private List<Molecula> currentResults = new ArrayList<>();

    public PubChemApp() {
        createUI();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PubChemApp());
    }

    // Monta a janela e todos os seus componentes
    private void createUI() {
        frame = new JFrame("PubChem Search and Download");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Painel superior para a busca
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        topPanel.add(new JLabel("Molecule Name:"));
        searchField = new JTextField(30);
        topPanel.add(searchField);
        searchButton = new JButton("Search");
        topPanel.add(searchButton);

        // Área de texto central com rolagem
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(resultsArea);

        // Painel inferior para a função de download
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        downloadLabel = new JLabel("Enter item # to download:");
        bottomPanel.add(downloadLabel);
        downloadField = new JTextField(5);
        bottomPanel.add(downloadField);
        downloadButton = new JButton("Download SDF");
        bottomPanel.add(downloadButton);
        downloadButton.setEnabled(false); // Começa desabilitado

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.add(mainPanel);

        // Conecta os botões às suas ações
        searchButton.addActionListener(e -> startSearchWorker());
        downloadButton.addActionListener(e -> startDownloadWorker());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Método que inicia o processo de BUSCA em segundo plano
    private void startSearchWorker() {
        String compoundName = searchField.getText();
        if (compoundName == null || compoundName.trim().length() < 3) {
            JOptionPane.showMessageDialog(frame, "Please enter at least 3 characters.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resultsArea.setText("Searching for suggestions, please wait...");
        searchButton.setEnabled(false);
        downloadButton.setEnabled(false);
        PubChemSearchWorker worker = new PubChemSearchWorker(compoundName);
        worker.execute();
    }
    
    // Método que inicia o processo de DOWNLOAD em segundo plano
    private void startDownloadWorker() {
        String choiceStr = downloadField.getText();
        int choiceInt;

        try {
            choiceInt = Integer.parseInt(choiceStr);
            if (choiceInt < 1 || choiceInt > currentResults.size()) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid number from the list.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int cidToDownload = currentResults.get(choiceInt - 1).cid;

        searchButton.setEnabled(false);
        downloadButton.setEnabled(false);

        DownloadWorker worker = new DownloadWorker(cidToDownload);
        worker.execute();
    }

    // --- Trabalhador de segundo plano para a BUSCA ---
    private class PubChemSearchWorker extends SwingWorker<List<Molecula>, Void> {
        private final String partialName;
        public PubChemSearchWorker(String partialName) { this.partialName = partialName; }
        @Override
        protected List<Molecula> doInBackground() throws Exception {
            // Etapa 1: Busca as sugestões de nome
            List<String> suggestions = PubChemApp.this.searchSuggestions(partialName);
            if (suggestions.isEmpty()) { throw new Exception("No suggestions found for '" + partialName + "'."); }
            
            // Etapa 2: Mostra o pop-up (JOptionPane) para o usuário escolher
            StringBuilder suggestionText = new StringBuilder("Please choose one of the following:\n\n");
            for (int i = 0; i < suggestions.size(); i++) { suggestionText.append((i + 1)).append(". ").append(suggestions.get(i)).append("\n"); }
            String choiceStr = JOptionPane.showInputDialog(frame, suggestionText.toString(), "Select a Molecule", JOptionPane.QUESTION_MESSAGE);
            
            if (choiceStr == null) return new ArrayList<>(); // Usuário clicou em "Cancelar"
            
            int choiceInt = Integer.parseInt(choiceStr);
            if (choiceInt < 1 || choiceInt > suggestions.size()) { throw new Exception("Invalid selection."); }
            String selectedName = suggestions.get(choiceInt - 1);

            // Etapa 3: Continua o fluxo com o nome escolhido
            int principalCid = PubChemApp.this.findPrincipalCid(selectedName);
            List<Integer> similarCids = PubChemApp.this.findSimilarCids(principalCid);
            return PubChemApp.this.fetchProperties(similarCids);
        }
        @Override
        protected void done() {
            try {
                List<Molecula> moleculas = get();
                if (!moleculas.isEmpty()) {
                    displayResults(moleculas);
                } else {
                    resultsArea.setText("Operation cancelled or no results found.");
                }
            } catch (Exception e) {
                String errorMessage = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                resultsArea.setText("An error occurred: " + errorMessage);
                JOptionPane.showMessageDialog(frame, "Error during search: " + errorMessage, "API Error", JOptionPane.ERROR_MESSAGE);
            }
            searchButton.setEnabled(true);
        }
    }

    // --- Trabalhador de segundo plano para o DOWNLOAD ---
    private class DownloadWorker extends SwingWorker<Path, Void> {
        private final int cidToDownload;
        public DownloadWorker(int cid) { this.cidToDownload = cid; }
        @Override
        protected Path doInBackground() throws Exception {
            String downloadUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cidToDownload + "/SDF?record_type=3d";
            Path filePath = Paths.get("molecule_cid_" + cidToDownload + ".sdf");
            HttpRequest requestDownload = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build();
            HttpResponse<Path> responseDownload = client.send(requestDownload, HttpResponse.BodyHandlers.ofFile(filePath));
            if (responseDownload.statusCode() != 200) { throw new Exception("Download failed with status code: " + responseDownload.statusCode()); }
            return responseDownload.body();
        }
        @Override
        protected void done() {
            try {
                Path downloadedFile = get();
                JOptionPane.showMessageDialog(frame, "Download complete!\nFile saved as: " + downloadedFile.getFileName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Download failed: " + e.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            searchButton.setEnabled(true);
            downloadButton.setEnabled(true);
        }
    }

    // Método que atualiza a área de texto com os resultados
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
        downloadLabel.setText("Enter item # to download (1-" + currentResults.size() + "):");
    }
    
    // --- Métodos que conversam com a API do PubChem ---
    public List<String> searchSuggestions(String textPartial) throws Exception {
        String encodedText = URLEncoder.encode(textPartial, StandardCharsets.UTF_8);
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/compound/" + encodedText + "/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("Autocomplete API failed: " + response.statusCode());
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonArray terms = jsonObject.getAsJsonObject("dictionary_terms").getAsJsonArray("compound");
        List<String> suggestions = new ArrayList<>();
        int limit = Math.min(10, terms.size());
        for (int i = 0; i < limit; i++) { suggestions.add(terms.get(i).getAsString()); }
        return suggestions;
    }

    public int findPrincipalCid(String compoundName) throws Exception {
        String encodedName = URLEncoder.encode(compoundName, StandardCharsets.UTF_8);
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + encodedName + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API could not find compound: " + response.statusCode());
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        return jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
    }

    public List<Integer> findSimilarCids(int principalCid) throws Exception {
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/fastsimilarity_2d/cid/" + principalCid + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API failed similarity search: " + response.statusCode());
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonArray cidArray = jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID");
        List<Integer> cidsList = new ArrayList<>();
        int count = Math.min(5, cidArray.size());
        for (int i = 0; i < count; i++) { cidsList.add(cidArray.get(i).getAsInt()); }
        return cidsList;
    }

    public List<Molecula> fetchProperties(List<Integer> cids) throws Exception {
        List<Molecula> moleculasCompletas = new ArrayList<>();
        for (Integer cid : cids) {
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/property/MolecularFormula/JSON";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonPropriedade = gson.fromJson(response.body(), JsonObject.class);
                JsonObject dados = jsonPropriedade.getAsJsonObject("PropertyTable").getAsJsonArray("Properties").get(0).getAsJsonObject();
                String formula = dados.has("MolecularFormula") ? dados.get("MolecularFormula").getAsString() : "N/A";
                moleculasCompletas.add(new Molecula(cid, formula));
            }
            Thread.sleep(250);
        }
        return moleculasCompletas;
    }
}