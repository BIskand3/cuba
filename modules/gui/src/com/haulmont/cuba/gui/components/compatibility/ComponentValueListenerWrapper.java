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

package com.haulmont.cuba.gui.components.compatibility;

import com.haulmont.cuba.gui.components.HasValue;
import com.haulmont.cuba.gui.data.ValueListener;

import java.util.function.Consumer;

// todo for removal
@Deprecated
public class ComponentValueListenerWrapper implements Consumer<HasValue.ValueChangeEvent> {

    protected final ValueListener listener;

    public ComponentValueListenerWrapper(ValueListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ComponentValueListenerWrapper that = (ComponentValueListenerWrapper) obj;

        return this.listener.equals(that.listener);
    }

    @Override
    public int hashCode() {
        return listener.hashCode();
    }

    @Override
    public void accept(HasValue.ValueChangeEvent e) {
        listener.valueChanged(e.getComponent(), "value", e.getPrevValue(), e.getValue());
    }
}