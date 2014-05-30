//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package codex.store;

import java.io.UnsupportedEncodingException;

public class Flater {

  public static byte[] toBytes (String string) {
    try {
      return string.getBytes("UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new AssertionError(uee);
    }
  }

  public static String fromBytes (byte[] bytes) {
    return fromBytes(bytes, 0, bytes.length);
  }

  public static String fromBytes (byte[] bytes, int offset, int length) {
    try {
      return new String(bytes, offset, length, "UTF-8");
    } catch (UnsupportedEncodingException uee) {
      throw new AssertionError(uee);
    }
  }

  protected static final byte TRUE = 1;
  protected static final byte FALSE = 0;

}
