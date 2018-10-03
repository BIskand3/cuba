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

package com.haulmont.cuba.gui.components.factories;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.gui.UiComponents;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.RuntimePropsDatasource;
import com.haulmont.cuba.gui.xml.layout.loaders.FieldGroupLoader.FieldConfig;

import javax.inject.Inject;

@org.springframework.stereotype.Component(FieldGroupFieldFactory.NAME)
public class FieldGroupFieldFactoryImpl implements FieldGroupFieldFactory {

    @Inject
    protected UiComponentsGenerator uiComponentsGenerator;

    @Inject
    protected UiComponents uiComponents;

    @Override
    public GeneratedField createField(FieldConfig fc) {
        return createFieldComponent(fc);
    }

    protected GeneratedField createFieldComponent(FieldConfig fc) {
        if (fc.isCustom()) {
            return new GeneratedField(uiComponents.create(FieldGroupEmptyField.NAME));
        }

        MetaClass metaClass = resolveMetaClass(fc.getTargetDatasource());

        ComponentGenerationContext context = new ComponentGenerationContext(metaClass, fc.getProperty())
                .setDatasource(fc.getTargetDatasource())
                .setOptionsDatasource(fc.getOptionsDatasource())
                .setXmlDescriptor(fc.getXmlDescriptor())
                .setComponentClass(FieldGroup.class);

        return new GeneratedField(uiComponentsGenerator.generate(context));
    }

    protected MetaClass resolveMetaClass(Datasource datasource) {
        return datasource instanceof RuntimePropsDatasource ?
                ((RuntimePropsDatasource) datasource).resolveCategorizedEntityClass() : datasource.getMetaClass();
    }
}