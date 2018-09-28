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

package com.haulmont.cuba.gui.components.data;

import com.haulmont.cuba.gui.components.HasValue;

/**
 * vaadin8 todo JavaDoc
 * vaadin8 subclasses for EntityValueBinding
 *
 * @param <V> todo
 */
public interface ValueBinding<V> extends Binding {
    ValueSource<V> getSource();
    HasValue<V> getComponent();

    void activate();

    void write();

    void discard();

    boolean isBuffered();

    void setBuffered(boolean buffered);

    boolean isModified();
}