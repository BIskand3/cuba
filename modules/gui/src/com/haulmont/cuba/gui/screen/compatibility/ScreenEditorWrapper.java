/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.gui.screen.compatibility;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.compatibility.AfterCloseListenerAdapter;
import com.haulmont.cuba.gui.screen.EditorScreen;
import com.haulmont.cuba.gui.screen.Screen;

/**
 * Wrapper object for compatibility with legacy code.
 */
@Deprecated
public class ScreenEditorWrapper extends AbstractEditor {

    private Screen screen;

    public ScreenEditorWrapper(Screen screen) {
        this.screen = screen;
    }

    @Override
    protected void initCommitActions(InitEvent event) {
        // do nothing
    }

    @Override
    public Frame getWrappedFrame() {
        return screen.getWindow();
    }

    @Override
    public void addListener(CloseListener listener) {
        screen.addAfterCloseListener(new AfterCloseListenerAdapter(listener));
    }

    @Override
    public void addCloseListener(CloseListener listener) {
        screen.addAfterCloseListener(new AfterCloseListenerAdapter(listener));
    }

    @Override
    public Entity getItem() {
        return ((EditorScreen) screen).getEditedEntity();
    }
}