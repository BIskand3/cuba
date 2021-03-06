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

package com.haulmont.cuba.web.tmp;

import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.data.table.SortableDatasourceTableItems;
import com.haulmont.cuba.gui.data.impl.CollectionDatasourceImpl;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import com.haulmont.cuba.security.entity.User;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

public class TableApiScreen1 extends AbstractWindow {
    @Inject
    protected ComponentsFactory componentsFactory;
    @Inject
    protected CollectionDatasourceImpl<User, UUID> usersDs;

    @Override
    public void init(Map<String, Object> params) {
        Table<User> usersTable = componentsFactory.createComponent(Table.class);

        usersTable.addColumn(new Table.Column<>(User.class, "login"));
        usersTable.addColumn(new Table.Column<>(User.class, "active"));
        usersTable.addColumn(new Table.Column<>(User.class, "name"));

        usersTable.setItems(new SortableDatasourceTableItems<>(usersDs));
        usersTable.setSizeFull();

        add(usersTable);
        expand(usersTable);
    }

    public void refresh() {
        usersDs.refresh();
    }
}