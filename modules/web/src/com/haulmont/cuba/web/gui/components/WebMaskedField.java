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

package com.haulmont.cuba.web.gui.components;

import com.haulmont.bali.events.Subscription;
import com.haulmont.cuba.gui.components.MaskedField;
import com.haulmont.cuba.gui.components.TextField;
import com.haulmont.cuba.gui.components.data.ConversionException;
import com.haulmont.cuba.web.gui.components.util.ShortcutListenerDelegate;
import com.haulmont.cuba.web.widgets.CubaMaskedTextField;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.event.ShortcutListener;

import java.util.function.Consumer;

public class WebMaskedField extends WebV8AbstractField<CubaMaskedTextField, String, String> implements MaskedField {

    protected ShortcutListener enterShortcutListener;

    public WebMaskedField() {
        this.component = createTextFieldImpl();

        attachValueChangeListener(component);
    }

    @Override
    public void setMask(String mask) {
        component.setMask(mask);
    }

    @Override
    public String getMask() {
        return component.getMask();
    }

    @Override
    public void setValueMode(ValueMode mode) {
        component.setMaskedMode(mode == ValueMode.MASKED);
    }

    @Override
    public ValueMode getValueMode() {
        return component.isMaskedMode() ? ValueMode.MASKED : ValueMode.CLEAR;
    }

    @Override
    public boolean isSendNullRepresentation() {
        return component.isSendNullRepresentation();
    }

    @Override
    public void setSendNullRepresentation(boolean sendNullRepresentation) {
        component.setSendNullRepresentation(sendNullRepresentation);
    }

    @Override
    public String getRawValue() {
        return component.getValue();
    }

    @Override
    protected String convertToPresentation(String modelValue) throws ConversionException {
        return modelValue == null ? "" : modelValue;
    }

//    vaadin8
//    @Override
    protected CubaMaskedTextField createTextFieldImpl() {
        return new CubaMaskedTextField();
    }

    @Override
    public void setCursorPosition(int position) {
        component.setCursorPosition(position);
    }

    @Override
    public void selectAll() {
        component.selectAll();
    }

    @Override
    public void setSelectionRange(int pos, int length) {
        component.setSelection(pos, length);
    }

    @Override
    public Subscription addEnterPressListener(Consumer<EnterPressEvent> listener) {
        if (enterShortcutListener == null) {
            enterShortcutListener = new ShortcutListenerDelegate("enter", KeyCode.ENTER, null)
                    .withHandler((sender, target) -> {
                        EnterPressEvent event = new EnterPressEvent(WebMaskedField.this);
                        publish(EnterPressEvent.class, event);
                    });
            component.addShortcutListener(enterShortcutListener);
        }

        return MaskedField.super.addEnterPressListener(listener);
    }

    @Override
    public void removeEnterPressListener(Consumer<EnterPressEvent> listener) {
        MaskedField.super.removeEnterPressListener(listener);

        if (enterShortcutListener != null
                && !hasSubscriptions(EnterPressEvent.class)) {
            component.removeShortcutListener(enterShortcutListener);
            enterShortcutListener = null;
        }
    }

    @Override
    public void focus() {
        component.focus();
    }

    @Override
    public int getTabIndex() {
        return component.getTabIndex();
    }

    @Override
    public void setTabIndex(int tabIndex) {
        component.setTabIndex(tabIndex);
    }

    @Override
    public void commit() {
        // vaadin8
    }

    @Override
    public void discard() {
        // vaadin8
    }

    @Override
    public boolean isBuffered() {
        // vaadin8
        return false;
    }

    @Override
    public void setBuffered(boolean buffered) {
        // vaadin8
    }

    @Override
    public boolean isModified() {
        // vaadin8
        return false;
    }
}