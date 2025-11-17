/**
 * This file is part of the PojetoJava project.
 * This version uses an editable JComboBox to provide a robust suggestion list,
 * with fixes for auto-showing the popup and preventing event loops on selection.
 */
package pojetojava.prototipos;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

public class Autocomp {

    // --- Componentes da Interface ---
    private JFrame frame;
    private JComboBox<String> searchComboBox;
    private JButton searchButton;
    private JTextArea resultsArea;
    private JTextField downloadField;
    private JButton downloadButton;
    private JLabel downloadLabel;

    // --- Lógica do Autocomplete ---
    private Timer debounceTimer;
    private SwingWorker<List<String>, Void> activeWorker;
    private boolean isUpdatingInternally = false; // <<< MUDANÇA: Flag para controlar o loop de eventos

    // --- Ferramentas da Aplicação ---
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private List<Molecula> currentResults = new ArrayList<>();

    public Autocomp() {
        createUI();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Autocomp());
    }

    private void createUI() {
        frame = new JFrame("PubChem Search and Download");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        topPanel.add(new JLabel("Molecule Name:"));

        searchComboBox = new JComboBox<>(new Vector<>());
        searchComboBox.setEditable(true);
        searchComboBox.setPreferredSize(new Dimension(280, 25));

        JTextComponent editor = (JTextComponent) searchComboBox.getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                if (isUpdatingInternally) return; // Se a mudança foi interna, não faz nada
                debounceTimer.restart();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                if (isUpdatingInternally) return;
                debounceTimer.restart();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        
        // <<< MUDANÇA: Adiciona um ActionListener para tratar a seleção de um item
        searchComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Este evento é acionado quando o usuário SELECIONA um item
                if (e.getActionCommand().equals("comboBoxEdited")) {
                    // Ignora eventos que são apenas de edição de texto
                    return;
                }
                // Um item foi selecionado da lista, então fechamos o popup
                searchComboBox.hidePopup();
            }
        });

        topPanel.add(searchComboBox);
        searchButton = new JButton("Search");
        topPanel.add(searchButton);

        debounceTimer = new Timer(700, e -> startSuggestionWorker());
        debounceTimer.setRepeats(false);

        // --- Resto da UI ---
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(resultsArea);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        downloadLabel = new JLabel("Enter item # to download:");
        bottomPanel.add(downloadLabel);
        downloadField = new JTextField(5);
        bottomPanel.add(downloadField);
        downloadButton = new JButton("Download SDF");
        bottomPanel.add(downloadButton);
        downloadButton.setEnabled(false);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.add(mainPanel);

        searchButton.addActionListener(e -> startFullSearchWorker());
        downloadButton.addActionListener(e -> startDownloadWorker());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void startSuggestionWorker() {
        String text = ((JTextComponent) searchComboBox.getEditor().getEditorComponent()).getText();

        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
        }

        if (text.trim().length() < 3) {
            searchComboBox.hidePopup();
            return;
        }

        activeWorker = new SuggestionWorker(text);
        activeWorker.execute();
    }
    
    private void startFullSearchWorker() {
        String compoundName = (String) searchComboBox.getSelectedItem();
        if (compoundName == null || compoundName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter a molecule name.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        searchComboBox.hidePopup();
        resultsArea.setText("Searching for '" + compoundName + "', please wait...");
        searchButton.setEnabled(false);
        downloadButton.setEnabled(false);
        new PubChemSearchWorker(compoundName).execute();
    }
    
    private void startDownloadWorker() {
        // (Este método permanece igual)
        String choiceStr = downloadField.getText();
        int choiceInt;
        try {
            choiceInt = Integer.parseInt(choiceStr);
            if (choiceInt < 1 || choiceInt > currentResults.size()) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid number from the list.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int cidToDownload = currentResults.get(choiceInt - 1).cid;
        searchButton.setEnabled(false);
        downloadButton.setEnabled(false);
        new DownloadWorker(cidToDownload).execute();
    }

    // --- Trabalhador para buscar SUGESTÕES ---
    private class SuggestionWorker extends SwingWorker<List<String>, Void> {
        private final String partialName;
        public SuggestionWorker(String partialName) { this.partialName = partialName; }

        @Override
        protected List<String> doInBackground() throws Exception {
            return Autocomp.this.searchSuggestions(partialName);
        }

        @Override
        protected void done() {
            try {
                if (isCancelled()) return;
                List<String> suggestions = get();
                
                String currentText = ((JTextComponent) searchComboBox.getEditor().getEditorComponent()).getText();
                
                // <<< MUDANÇA: Ativa a flag para evitar que o DocumentListener seja acionado
                isUpdatingInternally = true;
                
                searchComboBox.removeAllItems();
                if (!suggestions.isEmpty()) {
                    suggestions.forEach(searchComboBox::addItem);
                }
                
                searchComboBox.getEditor().setItem(currentText);
                
                // <<< MUDANÇA: Desativa a flag após a atualização
                isUpdatingInternally = false;

                // Se temos sugestões e o popup não está visível, mostre-o
                if (!suggestions.isEmpty() && !searchComboBox.isPopupVisible()) {
                    searchComboBox.showPopup();
                } else if (suggestions.isEmpty()) {
                    searchComboBox.hidePopup();
                }

            } catch (Exception e) {
                searchComboBox.hidePopup();
                System.err.println("Suggestion error: " + e.getMessage());
            }
        }
    }

    // --- Outras classes Worker e métodos de API (permanecem iguais) ---
    private class PubChemSearchWorker extends SwingWorker<List<Molecula>, Void> {
        private final String selectedName;
        public PubChemSearchWorker(String selectedName) { this.selectedName = selectedName; }
        @Override
        protected List<Molecula> doInBackground() throws Exception {
            int principalCid = Autocomp.this.findPrincipalCid(selectedName);
            List<Integer> similarCids = Autocomp.this.findSimilarCids(principalCid);
            if (similarCids.isEmpty()) throw new Exception("No similar molecules found for '" + selectedName + "'.");
            return Autocomp.this.fetchProperties(similarCids);
        }
        @Override
        protected void done() {
            try {
                List<Molecula> moleculas = get();
                if (!moleculas.isEmpty()) displayResults(moleculas);
                else resultsArea.setText("No results found.");
            } catch (Exception e) {
                String error = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                resultsArea.setText("An error occurred: " + error);
                JOptionPane.showMessageDialog(frame, "Error during search: " + error, "API Error", JOptionPane.ERROR_MESSAGE);
            }
            searchButton.setEnabled(true);
        }
    }

    private class DownloadWorker extends SwingWorker<Path, Void> {
        private final int cidToDownload;
        public DownloadWorker(int cid) { this.cidToDownload = cid; }
        @Override
        protected Path doInBackground() throws Exception {
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cidToDownload + "/SDF?record_type=3d";
            Path filePath = Paths.get("molecule_cid_" + cidToDownload + ".sdf");
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(filePath));
            if (response.statusCode() != 200) throw new Exception("Download failed with status: " + response.statusCode());
            return response.body();
        }
        @Override
        protected void done() {
            try {
                Path file = get();
                JOptionPane.showMessageDialog(frame, "Download complete!\nFile saved as: " + file.getFileName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                String error = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
                JOptionPane.showMessageDialog(frame, "Download failed: " + error, "Error", JOptionPane.ERROR_MESSAGE);
            }
            searchButton.setEnabled(true);
            downloadButton.setEnabled(true);
        }
    }
    
    private void displayResults(List<Molecula> moleculas) {
        currentResults.clear();
        currentResults.addAll(moleculas);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-5s %-15s %-50s\n", "#", "CID", "Formula"));
        sb.append("-".repeat(70) + "\n");
        int index = 1;
        for (Molecula mol : moleculas) {
            sb.append(String.format("%-5s %-15s %-50s\n", index + ".", mol.cid, mol.formula));
            index++;
        }
        resultsArea.setText(sb.toString());
        downloadButton.setEnabled(true);
        downloadLabel.setText("Enter item # to download (1-" + currentResults.size() + "):");
    }
    
    public List<String> searchSuggestions(String textPartial) throws Exception {
        String encodedText = URLEncoder.encode(textPartial, StandardCharsets.UTF_8);
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/compound/" + encodedText + "/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API failed: " + response.statusCode());
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonArray terms = jsonObject.getAsJsonObject("dictionary_terms").getAsJsonArray("compound");
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < Math.min(10, terms.size()); i++) {
            suggestions.add(terms.get(i).getAsString());
        }
        return suggestions;
    }

    public int findPrincipalCid(String compoundName) throws Exception {
        String encodedName = URLEncoder.encode(compoundName, StandardCharsets.UTF_8);
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" + encodedName + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API could not find compound '" + compoundName + "'.");
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        return jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID").get(0).getAsInt();
    }

    public List<Integer> findSimilarCids(int principalCid) throws Exception {
        String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/fastsimilarity_2d/cid/" + principalCid + "/cids/JSON";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("API failed similarity search.");
        JsonObject jsonObject = gson.fromJson(response.body(), JsonObject.class);
        JsonArray cidArray = jsonObject.getAsJsonObject("IdentifierList").getAsJsonArray("CID");
        List<Integer> cidsList = new ArrayList<>();
        for (int i = 0; i < Math.min(5, cidArray.size()); i++) {
            cidsList.add(cidArray.get(i).getAsInt());
        }
        return cidsList;
    }

    public List<Molecula> fetchProperties(List<Integer> cids) throws Exception {
        List<Molecula> moleculasCompletas = new ArrayList<>();
        for (Integer cid : cids) {
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/property/MolecularFormula/JSON";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonProp = gson.fromJson(response.body(), JsonObject.class);
                JsonObject data = jsonProp.getAsJsonObject("PropertyTable").getAsJsonArray("Properties").get(0).getAsJsonObject();
                String formula = data.has("MolecularFormula") ? data.get("MolecularFormula").getAsString() : "N/A";
                moleculasCompletas.add(new pojetojava.prototipos.Molecula(cid, formula));
            }
            Thread.sleep(250);
        }
        return moleculasCompletas;
    }
}