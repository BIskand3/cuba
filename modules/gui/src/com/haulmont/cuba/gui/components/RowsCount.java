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
package com.haulmont.cuba.gui.components;

import com.haulmont.cuba.gui.data.CollectionDatasource;

import java.util.EventObject;

/**
 * Component that makes a {@link CollectionDatasource} to load data by pages. Usually used inside {@link Table}.
 */
public interface RowsCount extends Component.BelongToFrame, Component.HasXmlDescriptor {

    enum State {
        FIRST_COMPLETE,     // "63 rows"
        FIRST_INCOMPLETE,   // "1-100 rows of [?] >"
        MIDDLE,             // "< 101-200 rows of [?] >"
        LAST                // "< 201-252 rows"
    }

    String NAME = "rowsCount";

    // todo JavaDoc
    @Deprecated
    CollectionDatasource getDatasource();
    // todo JavaDoc
    @Deprecated
    void setDatasource(CollectionDatasource datasource);

    /**
     * @return a component that displays data from the same datasource, usually a {@link Table}. Can be null.
     */
    @Deprecated
    ListComponent getOwner();
    @Deprecated
    void setOwner(ListComponent owner);

    RowsCountTarget getRowsCountTarget();
    void setRowsCountTarget(RowsCountTarget target);

    // vaadin8 extract RowsCountTarget interface
    interface RowsCountTarget {
        // todo
    }

    class BeforeRefreshEvent extends EventObject {
        private boolean refreshPrevented;

        public BeforeRefreshEvent(RowsCount source) {
            super(source);
        }

        /**
         * If invoked, the component will not refresh the datasource.
         */
        public void preventRefresh() {
            refreshPrevented = true;
        }

        public boolean isRefreshPrevented() {
            return refreshPrevented;
        }
    }

    /**
     * A listener to be notified before refreshing the datasource when the user clicks next, previous, etc.
     * <p>
     * You can prevent the datasource refresh by invoking {@link BeforeRefreshEvent#preventRefresh()},
     * for example:
     * <pre>{@code
     * table.getRowsCount().addBeforeDatasourceRefreshListener(event -> {
     *     if (event.getDatasource().isModified()) {
     *         showNotification("Save changes before going to another page");
     *         event.preventRefresh();
     *     }
     * });
     * }</pre>
     */
    @FunctionalInterface
    interface BeforeRefreshListener {
        void beforeDatasourceRefresh(BeforeRefreshEvent event);
    }

    /**
     * Adds a {@link BeforeRefreshListener}.
     */
    void addBeforeRefreshListener(BeforeRefreshListener listener);
    void removeBeforeRefreshListener(BeforeRefreshListener listener);
}