// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.patch;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.SimpleDateFormat;

public class Text extends RawText {
  private static final Logger log = LoggerFactory.getLogger(Text.class);
  private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

  public static final byte[] NO_BYTES = {};
  public static final Text EMPTY = new Text(NO_BYTES);

  public static Text forCommit(Repository db, AnyObjectId commitId)
      throws IOException {
    RevWalk rw = new RevWalk(db);
    RevCommit c;
    if (commitId instanceof RevCommit) {
      c = (RevCommit) commitId;
    } else {
      c = rw.parseCommit(commitId);
    }

    StringBuilder b = new StringBuilder();
    switch (c.getParentCount()) {
      case 0:
        break;
      case 1: {
        RevCommit p = c.getParent(0);
        rw.parseBody(p);
        b.append("Parent:     ");
        b.append(p.abbreviate(db, 8).name());
        b.append(" (");
        b.append(p.getShortMessage());
        b.append(")\n");
        break;
      }
      default:
        for (int i = 0; i < c.getParentCount(); i++) {
          RevCommit p = c.getParent(i);
          rw.parseBody(p);
          b.append(i == 0 ? "Merge Of:   " : "            ");
          b.append(p.abbreviate(db, 8).name());
          b.append(" (");
          b.append(p.getShortMessage());
          b.append(")\n");
        }
    }
    appendPersonIdent(b, "Author", c.getAuthorIdent());
    appendPersonIdent(b, "Commit", c.getCommitterIdent());
    b.append("\n");
    b.append(c.getFullMessage());
    return new Text(b.toString().getBytes("UTF-8"));
  }

  private static void appendPersonIdent(StringBuilder b, String field,
      PersonIdent person) {
    if (person != null) {
      b.append(field + ":    ");
      if (person.getName() != null) {
        b.append(" ");
        b.append(person.getName());
      }
      if (person.getEmailAddress() != null) {
        b.append(" <");
        b.append(person.getEmailAddress());
        b.append(">");
      }
      b.append("\n");

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZ");
      sdf.setTimeZone(person.getTimeZone());
      b.append(field + "Date: ");
      b.append(sdf.format(person.getWhen()));
      b.append("\n");
    }
  }

  public static String asString(byte[] content, String encoding) {
    return new String(content, charset(content, encoding));
  }

  private static Charset charset(byte[] content, String encoding) {
    if (encoding == null) {
      UniversalDetector d = new UniversalDetector(null);
      d.handleData(content, 0, content.length);
      d.dataEnd();
      encoding = d.getDetectedCharset();
    }
    if (encoding == null) {
      return ISO_8859_1;
    }
    try {
      return Charset.forName(encoding);

    } catch (IllegalCharsetNameException err) {
      log.error("Invalid detected charset name '" + encoding + "': " + err);
      return ISO_8859_1;

    } catch (UnsupportedCharsetException err) {
      log.error("Detected charset '" + encoding + "' not supported: " + err);
      return ISO_8859_1;
    }
  }

  private Charset charset;

  public Text(final byte[] r) {
    super(r);
  }

  public byte[] getContent() {
    return content;
  }

  public String getLine(final int i) {
    return getLines(i, i + 1, true);
  }

  public String getLines(final int begin, final int end, boolean dropLF) {
    if (begin == end) {
      return "";
    }

    final int s = getLineStart(begin);
    int e = getLineEnd(end - 1);
    if (dropLF && content[e - 1] == '\n') {
      e--;
    }
    return decode(s, e);
  }

  private String decode(final int s, int e) {
    if (charset == null) {
      charset = charset(content, null);
    }
    return RawParseUtils.decode(charset, content, s, e);
  }

  private int getLineStart(final int i) {
    return lines.get(i + 1);
  }

  private int getLineEnd(final int i) {
    return lines.get(i + 2);
  }
}
