import org.antlr.v4.runtime.*;
import java.nio.file.*;
import java.io.*;

public class Lexico {

    // Listener inline para errores léxicos claros (sin archivo extra)
    private static final BaseErrorListener PRETTY = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            System.err.printf("[Léxico] línea %d:%d %s%n", line, charPositionInLine, msg);
        }
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java -cp \"tools\\antlr-4.13.2-complete.jar;gen;out\" Lexico <archivo.remi>");
            System.exit(1);
        }

        String inputPath = args[0];
        String source = Files.readString(Paths.get(inputPath));

        CharStream cs = CharStreams.fromString(source);
        JuanLexer lexer = new JuanLexer(cs);
        Vocabulary vocab = lexer.getVocabulary();

        // Activa mensajes claros de error léxico
        lexer.removeErrorListeners();
        lexer.addErrorListener(PRETTY);

        int i = 0;
        while (true) {
            Token t = lexer.nextToken();
            if (t.getType() == Token.EOF) {
                System.out.printf("# %d EOF <EOF>%n", i);
                break;
            }
            String name = vocab.getSymbolicName(t.getType());
            if (name == null) name = vocab.getDisplayName(t.getType());
            name = mostrarNombre(name);

            int line = t.getLine();
            int col = t.getCharPositionInLine() + 1;

            System.out.printf("# %d %-12s %-20s (línea:%d, col:%d)%n",
                    i, name, quoteLexeme(t.getText()), line, col);
            i++;
        }

        // Nota: WS y COMMENT no aparecen porque la gramática los salta con `-> skip`.
    }

    private static String mostrarNombre(String name) {
        if (name == null) return null;
        switch (name) {
            case "SI":       return "CONDICIONAL_SI";
            case "SINO":     return "CONDICIONAL_NO";
            case "ENTERO":
            case "CADENA":   return "TIPO_VARIABLE";
            default:         return name;
        }
    }

    private static String quoteLexeme(String s) {
        if (s == null) return "<null>";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\r", "\\r")
                       .replace("\n", "\\n") + "\"";
    }
}
