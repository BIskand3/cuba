/*
 * Copyright (c) 2008-2016 Haulmont.
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
 *
 */
package com.haulmont.cuba.gui.data;

import com.haulmont.cuba.gui.components.HasValue;

import javax.annotation.Nullable;

/**
 * Listener to value change events
 *
 * @deprecated Use {@link HasValue#addValueChangeListener(java.util.function.Consumer)}
 * @param <T> type of event source
 */
@Deprecated
public interface ValueListener<T> {

    /**
     * Called when an attribute value changed.
     *
     * @param source    changed object
     * @param property  changed attribute name
     * @param prevValue previous value
     * @param value     current value
     */
    void valueChanged(T source, String property, @Nullable Object prevValue, @Nullable Object value);
}