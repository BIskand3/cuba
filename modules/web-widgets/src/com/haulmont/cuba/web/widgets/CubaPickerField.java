/*
 * Copyright (c) 2008-2017 Haulmont.
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

package com.haulmont.cuba.web.widgets;

import com.vaadin.data.ValueProvider;
import com.vaadin.event.Action;
import com.vaadin.ui.AbstractField;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class CubaPickerField<T> extends com.vaadin.ui.CustomField<T> implements Action.Container {

    protected static final String LAYOUT_STYLENAME = "c-pickerfield-layout";
    protected static final String TEXT_FIELD_STYLENAME = "c-pickerfield-text";
    protected static final String BUTTON_STYLENAME = "c-pickerfield-button";

    protected T valueInternal;

    protected AbstractField<?> field;
    protected ValueProvider<T, String> testFieldValueProvider;

    protected List<Button> buttons = new ArrayList<>(4);
    protected CubaCssActionsLayout container;

    protected boolean fieldReadOnly = true;

    protected boolean suppressTextChangeListener = false;

    public CubaPickerField() {
        init();
        initField();
        initLayout();
    }

    protected void init() {
        setPrimaryStyleName("c-pickerfield");
        setSizeUndefined();

        // VAADIN8: gg,
//        setValidationVisible(false);
//        setShowBufferedSourceException(false);
//        setShowErrorForDisabledState(false);
    }

    @Override
    protected Component initContent() {
        return container;
    }

    protected void initLayout() {
        container = new CubaCssActionsLayout();
        container.setPrimaryStyleName(LAYOUT_STYLENAME);

        container.setWidth(100, Unit.PERCENTAGE);
        field.setWidth(100, Unit.PERCENTAGE);

        container.addComponent(field);

        setFocusDelegate(field);
    }

    protected void initField() {
        CubaTextField field = new CubaTextField();
        field.setStyleName(TEXT_FIELD_STYLENAME);
        field.setReadOnlyFocusable(true);

        field.setReadOnly(true);
//        vaadin8
//        field.setNullRepresentation("");

        // TEST: gg, do we need this?
        /*addValueChangeListener(event -> {
            if (!suppressTextChangeListener) {
                updateTextRepresentation();
            }
        });*/

        this.field = field;
    }

    protected void updateTextRepresentation() {
        CubaTextField textField = (CubaTextField) field;

        // TEST: gg, do we need this?
        suppressTextChangeListener = true;

        String value = getStringRepresentation();
        textField.setValue(value);

        // TEST: gg, do we need this?
        suppressTextChangeListener = false;
    }

    @SuppressWarnings("unchecked")
    protected String getStringRepresentation() {
        if (testFieldValueProvider != null) {
            return testFieldValueProvider.apply(getValue());
        }

        T value = getValue();
        return value != null
                ? String.valueOf(value)
                : getEmptyStringRepresentation();
    }

    protected String getEmptyStringRepresentation() {
        return "";
    }

    @Override
    protected void doSetValue(T value) {
        valueInternal = value;
        updateTextRepresentation();
    }

    @Override
    public T getValue() {
        return valueInternal;
    }

    public boolean isFieldReadOnly() {
        return fieldReadOnly;
    }

    public void setFieldReadOnly(boolean fieldReadOnly) {
        this.fieldReadOnly = fieldReadOnly;

        updateFieldReadOnly();
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);

        updateFieldReadOnly();
        updateFieldReadOnlyFocusable();
    }

    protected void updateFieldReadOnly() {
        getField().setReadOnly(isReadOnly() || fieldReadOnly);
    }

    protected void updateFieldReadOnlyFocusable() {
        ((CubaTextField) getField()).setReadOnlyFocusable(!isReadOnly() && fieldReadOnly);
    }

    @Override
    public void attach() {
        // TEST: gg, do we need this?
        suppressTextChangeListener = true;

        super.attach();

        // TEST: gg, do we need this?
        suppressTextChangeListener = false;

        // update text representation manually
        if (field instanceof TextField) {
            updateTextRepresentation();
        }
    }

    @Override
    public void setWidth(float width, Unit unit) {
        super.setWidth(width, unit);

        if (container != null) {
            if (width < 0) {
                container.setWidthUndefined();
                field.setWidthUndefined();
            } else {
                container.setWidth(100, Unit.PERCENTAGE);
                field.setWidth(100, Unit.PERCENTAGE);
            }
        }
    }

    @Override
    public void setHeight(float height, Unit unit) {
        super.setHeight(height, unit);

        if (container != null) {
            if (height < 0) {
                container.setHeightUndefined();
                field.setHeightUndefined();
            } else {
                container.setHeight(100, Unit.PERCENTAGE);
                field.setHeight(100, Unit.PERCENTAGE);
            }
        }
    }

    public List<Button> getButtons() {
        return Collections.unmodifiableList(buttons);
    }

    public void addButton(Button button, int index) {
        button.setTabIndex(-1);
        button.setStyleName(BUTTON_STYLENAME);

        buttons.add(index, button);
        container.addComponent(button, index + 1); // 0 - field
    }

    public void removeButton(Button button) {
        buttons.remove(button);
        container.removeComponent(button);
    }

    public AbstractField<?> getField() {
        return field;
    }

    public void addFieldListener(BiConsumer<String, Object> listener) {
        field.addValueChangeListener(event -> {
            // TEST: gg,
            String text = (String) event.getValue();

            if (!suppressTextChangeListener &&
                    !Objects.equals(getStringRepresentation(), text)) {
                suppressTextChangeListener = true;

                listener.accept(text, getValue());

                suppressTextChangeListener = false;

                // update text representation manually
                if (field instanceof TextField) {
                    updateTextRepresentation();
                }
            }
        });
    }

    @Override
    public void focus() {
        field.focus();
    }

    @Override
    public void addActionHandler(Action.Handler actionHandler) {
        container.addActionHandler(actionHandler);
    }

    @Override
    public void removeActionHandler(Action.Handler actionHandler) {
        container.removeActionHandler(actionHandler);
    }

    // VAADIN8: gg, implement
    /*@Override
    public ErrorMessage getErrorMessage() {
        *//*ErrorMessage superError = super.getErrorMessage();
        if (!isReadOnly() && isRequired() && isEmpty()) {
            ErrorMessage error = AbstractErrorMessage.getErrorMessageForException(
                    new com.vaadin.v7.data.Validator.EmptyValueException(getRequiredError()));
            if (error != null) {
                return new CompositeErrorMessage(superError, error);
            }
        }

        return superError;*//*
    }*/

    @Override
    public boolean isEmpty() {
        return getValue() == null;
    }

    public ValueProvider<T, String> getTestFieldValueProvider() {
        return testFieldValueProvider;
    }

    public void setTestFieldValueProvider(ValueProvider<T, String> testFieldValueProvider) {
        this.testFieldValueProvider = testFieldValueProvider;
    }

    @Override
    public void setTabIndex(int tabIndex) {
        field.setTabIndex(tabIndex);
    }

    @Override
    public int getTabIndex() {
        return field.getTabIndex();
    }

    // TODO: gg, remove
    /*@Override
    protected boolean fieldValueEquals(Object value1, Object value2) {
        // only if instance the same,
        // we can set instance of entity with the same id but different property values
        return value1 == value2;
    }*/

    public static class PickerButton extends CubaButton {

    }
}