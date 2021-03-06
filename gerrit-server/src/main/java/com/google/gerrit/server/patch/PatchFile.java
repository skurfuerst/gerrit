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

import com.google.gerrit.common.errors.CorruptEntityException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Patch;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;

/** State supporting processing of a single {@link Patch} instance. */
public class PatchFile {
  private final Repository repo;
  private final PatchListEntry entry;
  private final RevTree aTree;
  private final RevTree bTree;

  private Text a;
  private Text b;

  public PatchFile(final Repository repo, final PatchList patchList,
      final String fileName) throws MissingObjectException,
      IncorrectObjectTypeException, IOException {
    this.repo = repo;
    this.entry = patchList.get(fileName);

    final RevWalk rw = new RevWalk(repo);
    final RevCommit bCommit = rw.parseCommit(patchList.getNewId());

    if (Patch.COMMIT_MSG.equals(fileName)) {
      if (patchList.isAgainstParent()) {
        a = Text.EMPTY;
      } else {
        a = Text.forCommit(repo, patchList.getOldId());
      }
      b = Text.forCommit(repo, bCommit);

      aTree = null;
      bTree = null;

    } else {
      if (patchList.getOldId() != null) {
        aTree = rw.parseTree(patchList.getOldId());
      } else {
        final RevCommit p = bCommit.getParent(0);
        rw.parseHeaders(p);
        aTree = p.getTree();
      }
      bTree = bCommit.getTree();
    }
  }

  /**
   * Extract a line from the file, as a string.
   *
   * @param file the file index to extract.
   * @param line the line number to extract (1 based; 1 is the first line).
   * @return the string version of the file line.
   * @throws CorruptEntityException the patch cannot be read.
   * @throws IOException the patch or complete file content cannot be read.
   * @throws NoSuchEntityException
   * @throws CharacterCodingException the file is not a known character set.
   */
  public String getLine(final int file, final int line)
      throws CorruptEntityException, IOException, NoSuchEntityException {
    switch (file) {
      case 0:
        if (a == null) {
          a = load(aTree, entry.getOldName());
        }
        return a.getLine(line - 1);

      case 1:
        if (b == null) {
          b = load(bTree, entry.getNewName());
        }
        return b.getLine(line - 1);

      default:
        throw new NoSuchEntityException();
    }
  }

  private Text load(final ObjectId tree, final String path)
      throws MissingObjectException, IncorrectObjectTypeException,
      CorruptObjectException, IOException {
    if (path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(repo, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
      return Text.EMPTY;
    }
    final ObjectId id = tw.getObjectId(0);
    final ObjectLoader ldr = repo.openObject(id);
    if (ldr == null) {
      throw new MissingObjectException(id, Constants.TYPE_BLOB);
    }
    return new Text(ldr.getCachedBytes());
  }
}
