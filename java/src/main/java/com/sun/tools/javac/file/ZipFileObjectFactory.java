//
// Codex - a framework for grokking code
// http://github.com/scaled/codex/blob/master/LICENSE

package com.sun.tools.javac.file;

import java.util.zip.ZipEntry;

/**
 * The {@code ZipFileObject} constructor is protected, but if we extend the class to call the
 * constructor we end up outside the {@code com.sun.tools.javac} package, and javac detects that and
 * wraps the file object in a class that causes it to fail the isSameFile check. Yay!
 */
public class ZipFileObjectFactory {

  public static ZipArchive.ZipFileObject newZipFileObject (
    ZipArchive arch, String name, ZipEntry entry) {
    return new ZipArchive.ZipFileObject(arch, name, entry);
  }
}
