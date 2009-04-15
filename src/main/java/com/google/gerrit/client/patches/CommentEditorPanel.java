// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasFocus;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;

class CommentEditorPanel extends Composite implements ClickListener {
  private static final int INITIAL_COLS = 60;
  private static final int INITIAL_LINES = 5;
  private static final int MAX_LINES = 30;
  private PatchLineComment comment;
  private final LineCommentPanel renderedPanel;
  private final TextArea text;
  private final Button edit;
  private final Button save;
  private final Button cancel;
  private final Button discard;
  private final Label savedAt;
  private final Timer expandTimer;

  CommentEditorPanel(final PatchLineComment plc) {
    comment = plc;

    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName("gerrit-CommentEditor");

    renderedPanel = new LineCommentPanel(comment) {
      {
        sinkEvents(Event.ONDBLCLICK);
      }

      @Override
      public void onBrowserEvent(final Event event) {
        switch (DOM.eventGetType(event)) {
          case Event.ONDBLCLICK:
            edit();
            break;
        }
        super.onBrowserEvent(event);
      }
    };
    body.add(renderedPanel);

    expandTimer = new Timer() {
      @Override
      public void run() {
        expandText();
      }
    };
    text = new TextArea();
    text.setText(comment.getMessage());
    text.setCharacterWidth(INITIAL_COLS);
    text.setVisibleLines(INITIAL_LINES);
    DOM.setElementPropertyBoolean(text.getElement(), "spellcheck", true);
    text.addKeyboardListener(new KeyboardListenerAdapter() {
      @Override
      public void onKeyPress(final Widget sender, final char kc, final int mod) {
        DOM.eventCancelBubble(DOM.eventGetCurrentEvent(), true);

        if (kc == KEY_ESCAPE && mod == 0 && isNew()) {
          DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
          onDiscard();
          return;
        }

        if ((mod & MODIFIER_CTRL) == MODIFIER_CTRL) {
          switch (kc) {
            case 's':
              DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
              onSave();
              return;

            case 'd':
            case KEY_BACKSPACE:
            case KEY_DELETE:
              DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
              if (isNew()) {
                onDiscard();
              } else if (Window.confirm(PatchUtil.C.confirmDiscard())) {
                onDiscard();
              } else {
                text.setFocus(true);
              }
              return;
          }
        }

        expandTimer.schedule(250);
      }
    });
    body.add(text);

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName("gerrit-CommentEditor-Buttons");
    body.add(buttons);

    edit = new Button();
    edit.setText(PatchUtil.C.buttonEdit());
    edit.addClickListener(this);
    buttons.add(edit);

    save = new Button();
    save.setText(PatchUtil.C.buttonSave());
    save.addClickListener(this);
    new TextSaveButtonListener(text, save);
    save.setEnabled(false);
    buttons.add(save);

    cancel = new Button();
    cancel.setText(PatchUtil.C.buttonCancel());
    cancel.addClickListener(this);
    buttons.add(cancel);

    discard = new Button();
    discard.setText(PatchUtil.C.buttonDiscard());
    discard.addClickListener(this);
    buttons.add(discard);

    savedAt = new InlineLabel();
    savedAt.setStyleName("gerrit-CommentEditor-SavedDraft");
    buttons.add(savedAt);

    if (isNew()) {
      edit();
    } else {
      render();
    }
  }

  private void expandText() {
    final double cols = text.getCharacterWidth();
    int rows = 2;
    for (final String line : text.getText().split("\n")) {
      rows += Math.ceil((1.0 + line.length()) / cols);
    }
    rows = Math.max(INITIAL_LINES, Math.min(rows, MAX_LINES));
    if (text.getVisibleLines() != rows) {
      text.setVisibleLines(rows);
    }
  }

  private void edit() {
    text.setText(comment.getMessage());
    expandText();
    stateEdit(true);
    text.setFocus(true);
  }

  private void render() {
    final Timestamp on = comment.getWrittenOn();
    savedAt.setText(PatchUtil.M.draftSaved(new java.util.Date(on.getTime())));
    renderedPanel.update(comment);
    stateEdit(false);
  }

  private void stateEdit(final boolean inEdit) {
    expandTimer.cancel();
    renderedPanel.setVisible(!inEdit);
    edit.setVisible(!inEdit);

    text.setVisible(inEdit);
    save.setVisible(inEdit);
    cancel.setVisible(inEdit && !isNew());
    discard.setVisible(inEdit);
  }

  void setFocus(final boolean take) {
    text.setFocus(take);
  }

  boolean isNew() {
    return comment.getKey().get() == null;
  }

  public void onClick(Widget sender) {
    if (sender == edit) {
      edit();

    } else if (sender == save) {
      onSave();

    } else if (sender == cancel) {
      render();

    } else if (sender == discard) {
      onDiscard();
    }
  }

  private void onSave() {
    expandTimer.cancel();
    final String txt = text.getText().trim();
    if ("".equals(txt)) {
      return;
    }

    comment.setMessage(txt);
    text.setReadOnly(true);
    save.setEnabled(false);
    cancel.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.saveDraft(comment,
        new GerritCallback<PatchLineComment>() {
          public void onSuccess(final PatchLineComment result) {
            comment = result;
            text.setReadOnly(false);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            render();
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void onDiscard() {
    expandTimer.cancel();
    if (isNew()) {
      removeUI();
      return;
    }

    final boolean saveOn = save.isEnabled();
    text.setReadOnly(true);
    save.setEnabled(false);
    cancel.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.deleteDraft(comment.getKey(),
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            removeUI();
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            save.setEnabled(saveOn);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void removeUI() {
    final FlexTable table = (FlexTable) getParent();
    final int nRows = table.getRowCount();
    for (int row = 0; row < nRows; row++) {
      final int nCells = table.getCellCount(row);
      for (int cell = 0; cell < nCells; cell++) {
        if (table.getWidget(row, cell) == this) {
          AbstractPatchContentTable.destroyEditor(table, row, cell);
          Widget p = table;
          while (p != null) {
            if (p instanceof HasFocus) {
              ((HasFocus) p).setFocus(true);
              break;
            }
            p = p.getParent();
          }
          return;
        }
      }
    }
  }
}
