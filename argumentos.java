public class argumentos {
    public static void main(String[] args) {
        System.out.println("Analisando os argumentos recebidos...");

        // A propriedade .length nos diz quantos argumentos foram passados.
        if (args.length == 0) {
            System.out.println("Nenhum argumento foi fornecido!");
        } else {
            System.out.println("Total de argumentos: " + args.length);

            // Um loop 'for' para passar por cada argumento na lista e imprimi-lo.
            for (int i = 0; i < args.length; i++) {
                // args[i] acessa o argumento na posição 'i' da lista.
                System.out.println("Argumento #" + (i + 1) + ": " + args[i]);
            }
        }
    }
}
     // String[] é um array/lista de Strings
        // args é o nome do array/lista
        
    

