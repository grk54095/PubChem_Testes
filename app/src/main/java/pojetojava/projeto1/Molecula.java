/** 
 * This file is part of the PojetoJava project.
 * The code is a simple class to represent a molecule with its CID and formula. 
 * Its main purpose is to provide a structured way to
 * pass molecular data between methods in the PubChemApp class.
 */

package pojetojava.projeto1;

public class Molecula {
    // Atributos da classe
    int cid;
    String formula;
    String nome;

    // Construtor atualizado para receber apenas o CID e a Fórmula
    public Molecula(int cid, String formula, String nome) {
        this.cid = cid;
        this.formula = formula;
        this.nome = nome;
    }

    // Método toString atualizado para exibir apenas o CID e a Fórmula
    @Override
    public String toString() {
        return "CID " + cid + " - Fórmula: " + formula + " - Nome: " + nome;
    }
}
