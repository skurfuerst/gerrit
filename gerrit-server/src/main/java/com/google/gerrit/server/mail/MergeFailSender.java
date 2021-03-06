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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.Change;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Send notice about a change failing to merged. */
public class MergeFailSender extends ReplyToChangeSender {
  public static interface Factory {
    public MergeFailSender create(Change change);
  }

  @Inject
  public MergeFailSender(EmailArguments ea, @Assisted Change c) {
    super(ea, c, "comment");
  }

  @Override
  protected void init() {
    super.init();

    ccExistingReviewers();
  }

  @Override
  protected void formatChange() {
    appendText("Change " + change.getKey().abbreviate());
    if (patchSetInfo != null && patchSetInfo.getAuthor() != null
        && patchSetInfo.getAuthor().getName() != null) {
      appendText(" by ");
      appendText(patchSetInfo.getAuthor().getName());
    }
    appendText(" FAILED to submit to ");
    appendText(change.getDest().getShortName());
    appendText(".\n\n");
    formatCoverLetter();
  }
}
