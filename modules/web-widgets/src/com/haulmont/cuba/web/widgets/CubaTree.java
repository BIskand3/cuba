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

import com.google.common.base.Preconditions;
import com.haulmont.cuba.web.widgets.tree.EnhancedTreeDataProvider;
import com.vaadin.data.SelectionModel;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.HierarchicalQuery;
import com.vaadin.data.provider.Query;
import com.vaadin.event.Action;
import com.vaadin.event.ActionManager;
import com.vaadin.event.ShortcutListener;
import com.vaadin.shared.Registration;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Tree;
import com.vaadin.ui.TreeGrid;
import com.vaadin.ui.components.grid.GridSelectionModel;
import com.vaadin.ui.components.grid.MultiSelectionModel;
import com.vaadin.ui.components.grid.NoSelectionModel;
import com.vaadin.ui.components.grid.SingleSelectionModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CubaTree<T> extends Tree<T> implements Action.ShortcutNotifier {

    /**
     * Keeps track of the ShortcutListeners added to this component, and manages the painting and handling as well.
     */
    protected ActionManager shortcutActionManager;

    @Override
    protected TreeGrid<T> createTreeGrid() {
        return new CubaTreeGrid<>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CubaTreeGrid<T> getCompositionRoot() {
        return (CubaTreeGrid<T>) super.getCompositionRoot();
    }

    public void setGridSelectionModel(GridSelectionModel<T> model) {
        getCompositionRoot().setGridSelectionModel(model);
    }

    @Override
    protected Grid.SelectionMode getSelectionMode() {
        SelectionModel<T> selectionModel = getSelectionModel();
        Grid.SelectionMode mode = null;
        if (selectionModel instanceof SingleSelectionModel) {
            mode = Grid.SelectionMode.SINGLE;
        } else if (selectionModel instanceof MultiSelectionModel) {
            mode = Grid.SelectionMode.MULTI;
        } else if (selectionModel instanceof NoSelectionModel) {
            mode = Grid.SelectionMode.NONE;
        }
        return mode;
    }

    @Override
    public void setDataProvider(DataProvider<T, ?> dataProvider) {
        if (!(dataProvider instanceof EnhancedTreeDataProvider)) {
            throw new IllegalArgumentException("DataProvider must implement " +
                    "com.haulmont.cuba.web.widgets.tree.EnhancedTreeDataProvider");
        }

        super.setDataProvider(dataProvider);
    }

    public Collection<T> getChildren(T item) {
        return getDataProvider().fetchChildren(new HierarchicalQuery<>(null, item))
                .collect(Collectors.toList());
    }

    public boolean hasChildren(T item) {
        return getDataProvider().hasChildren(item);
    }

    public Stream<T> getItems() {
        return getDataProvider().fetch(new Query<>());
    }

    @SuppressWarnings("unchecked")
    protected T getParentItem(T item) {
        return ((EnhancedTreeDataProvider<T>) getDataProvider()).getParent(item);
    }

    // TODO: gg, replace
    /*@Override
    public void changeVariables(Object source, Map<String, Object> variables) {
        super.changeVariables(source, variables);

        if (shortcutActionManager != null) {
            shortcutActionManager.handleActions(variables, this);
        }
    }*/

    @Override
    public Registration addShortcutListener(ShortcutListener shortcut) {
        if (shortcutActionManager == null) {
            shortcutActionManager = new ShortcutActionManager(this);
        }

        shortcutActionManager.addAction(shortcut);

        return () -> shortcutActionManager.removeAction(shortcut);
    }

    @Override
    public void removeShortcutListener(ShortcutListener shortcut) {
        if (shortcutActionManager != null) {
            shortcutActionManager.removeAction(shortcut);
        }
    }

    // TODO: gg, replace
    /*@Override
    protected void paintActions(PaintTarget target, Set<Action> actionSet) throws PaintException {
        super.paintActions(target, actionSet);

        if (shortcutActionManager != null) {
            shortcutActionManager.paintActions(null, target);
        }
    }*/

    public void expandAll() {
        expand(getItems().collect(Collectors.toList()));
    }

    public void expandItemWithParents(T item) {
        List<T> itemsToExpand = new ArrayList<>();

        T current = item;
        while (current != null) {
            itemsToExpand.add(current);
            current = getParentItem(current);
        }

        expand(itemsToExpand);
    }

    public void collapseAll() {
        collapse(getItems().collect(Collectors.toList()));
    }

    public void collapseItemWithChildren(T item) {
        Collection<T> itemsToCollapse = getItemWithChildren(item)
                .collect(Collectors.toList());
        collapse(itemsToCollapse);
    }

    protected Stream<T> getItemWithChildren(T item) {
        return Stream.concat(Stream.of(item), hasChildren(item)
                ? getChildren(item).stream().flatMap(this::getItemWithChildren)
                : Stream.empty());
    }

    public void expandUpTo(int level) {
        Preconditions.checkArgument(level > 0, "level should be greater than 0");

        Collection<T> rootItems = getChildren(null);
        expandRecursively(rootItems, level - 1);
    }

    public void deselectAll() {
        getSelectionModel().deselectAll();
    }

    public void repaint() {
        markAsDirtyRecursive();
        getCompositionRoot().repaint();
    }
}