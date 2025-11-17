/** 
 * This file is part of the PojetoJava project.
 * The code is a simple class to represent a molecule with its CID and formula. 
 * Its main purpose is to provide a structured way to
 * pass molecular data between methods in the PubChemApp class.
 */



package pojetojava.prototipos;

public class Molecula { 
    // Atributos da classe
    int cid;
    String formula;

    // Construtor atualizado para receber apenas o CID e a Fórmula
    public Molecula(int cid, String formula) {
        this.cid = cid;
        this.formula = formula;
    }

    // Método toString atualizado para exibir apenas o CID e a Fórmula
    @Override
    public String toString() {
        return "CID " + cid + " - Fórmula: " + formula;
    }
}
    

