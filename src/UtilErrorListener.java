import org.antlr.v4.runtime.*;

public class UtilErrorListener extends BaseErrorListener {
  public static final UtilErrorListener INSTANCE = new UtilErrorListener();
  @Override
  public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol,
                          int line, int charPositionInLine, String msg, RecognitionException e) {
    System.err.printf("[Sintaxis] linea %d:%d %s%n", line, charPositionInLine, msg);
  }
}
