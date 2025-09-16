public class variaveis {
    public static void main(String[] args) {
        int idade = 30; // inteiro
        double salario = 4500.99; // número com ponto flutuante
        String nome = "Gabriel"; // texto
        boolean estaChovendo = false; // verdadeiro ou falso 
        var estado = "São Paulo"; // o compilador infere o tipo

        System.out.println("Idade: " + idade);
        System.out.println("Salário: " + salario);
        System.out.println("Nome: " + nome);
        System.out.println("Está chovendo: " + estaChovendo);
        System.out.println("Estado: " + estado);
    }
}
