import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.BailErrorStrategy;

import java.nio.file.*;
import java.io.*;

public class Main {

    // Listener inline para mensajes de error sintáctico claros (sin archivo extra)
    private static final BaseErrorListener PRETTY = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            System.err.printf("[Sintaxis] línea %d:%d %s%n", line, charPositionInLine, msg);
        }
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java -cp \"tools\\antlr-4.13.2-complete.jar;gen;out\" Main <archivo.remi>");
            System.exit(1);
        }

        String inputPath = args[0];
        String source = Files.readString(Paths.get(inputPath));

        // === Fase 1: Léxico
        CharStream cs = CharStreams.fromString(source);
        JuanLexer lexer = new JuanLexer(cs);

        // Listener de errores léxicos (mensajes claros)
        lexer.removeErrorListeners();
        lexer.addErrorListener(PRETTY);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // === Fase 2: Parser
        JuanParser parser = new JuanParser(tokens);

        // Listener de errores sintácticos + estrategia "bail" (fail-fast)
        parser.removeErrorListeners();
        parser.addErrorListener(PRETTY);
        parser.setErrorHandler(new BailErrorStrategy());

        ParseTree tree;
        try {
            tree = parser.program();
        } catch (ParseCancellationException ex) {
            // Mensaje amigable cuando el parser se “baja” en el primer error
            Token off = parser.getCurrentToken();
            int line = off.getLine();
            int col  = off.getCharPositionInLine();
            String text = off.getText();
            System.err.printf("[Sintaxis] línea %d:%d token inesperado %s%n", line, col, quoteLexeme(text));
            System.exit(1);
            return;
        }

        // === Fase 3: Semántica
        Semantico sem = new Semantico();
        sem.visit(tree);

        if (sem.tieneErrores()) {
            System.err.println("Errores semánticos:");
            for (String e : sem.getErrores()) {
                System.err.println("  - " + e);
            }
            System.exit(2);
        }

        if (!sem.getAdvertencias().isEmpty()) {
            System.out.println("Advertencias:");
            for (String w : sem.getAdvertencias()) {
                System.out.println("  - " + w);
            }
        }

        // === Fase 4: Generación de código
        String outDir = "out";
        Files.createDirectories(Paths.get(outDir));
        String outFile = outDir + File.separator + "JuanOut.java";

        GeneradorCodigo gen = new GeneradorCodigo(inputPath);
        String javaCode = gen.generar(tree);
        Files.writeString(Paths.get(outFile), javaCode);

        System.out.println("[OK] Generado: " + outFile);
        System.out.println("Compila con:  javac -d out out" + File.separator + "JuanOut.java");
        System.out.println("Ejecuta con:  java -cp out JuanOut");
    }

    private static String quoteLexeme(String s) {
        if (s == null) return "<null>";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\r", "\\r")
                       .replace("\n", "\\n") + "\"";
    }
}
