package network;

import java.io.IOException;
import java.io.Reader;

public final class LineIO {
  public static final int MAX_LINE = 65_536;

  private LineIO() {}

  public static String read(Reader reader) throws IOException {
    StringBuilder line = new StringBuilder();
    int ch;
    while ((ch = reader.read()) != -1) {
      if (ch == '\n') return line.toString();
      if (ch == '\r') {
        if (reader.read() != '\n') throw new IOException("invalid line ending");
        return line.toString();
      }
      if (line.length() == MAX_LINE) throw new IOException("line too long");
      line.append((char) ch);
    }
    if (!line.isEmpty()) throw new IOException("truncated line");
    return null;
  }
}
