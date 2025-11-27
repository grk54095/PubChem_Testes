/**
 * This file is part of the PojetoJava project.
 * This version uses an editable JComboBox for suggestions and integrates
 * a Jmol 3D viewer in a separate dialog window.
 */
package pojetojava.projeto1;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
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
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.api.JmolViewer; 
 

public class PubChemApp {

    // --- Componentes da Interface ---
    private JFrame frame;
    private JComboBox<String> searchComboBox;
    private JButton searchButton;
    private JTextArea resultsArea;
    private JTextField downloadField;
    private JButton downloadButton;
    private JLabel downloadLabel;
    private JButton view3DButton;

    // --- Lógica do Autocomplete ---
    private Timer debounceTimer;
    private SwingWorker<List<String>, Void> activeWorker;
    private boolean isUpdatingInternally = false;

    // --- Ferramentas da Aplicação ---
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private List<Molecula> currentResults = new ArrayList<>();

    public PubChemApp() {
        createUI();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PubChemApp());
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
                if (isUpdatingInternally) return;
                debounceTimer.restart();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                if (isUpdatingInternally) return;
                debounceTimer.restart();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });
        
        searchComboBox.addActionListener(e -> {
            if ("comboBoxEdited".equals(e.getActionCommand())) return;
            searchComboBox.hidePopup();
        });

        topPanel.add(searchComboBox);
        searchButton = new JButton("Search");
        topPanel.add(searchButton);

        debounceTimer = new Timer(700, e -> startSuggestionWorker());
        debounceTimer.setRepeats(false);

        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(resultsArea);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        downloadLabel = new JLabel("Enter item # for action:");
        bottomPanel.add(downloadLabel);
        downloadField = new JTextField(5);
        bottomPanel.add(downloadField);
        downloadButton = new JButton("Download SDF");
        bottomPanel.add(downloadButton);
        downloadButton.setEnabled(false);

        view3DButton = new JButton("Visualizar 3D");
        bottomPanel.add(view3DButton);
        view3DButton.setEnabled(false);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.add(mainPanel);

        searchButton.addActionListener(e -> startFullSearchWorker());
        downloadButton.addActionListener(e -> startDownloadWorker());
        // Adiciona a ação para o novo botão de visualização
        view3DButton.addActionListener(e -> startJmolViewer());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    // --- MÉTODOS DE AÇÃO ---

    private void startSuggestionWorker() {
        String text = ((JTextComponent) searchComboBox.getEditor().getEditorComponent()).getText();
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
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
        view3DButton.setEnabled(false);
        new PubChemSearchWorker(compoundName).execute();
    }
    
    private void startDownloadWorker() {
        try {
            int choiceInt = Integer.parseInt(downloadField.getText());
            if (choiceInt < 1 || choiceInt > currentResults.size()) throw new NumberFormatException();
            int cidToDownload = currentResults.get(choiceInt - 1).cid;
            new DownloadWorker(cidToDownload).execute();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid number from the list.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startJmolViewer() {
        try {
            int choiceInt = Integer.parseInt(downloadField.getText());
            if (choiceInt < 1 || choiceInt > currentResults.size()) throw new NumberFormatException();
            int cidToView = currentResults.get(choiceInt - 1).cid;
            new VisualizationDialog(frame, cidToView).setVisible(true);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Invalid input. Please enter a valid number from the list.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- CLASSES INTERNAS (WORKERS) ---

    private class SuggestionWorker extends SwingWorker<List<String>, Void> {
        private final String partialName;
        public SuggestionWorker(String partialName) { this.partialName = partialName; }

        @Override
        protected List<String> doInBackground() throws Exception {
            return PubChemApp.this.searchSuggestions(partialName);
        }

        @Override
        protected void done() {
            try {
                if (isCancelled()) return;
                List<String> suggestions = get();
                String currentText = ((JTextComponent) searchComboBox.getEditor().getEditorComponent()).getText();
                
                isUpdatingInternally = true;
                searchComboBox.removeAllItems();
                if (!suggestions.isEmpty()) suggestions.forEach(searchComboBox::addItem);
                searchComboBox.getEditor().setItem(currentText);
                isUpdatingInternally = false;

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

    private class PubChemSearchWorker extends SwingWorker<List<Molecula>, Void> {
        private final String selectedName;
        public PubChemSearchWorker(String selectedName) { this.selectedName = selectedName; }
        
        @Override
        protected List<Molecula> doInBackground() throws Exception {
            int principalCid = PubChemApp.this.findPrincipalCid(selectedName);
            List<Integer> similarCids = PubChemApp.this.findSimilarCids(principalCid);
            if (similarCids.isEmpty()) throw new Exception("No similar molecules found for '" + selectedName + "'.");
            return PubChemApp.this.fetchProperties(similarCids);
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
        }
    }

    // <<<<<<<<<<< MUDANÇA 2: A SUA CLASSE DE PAINEL VITORIOSA >>>>>>>>>>>

    private class JMolPanel extends JPanel {
        JmolViewer viewer;
        final Dimension currentSize = new Dimension();

        public JMolPanel() {
            viewer = JmolViewer.allocateViewer(this, new SmarterJmolAdapter());
        }

        @Override
        @SuppressWarnings("deprecation") 
        public void paint(Graphics g) {
            // A "mágica" está aqui:
            viewer.setScreenDimension(getWidth(), getHeight());
            Rectangle rectClip = new Rectangle();
            g.getClipBounds(rectClip);
            viewer.renderScreenImage(g, currentSize, rectClip);
        }
    }

    // --- NOVA CLASSE INTERNA PARA A JANELA DO JMOL ---
    private class VisualizationDialog extends JDialog {
        private final int cid;
        private JMolPanel jmolPanel; // <-- MUDANÇA: Usa a nossa classe JMolPanel

        public VisualizationDialog(JFrame owner, int cidToVisualize) {
            super(owner, "3D Visualization - CID: " + cidToVisualize, true);
            this.cid = cidToVisualize;
            setSize(600, 600);
            setLayout(new BorderLayout());

            // MUDANÇA: Cria uma instância do *nosso* painel
            jmolPanel = new JMolPanel(); 
            jmolPanel.setPreferredSize(new Dimension(500, 500));

            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton saveButton = new JButton("Save SDF File");
            JButton closeButton = new JButton("Close");
            buttonPanel.add(saveButton);
            buttonPanel.add(closeButton);

            add(jmolPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);

            closeButton.addActionListener(e -> dispose());
            
            saveButton.addActionListener(e -> {
                saveButton.setEnabled(false);
                DownloadWorker worker = new DownloadWorker(cid);
                worker.addPropertyChangeListener(evt -> {
                    if ("state".equals(evt.getPropertyName()) && SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
                        saveButton.setEnabled(true);
                    }
                });
                worker.execute();
            });

            loadMolecule();
            setLocationRelativeTo(owner);
            setVisible(true);
        }

        private void loadMolecule() {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    // MUDANÇA: Acessa o 'viewer' que está dentro do nosso painel
                    JmolViewer viewer = jmolPanel.viewer; 
                    String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/" + cid + "/SDF?record_type=3d";
                    viewer.evalString("load " + url);
                    
                    // Adicionando seus comandos de renderização
                    viewer.evalString("background black");
                    viewer.evalString("select all");
                    viewer.evalString("cpk");
                    viewer.evalString("wireframe 0.15");
                    viewer.evalString("spacefill 23%");
                    viewer.evalString("zoom 150");
                    viewer.evalString("rotate best");
                    viewer.evalString("spin on");
                    return null;
                }
                
                @Override
                protected void done() {
                    // O repaint() ainda é bom para garantir a primeira renderização
                    jmolPanel.repaint();
                    System.out.println("Molecule CID " + cid + " loaded in Jmol.");
                }
            }.execute();
        }
    }
    
    // --- MÉTODOS DE UI E API ---
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
        view3DButton.setEnabled(true);
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
                // Lembre-se que sua classe Molecula precisa estar no lugar certo
                moleculasCompletas.add(new pojetojava.projeto1.Molecula(cid, formula));
            }
            Thread.sleep(250);
        }
        return moleculasCompletas;
    }
}