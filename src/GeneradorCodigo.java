import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Genera un archivo Java <baseName>Out.java desde el árbol de la gramática 'Juan'.
 * - Declara variables (ENTERO -> int, CADENA -> String)
 * - Asignaciones, imprimir, if/else (SI/SINO/FIN), while (MIENTRAS/FIN)
 * - Expresiones aritméticas y comparaciones
 *
 * NOTA: Para '=='/'!=' con Strings, Java usa equals(). Aquí se dejan '=='/'!=' tal cual,
 * asumiendo que cadenas se usan sobre todo en impresión/concatenación.
 * Si quieres equals() para cadenas, lo ajustamos.
 */
public class GeneradorCodigo extends JuanBaseVisitor<String> {

    private static final String IND = "    ";

    // Acumuladores por secciones
    private final StringBuilder decls = new StringBuilder();  // declaraciones (int x; String s;)
    private final StringBuilder body  = new StringBuilder();  // cuerpo (sentencias)

    // Tabla de símbolos mínima: nombre -> tipo ("int" | "String")
    private final Map<String, String> tipos = new LinkedHashMap<>();

    // Metadatos (opcional)
    private final String inputPath;  // se usa si quieres poner comentarios con el origen
    private String className = "JuanOut"; // nombre por defecto (compatible hacia atrás)

    /** Compatibilidad con Main antiguo: new GeneradorCodigo(inputPath) */
    public GeneradorCodigo(String inputPath) {
        this.inputPath = inputPath;
    }

    /** Versión dinámica: new GeneradorCodigo(inputPath, className) */
    public GeneradorCodigo(String inputPath, String className) {
        this.inputPath = inputPath;
        if (className != null && !className.isBlank()) {
            this.className = className;
        }
    }

    /** Punto de entrada para generar el código Java completo */
    public String generar(ParseTree tree) {
        // Visitar el programa para llenar decls/body
        visit(tree);

        StringBuilder sb = new StringBuilder();
        sb.append("import java.util.*;\n");
        if (inputPath != null && !inputPath.isBlank()) {
            sb.append("// Generado a partir de: ").append(inputPath).append("\n");
        }
        sb.append("public class ").append(className).append(" {\n");
        sb.append(IND).append("public static void main(String[] args) {\n");

        // Declaraciones primero
        if (decls.length() > 0) {
            sb.append(decls);
        }

        // Luego el cuerpo
        sb.append(body);

        sb.append(IND).append("}\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ---------- Reglas de alto nivel ----------

    @Override
    public String visitProgram(JuanParser.ProgramContext ctx) {
        for (JuanParser.SentenciaContext s : ctx.sentencia()) {
            visit(s);
        }
        return null;
    }

    @Override
    public String visitSentencia(JuanParser.SentenciaContext ctx) {
        if (ctx.declararVariable() != null) {
            visit(ctx.declararVariable());
        } else if (ctx.asignarValor() != null) {
            body.append(IND).append(visit(ctx.asignarValor())).append(";\n");
        } else if (ctx.imprimirValor() != null) {
            body.append(IND).append(visit(ctx.imprimirValor())).append(";\n");
        } else if (ctx.condicional() != null) {
            body.append(visit(ctx.condicional()));
        } else if (ctx.bucle() != null) {
            body.append(visit(ctx.bucle()));
        }
        return null;
    }

    // ---------- Declaraciones / Asignaciones / Imprimir ----------

    @Override
    public String visitDeclararVariable(JuanParser.DeclararVariableContext ctx) {
        String tipo = visit(ctx.tipoVariable());        // "int" | "String"
        String nombre = ctx.ID().getText();

        // Registrar tipo
        tipos.put(nombre, tipo);

        if (ctx.expresion() != null) {
            String expr = visit(ctx.expresion());
            decls.append(IND).append(tipo).append(" ").append(nombre).append(" = ").append(expr).append(";\n");
        } else {
            // sin inicializador
            String def = tipo.equals("int") ? "0" : "null";
            decls.append(IND).append(tipo).append(" ").append(nombre).append(" = ").append(def).append(";\n");
        }
        return null;
    }

    @Override
    public String visitAsignarValor(JuanParser.AsignarValorContext ctx) {
        String id = ctx.ID().getText();
        String expr = visit(ctx.expresion());
        return id + " = " + expr;
    }

    @Override
    public String visitImprimirValor(JuanParser.ImprimirValorContext ctx) {
        String expr = visit(ctx.expresion());
        return "System.out.println(" + expr + ")";
    }

    // ---------- Control de flujo ----------

    @Override
    public String visitCondicional(JuanParser.CondicionalContext ctx) {
        StringBuilder sb = new StringBuilder();
        String cond = visit(ctx.expresion());
        sb.append(IND).append("if (").append(cond).append(") {\n");
        sb.append(visit(ctx.bloque(0)));
        sb.append(IND).append("}\n");
        if (ctx.SINO() != null) {
            sb.append(IND).append("else {\n");
            sb.append(visit(ctx.bloque(1)));
            sb.append(IND).append("}\n");
        }
        return sb.toString();
    }

    @Override
    public String visitBucle(JuanParser.BucleContext ctx) {
        StringBuilder sb = new StringBuilder();
        String cond = visit(ctx.expresion());
        sb.append(IND).append("while (").append(cond).append(") {\n");
        sb.append(visit(ctx.bloque()));
        sb.append(IND).append("}\n");
        return sb.toString();
    }

    @Override
    public String visitBloque(JuanParser.BloqueContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (JuanParser.SentenciaContext s : ctx.sentencia()) {
            int baseLen = body.length();
            visit(s);
            String added = body.substring(baseLen);
            // Re-indentar +1 nivel (dentro de if/while)
            String reind = Arrays.stream(added.split("\n", -1))
                    .map(line -> line.isEmpty() ? line : IND + line)
                    .collect(Collectors.joining("\n"));
            // “Consumir” lo agregado en body y moverlo localmente al bloque
            body.setLength(baseLen);
            sb.append(reind);
            if (!reind.endsWith("\n")) sb.append("\n");
        }
        return sb.toString();
    }

    // ---------- Expresiones ----------

    @Override
    public String visitComparacionOp(JuanParser.ComparacionOpContext ctx) {
        String l = visit(ctx.expresion(0));
        String r = visit(ctx.expresion(1));
        String op = ctx.op.getText();
        return "(" + l + " " + op + " " + r + ")";
    }

    @Override
    public String visitSumaOp(JuanParser.SumaOpContext ctx) {
        String l = visit(ctx.expresion(0));
        String r = visit(ctx.expresion(1));
        String op = ctx.op.getText();
        return "(" + l + " " + op + " " + r + ")";
    }

    @Override
    public String visitMultiplicacionOp(JuanParser.MultiplicacionOpContext ctx) {
        String l = visit(ctx.expresion(0));
        String r = visit(ctx.expresion(1));
        String op = ctx.op.getText();
        return "(" + l + " " + op + " " + r + ")";
    }

    @Override
    public String visitExpresionParentesis(JuanParser.ExpresionParentesisContext ctx) {
        return "(" + visit(ctx.expresion()) + ")";
    }

    @Override
    public String visitExpresionLiteral(JuanParser.ExpresionLiteralContext ctx) {
        return visit(ctx.literal());
    }

    @Override
    public String visitIdExpresion(JuanParser.IdExpresionContext ctx) {
        return ctx.ID().getText();
    }

    @Override
    public String visitLiteral(JuanParser.LiteralContext ctx) {
        if (ctx.INT() != null) return ctx.INT().getText();
        return ctx.STRING().getText(); // ya viene con comillas
    }

    // ---------- tipos ----------

    @Override
    public String visitTipoVariable(JuanParser.TipoVariableContext ctx) {
        if (ctx.ENTERO() != null) return "int";
        if (ctx.CADENA() != null) return "String";
        throw new RuntimeException("Tipo no soportado: " + ctx.getText());
    }
}
