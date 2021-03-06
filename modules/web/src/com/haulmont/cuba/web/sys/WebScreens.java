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
package com.haulmont.cuba.web.sys;

import com.haulmont.bali.util.ReflectionHelper;
import com.haulmont.cuba.client.ClientConfig;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.*;
import com.haulmont.cuba.gui.Dialogs.MessageDialog;
import com.haulmont.cuba.gui.Dialogs.OptionDialog;
import com.haulmont.cuba.gui.Notifications.NotificationType;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.Component.Disposable;
import com.haulmont.cuba.gui.components.DialogWindow.WindowMode;
import com.haulmont.cuba.gui.components.Window.HasWorkArea;
import com.haulmont.cuba.gui.components.compatibility.SelectHandlerAdapter;
import com.haulmont.cuba.gui.components.mainwindow.AppWorkArea;
import com.haulmont.cuba.gui.components.mainwindow.AppWorkArea.Mode;
import com.haulmont.cuba.gui.components.mainwindow.FoldersPane;
import com.haulmont.cuba.gui.components.mainwindow.UserIndicator;
import com.haulmont.cuba.gui.components.sys.WindowImplementation;
import com.haulmont.cuba.gui.config.WindowConfig;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.data.DataSupplier;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.DsContext;
import com.haulmont.cuba.gui.data.impl.DatasourceImplementation;
import com.haulmont.cuba.gui.data.impl.DsContextImplementation;
import com.haulmont.cuba.gui.data.impl.GenericDataSupplier;
import com.haulmont.cuba.gui.logging.UIPerformanceLogger.LifeCycle;
import com.haulmont.cuba.gui.model.ScreenData;
import com.haulmont.cuba.gui.screen.*;
import com.haulmont.cuba.gui.screen.Screen.AfterInitEvent;
import com.haulmont.cuba.gui.screen.Screen.AfterShowEvent;
import com.haulmont.cuba.gui.screen.Screen.BeforeShowEvent;
import com.haulmont.cuba.gui.screen.Screen.InitEvent;
import com.haulmont.cuba.gui.screen.compatibility.*;
import com.haulmont.cuba.gui.settings.Settings;
import com.haulmont.cuba.gui.settings.SettingsImpl;
import com.haulmont.cuba.gui.sys.*;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.gui.util.OperationResult;
import com.haulmont.cuba.gui.xml.data.DsContextLoader;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoader;
import com.haulmont.cuba.gui.xml.layout.ScreenXmlLoader;
import com.haulmont.cuba.gui.xml.layout.loaders.ComponentLoaderContext;
import com.haulmont.cuba.security.app.UserSettingService;
import com.haulmont.cuba.security.entity.PermissionType;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.WebConfig;
import com.haulmont.cuba.web.gui.WebWindow;
import com.haulmont.cuba.web.gui.components.WebDialogWindow;
import com.haulmont.cuba.web.gui.components.WebTabWindow;
import com.haulmont.cuba.web.gui.components.mainwindow.WebAppWorkArea;
import com.haulmont.cuba.web.gui.components.util.ShortcutListenerDelegate;
import com.haulmont.cuba.web.gui.icons.IconResolver;
import com.haulmont.cuba.web.widgets.*;
import com.vaadin.event.ShortcutListener;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Layout;
import com.vaadin.ui.VerticalLayout;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.perf4j.StopWatch;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.haulmont.bali.util.Preconditions.checkNotNullArgument;
import static com.haulmont.cuba.gui.ComponentsHelper.walkComponents;
import static com.haulmont.cuba.gui.logging.UIPerformanceLogger.createStopWatch;
import static com.haulmont.cuba.gui.screen.FrameOwner.WINDOW_CLOSE_ACTION;
import static com.haulmont.cuba.gui.screen.UiControllerUtils.*;

@Scope(UIScope.NAME)
@Component(Screens.NAME)
public class WebScreens implements Screens, WindowManager {
    @Inject
    protected BeanLocator beanLocator;

    @Inject
    protected WindowConfig windowConfig;
    @Inject
    protected Security security;
    @Inject
    protected UuidSource uuidSource;
    @Inject
    protected UiComponents uiComponents;
    @Inject
    protected ScreenXmlLoader screenXmlLoader;
    @Inject
    protected UserSessionSource userSessionSource;
    @Inject
    protected UserSettingService userSettingService;
    @Inject
    protected ScreenViewsLoader screenViewsLoader;
    @Inject
    protected IconResolver iconResolver;
    @Inject
    protected Messages messages;
    @Inject
    protected WindowCreationHelper windowCreationHelper;
    @Inject
    protected AttributeAccessSupport attributeAccessSupport;

    @Inject
    protected WebConfig webConfig;
    @Inject
    protected ClientConfig clientConfig;

    protected AppUI ui;

    protected DataSupplier defaultDataSupplier = new GenericDataSupplier();

    public WebScreens(AppUI ui) {
        this.ui = ui;
    }

    @Override
    public <T extends Screen> T create(Class<T> requiredScreenClass, LaunchMode launchMode, ScreenOptions options) {
        checkNotNullArgument(requiredScreenClass);
        checkNotNullArgument(launchMode);
        checkNotNullArgument(options);

        WindowInfo windowInfo = getScreenInfo(requiredScreenClass);

        return createScreen(windowInfo, launchMode, options);
    }

    @Override
    public Screen create(WindowInfo windowInfo, LaunchMode launchMode, ScreenOptions options) {
        checkNotNullArgument(windowInfo);
        checkNotNullArgument(launchMode);
        checkNotNullArgument(options);

        return createScreen(windowInfo, launchMode, options);
    }

    protected <T extends Screen> T createScreen(WindowInfo windowInfo, LaunchMode launchMode, ScreenOptions options) {
        if (windowInfo.getType() != WindowInfo.Type.SCREEN) {
            throw new IllegalArgumentException(
                    String.format("Unable to create screen %s with type %s", windowInfo.getId(), windowInfo.getType())
            );
        }

        @SuppressWarnings("unchecked")
        Class<T> resolvedScreenClass = (Class<T>) windowInfo.getControllerClass();

        // load XML document here in order to get metadata before Window creation, e.g. forceDialog from <dialogMode>
        Element element = loadScreenXml(windowInfo, options);

        ScreenOpenDetails openDetails = prepareScreenOpenDetails(resolvedScreenClass, element, launchMode);

        checkPermissions(openDetails.getOpenMode(), windowInfo);

        // todo perf4j stop watches for lifecycle

        Window window = createWindow(windowInfo, resolvedScreenClass, openDetails);

        T controller = createController(windowInfo, window, resolvedScreenClass);

        // setup screen and controller

        setWindowId(controller, windowInfo.getId());
        setFrame(controller, window);
        setScreenContext(controller,
                new ScreenContextImpl(windowInfo, options, this,
                        ui.getDialogs(), ui.getNotifications(), ui.getFragments())
        );
        setScreenData(controller, beanLocator.get(ScreenData.NAME));

        WindowImplementation windowImpl = (WindowImplementation) window;
        windowImpl.setFrameOwner(controller);
        windowImpl.setId(controller.getId());

        // load UI from XML

        ComponentLoaderContext componentLoaderContext = new ComponentLoaderContext(options);
        componentLoaderContext.setFullFrameId(windowInfo.getId());
        componentLoaderContext.setCurrentFrameId(windowInfo.getId());
        componentLoaderContext.setFrame(window);

        if (element != null) {
            loadWindowFromXml(element, windowInfo, window, controller, componentLoaderContext);
        }

        // inject top level screen dependencies
        StopWatch injectStopWatch = createStopWatch(LifeCycle.INJECTION, windowInfo.getId());

        UiControllerDependencyInjector dependencyInjector =
                beanLocator.getPrototype(UiControllerDependencyInjector.NAME, controller, options);
        dependencyInjector.inject();

        injectStopWatch.stop();

        // perform injection in nested fragments
        componentLoaderContext.executeInjectTasks();

        // run init

        InitEvent initEvent = new InitEvent(controller, options);
        fireEvent(controller, InitEvent.class, initEvent);

        componentLoaderContext.executeInitTasks();
        componentLoaderContext.executePostInitTasks();

        AfterInitEvent afterInitEvent = new AfterInitEvent(controller, options);
        fireEvent(controller, AfterInitEvent.class, afterInitEvent);

        return controller;
    }

    protected ScreenOpenDetails prepareScreenOpenDetails(Class<? extends Screen> resolvedScreenClass,
                                                         @Nullable Element element,
                                                         LaunchMode requiredLaunchMode) {
        if (!(requiredLaunchMode instanceof OpenMode)) {
            throw new UnsupportedOperationException("Unsupported LaunchMode " + requiredLaunchMode);
        }

        // check if we need to change launchMode to DIALOG
        boolean forceDialog = false;
        OpenMode launchMode = (OpenMode) requiredLaunchMode;

        if (launchMode != OpenMode.DIALOG
                && launchMode != OpenMode.ROOT) {

            if (hasModalWindow()) {
                launchMode = OpenMode.DIALOG;
                forceDialog = true;
            } else {
                if (element != null && element.element("dialogMode") != null) {
                    String forceDialogAttr = element.element("dialogMode").attributeValue("forceDialog");
                    if (StringUtils.isNotEmpty(forceDialogAttr)
                            && Boolean.parseBoolean(forceDialogAttr)) {
                        launchMode = OpenMode.DIALOG;
                    }
                }

                DialogMode dialogMode = resolvedScreenClass.getAnnotation(DialogMode.class);
                if (dialogMode != null && dialogMode.forceDialog()) {
                    launchMode = OpenMode.DIALOG;
                }
            }
        }

        if (launchMode == OpenMode.THIS_TAB) {
            WebAppWorkArea workArea = getConfiguredWorkArea();

            switch (workArea.getMode()) {
                case SINGLE:
                    if (workArea.getSingleWindowContainer().getWindowContainer() == null) {
                        launchMode = OpenMode.NEW_TAB;
                    }
                    break;

                case TABBED:
                    TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

                    if (tabSheetBehaviour.getComponentCount() == 0) {
                        launchMode = OpenMode.NEW_TAB;
                    }
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported AppWorkArea mode");
            }
        } else if (launchMode == OpenMode.NEW_WINDOW) {
            launchMode = OpenMode.NEW_TAB;
        }

        return new ScreenOpenDetails(forceDialog, launchMode);
    }

    @Nullable
    protected Element loadScreenXml(WindowInfo windowInfo, ScreenOptions options) {
        String templatePath = windowInfo.getTemplate();

        if (StringUtils.isNotEmpty(templatePath)) {
            Map<String, Object> params = Collections.emptyMap();
            if (options instanceof MapScreenOptions) {
                params = ((MapScreenOptions) options).getParams();
            }
            return screenXmlLoader.load(templatePath, windowInfo.getId(), params);
        }

        return null;
    }

    protected <T extends Screen> void loadWindowFromXml(Element element, WindowInfo windowInfo, Window window, T controller,
                                                        ComponentLoaderContext componentLoaderContext) {
        LayoutLoader layoutLoader = beanLocator.getPrototype(LayoutLoader.NAME, componentLoaderContext);
        layoutLoader.setLocale(getLocale());
        layoutLoader.setMessagesPack(getMessagePack(windowInfo.getTemplate()));

        ComponentLoader<Window> windowLoader = layoutLoader.createWindowContent(window, element, windowInfo.getId());

        if (controller instanceof LegacyFrame) {
            screenViewsLoader.deployViews(element);

            initDsContext(window, element, componentLoaderContext);

            DsContext dsContext = ((LegacyFrame) controller).getDsContext();
            if (dsContext != null) {
                dsContext.setFrameContext(window.getContext());
            }
        }

        windowLoader.loadComponent();
    }

    protected void initDsContext(Window window, Element element, ComponentLoaderContext componentLoaderContext) {
        DsContext dsContext = loadDsContext(element);
        initDatasources(window, dsContext, componentLoaderContext.getParams());

        componentLoaderContext.setDsContext(dsContext);
    }

    protected DsContext loadDsContext(Element element) {
        DataSupplier dataSupplier;

        String dataSupplierClass = element.attributeValue("dataSupplier");
        if (StringUtils.isEmpty(dataSupplierClass)) {
            dataSupplier = defaultDataSupplier;
        } else {
            Class<Object> aClass = ReflectionHelper.getClass(dataSupplierClass);
            try {
                dataSupplier = (DataSupplier) aClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Unable to create data supplier for screen", e);
            }
        }

        //noinspection UnnecessaryLocalVariable
        DsContext dsContext = new DsContextLoader(dataSupplier).loadDatasources(element.element("dsContext"), null, null);
        return dsContext;
    }

    protected void initDatasources(Window window, DsContext dsContext, Map<String, Object> params) {
        ((LegacyFrame) window.getFrameOwner()).setDsContext(dsContext);

        for (Datasource ds : dsContext.getAll()) {
            if (Datasource.State.NOT_INITIALIZED.equals(ds.getState()) && ds instanceof DatasourceImplementation) {
                ((DatasourceImplementation) ds).initialized();
            }
        }
    }

    protected String getMessagePack(String descriptorPath) {
        if (descriptorPath.contains("/")) {
            descriptorPath = StringUtils.substring(descriptorPath, 0, descriptorPath.lastIndexOf("/"));
        }

        String messagesPack = descriptorPath.replaceAll("/", ".");
        int start = messagesPack.startsWith(".") ? 1 : 0;
        messagesPack = messagesPack.substring(start);
        return messagesPack;
    }

    protected Locale getLocale() {
        return userSessionSource.getUserSession().getLocale();
    }

    @Override
    public void show(Screen screen) {
        checkNotNullArgument(screen);
        checkAlreadyOpened(screen);

        checkMultiOpen(screen);

        StopWatch uiPermissionsWatch = createStopWatch(LifeCycle.UI_PERMISSIONS, screen.getId());

        windowCreationHelper.applyUiPermissions(screen.getWindow());

        uiPermissionsWatch.stop();

        BeforeShowEvent beforeShowEvent = new BeforeShowEvent(screen);
        fireEvent(screen, BeforeShowEvent.class, beforeShowEvent);

        LaunchMode launchMode = screen.getWindow().getContext().getLaunchMode();

        if (launchMode instanceof OpenMode) {
            OpenMode openMode = (OpenMode) launchMode;

            switch (openMode) {
                case ROOT:
                    showRootWindow(screen);
                    break;

                case THIS_TAB:
                    showThisTabWindow(screen);
                    break;

                case NEW_WINDOW:
                case NEW_TAB:
                    showNewTabWindow(screen);
                    break;

                case DIALOG:
                    showDialogWindow(screen);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported OpenMode " + openMode);
            }
        }

        afterShowWindow(screen);

        AfterShowEvent afterShowEvent = new AfterShowEvent(screen);
        fireEvent(screen, AfterShowEvent.class, afterShowEvent);
    }

    protected void checkAlreadyOpened(Screen screen) {
        com.vaadin.ui.Component uiComponent = screen.getWindow()
                .unwrapComposition(com.vaadin.ui.Component.class);
        if (uiComponent.isAttached()) {
            throw new IllegalStateException("Screen is already opened " + screen.getId());
        }
    }

    protected void checkNotOpened(Screen screen) {
        com.vaadin.ui.Component uiComponent = screen.getWindow()
                .unwrapComposition(com.vaadin.ui.Component.class);
        if (!uiComponent.isAttached()) {
            throw new IllegalStateException("Screen is not opened " + screen.getId());
        }
    }

    protected void afterShowWindow(Screen screen) {
        WindowContext windowContext = screen.getWindow().getContext();

        if (!WindowParams.DISABLE_APPLY_SETTINGS.getBool(windowContext)) {
            applySettings(screen, getSettingsImpl(screen.getId()));
        }

        if (screen instanceof LegacyFrame) {
            if (!WindowParams.DISABLE_RESUME_SUSPENDED.getBool(windowContext)) {
                DsContext dsContext = ((LegacyFrame) screen).getDsContext();
                if (dsContext != null) {
                    ((DsContextImplementation) dsContext).resumeSuspended();
                }
            }
        }

        if (screen instanceof AbstractWindow) {
            AbstractWindow abstractWindow = (AbstractWindow) screen;

            if (abstractWindow.isAttributeAccessControlEnabled()) {
                attributeAccessSupport.applyAttributeAccess(abstractWindow, false);
            }
        }
    }

    protected Settings getSettingsImpl(String id) {
        return new SettingsImpl(id);
    }

    @Override
    public void remove(Screen screen) {
        checkNotNullArgument(screen);
        checkNotOpened(screen);

        WindowImplementation windowImpl = (WindowImplementation) screen.getWindow();
        if (windowImpl instanceof Disposable) {
            ((Disposable) windowImpl).dispose();
        }

        LaunchMode launchMode = windowImpl.getContext().getLaunchMode();
        if (launchMode instanceof OpenMode) {
            OpenMode openMode = (OpenMode) launchMode;

            switch (openMode) {
                case DIALOG:
                    removeDialogWindow(screen);
                    break;

                case NEW_TAB:
                case NEW_WINDOW:
                    removeNewTabWindow(screen);
                    break;

                case ROOT:
                    removeRootWindow(screen);
                    break;

                case THIS_TAB:
                    removeThisTabWindow(screen);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported OpenMode");
            }
        }

        fireEvent(screen, Screen.AfterDetachEvent.class, new Screen.AfterDetachEvent(screen));
    }

    protected void removeThisTabWindow(Screen screen) {
        WebTabWindow window = (WebTabWindow) screen.getWindow();

        com.vaadin.ui.Component windowComposition = window.unwrapComposition(com.vaadin.ui.Component.class);

        TabWindowContainer windowContainer = (TabWindowContainer) windowComposition.getParent();
        windowContainer.removeComponent(windowComposition);

        WindowBreadCrumbs breadCrumbs = windowContainer.getBreadCrumbs();

        breadCrumbs.removeWindow();

        Window currentWindow = breadCrumbs.getCurrentWindow();
        com.vaadin.ui.Component currentWindowComposition =
                currentWindow.unwrapComposition(com.vaadin.ui.Component.class);

        windowContainer.addComponent(currentWindowComposition);

        WebAppWorkArea workArea = getConfiguredWorkArea();
        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            String tabId = tabSheet.getTab(windowContainer);

            TabWindow tabWindow = (TabWindow) currentWindow;

            String formattedCaption = tabWindow.formatTabCaption();
            String formattedDescription = tabWindow.formatTabDescription();

            tabSheet.setTabCaption(tabId, formattedCaption);
            if (!Objects.equals(formattedCaption, formattedDescription)) {
                tabSheet.setTabDescription(tabId, formattedDescription);
            } else {
                tabSheet.setTabDescription(tabId, null);
            }

            tabSheet.setTabIcon(tabId, iconResolver.getIconResource(currentWindow.getIcon()));

            ContentSwitchMode contentSwitchMode =
                    ContentSwitchMode.valueOf(tabWindow.getContentSwitchMode().name());
            tabSheet.setContentSwitchMode(tabId, contentSwitchMode);
        } else {
            // todo single window mode
        }
    }

    protected void removeRootWindow(@SuppressWarnings("unused") Screen screen) {
        ui.setTopLevelWindow(null);
    }

    protected void removeNewTabWindow(Screen screen) {
        WebTabWindow window = (WebTabWindow) screen.getWindow();

        com.vaadin.ui.Component windowComposition = window.unwrapComposition(com.vaadin.ui.Component.class);

        TabWindowContainer windowContainer = (TabWindowContainer) windowComposition.getParent();
        windowContainer.removeComponent(windowComposition);

        WebAppWorkArea workArea = getConfiguredWorkArea();

        boolean allWindowsRemoved;
        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            tabSheet.silentCloseTabAndSelectPrevious(windowContainer);
            tabSheet.removeComponent(windowContainer);

            allWindowsRemoved = tabSheet.getComponentCount() == 0;
        } else {
            Layout singleLayout = workArea.getSingleWindowContainer();
            singleLayout.removeComponent(windowContainer);

            allWindowsRemoved = true;
        }

        WindowBreadCrumbs windowBreadCrumbs = windowContainer.getBreadCrumbs();
        if (windowBreadCrumbs != null) {
            windowBreadCrumbs.setWindowNavigateHandler(null);
            windowBreadCrumbs.removeWindow();
        }

        if (allWindowsRemoved) {
            workArea.switchTo(AppWorkArea.State.INITIAL_LAYOUT);
        }
    }

    protected void removeDialogWindow(Screen screen) {
        Window window = screen.getWindow();

        CubaWindow cubaDialogWindow = window.unwrapComposition(CubaWindow.class);
        cubaDialogWindow.forceClose();
    }

    @Override
    public void removeAll() {
        for (Screen dialogScreen : getDialogScreens()) {
            remove(dialogScreen);
        }

        // todo remove screens from WorkArea in the reverse order
    }

    @Override
    public boolean hasUnsavedChanges() {
        Screen rootScreen = getRootScreenOrNull();
        if (rootScreen != null &&
                rootScreen.hasUnsavedChanges()) {
            return true;
        }

        return getOpenedScreens().stream()
                .anyMatch(Screen::hasUnsavedChanges);
    }

    @Override
    public Collection<Screen> getOpenedScreens() {
        List<Screen> screens = new ArrayList<>();

        getOpenedWorkAreaScreensStream()
                .forEach(screens::add);

        getDialogScreensStream()
                .forEach(screens::add);

        return screens;
    }

    @Override
    public Collection<Screen> getOpenedWorkAreaScreens() {
        return getOpenedWorkAreaScreensStream()
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Screen> getActiveScreens() {
        List<Screen> screens = new ArrayList<>();

        getActiveWorkAreaScreensStream()
                .forEach(screens::add);

        getDialogScreensStream()
                .forEach(screens::add);

        return screens;
    }

    @Override
    public Collection<Screen> getActiveWorkAreaScreens() {
        return getActiveWorkAreaScreensStream()
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Screen> getDialogScreens() {
        Collection<com.vaadin.ui.Window> windows = ui.getWindows();
        if (windows.isEmpty()) {
            return Collections.emptyList();
        }

        return getDialogScreensStream()
                .collect(Collectors.toList());
    }

    protected Stream<Screen> getOpenedWorkAreaScreensStream() {
        Screen rootScreen = getRootScreenOrNull();

        if (rootScreen == null) {
            throw new IllegalStateException("There is no root screen in UI");
        }

        WebAppWorkArea workArea = getConfiguredWorkArea();

        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            return tabSheetBehaviour.getTabComponentsStream()
                    .flatMap(c -> {
                        TabWindowContainer windowContainer = (TabWindowContainer) c;

                        Deque<Window> windows = windowContainer.getBreadCrumbs().getWindows();

                        return windows.stream()
                                .map(Window::getFrameOwner);
                    });
        } else {
            CubaSingleModeContainer singleWindowContainer = workArea.getSingleWindowContainer();
            TabWindowContainer windowContainer = (TabWindowContainer) singleWindowContainer.getWindowContainer();

            if (windowContainer != null) {
                Deque<Window> windows = windowContainer.getBreadCrumbs().getWindows();

                return windows.stream()
                        .map(Window::getFrameOwner);
            }
        }

        return Stream.empty();
    }

    protected Stream<Screen> getActiveWorkAreaScreensStream() {
        Screen rootScreen = getRootScreenOrNull();

        if (rootScreen == null) {
            throw new IllegalStateException("There is no root screen in UI");
        }

        WebAppWorkArea workArea = getConfiguredWorkArea();

        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            return tabSheetBehaviour.getTabComponentsStream()
                    .map(c -> {
                        TabWindowContainer windowContainer = (TabWindowContainer) c;

                        Window currentWindow = windowContainer.getBreadCrumbs().getCurrentWindow();

                        return currentWindow.getFrameOwner();
                    });
        } else {
            CubaSingleModeContainer singleWindowContainer = workArea.getSingleWindowContainer();
            TabWindowContainer windowContainer = (TabWindowContainer) singleWindowContainer.getWindowContainer();

            if (windowContainer != null) {
                Window currentWindow = windowContainer.getBreadCrumbs().getCurrentWindow();

                return Stream.of(currentWindow.getFrameOwner());
            }
        }

        return Stream.empty();
    }

    protected Stream<Screen> getDialogScreensStream() {
        Collection<com.vaadin.ui.Window> windows = ui.getWindows();
        if (windows.isEmpty()) {
            return Stream.empty();
        }

        return windows.stream()
                .filter(w -> w instanceof WebDialogWindow.GuiDialogWindow)
                .map(w -> ((WebDialogWindow.GuiDialogWindow) w).getDialogWindow().getFrameOwner());
    }

    @Override
    public Collection<Screen> getCurrentBreadcrumbs() {
        WebAppWorkArea workArea = getConfiguredWorkArea();

        TabWindowContainer layout = getCurrentWindowContainer(workArea);

        if (layout != null) {
            WindowBreadCrumbs breadCrumbs = layout.getBreadCrumbs();

            List<Screen> screens = new ArrayList<>(breadCrumbs.getWindows().size());
            Iterator<Window> windowIterator = breadCrumbs.getWindows().descendingIterator();
            while (windowIterator.hasNext()) {
                Screen frameOwner = windowIterator.next().getFrameOwner();
                screens.add(frameOwner);
            }

            return screens;
        }

        return Collections.emptyList();
    }

    @Nullable
    protected TabWindowContainer getCurrentWindowContainer(WebAppWorkArea workArea) {
        TabWindowContainer layout;
        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            layout = (TabWindowContainer) tabSheetBehaviour.getSelectedTab();
        } else {
            CubaSingleModeContainer singleWindowContainer = workArea.getSingleWindowContainer();

            layout = (TabWindowContainer) singleWindowContainer.getWindowContainer();
        }
        return layout;
    }

    @Nonnull
    @Override
    public Screen getRootScreen() {
        RootWindow window = ui.getTopLevelWindow();
        if (window == null) {
            throw new IllegalStateException("There is no root screen in UI");
        }

        return window.getFrameOwner();
    }

    @Override
    public Screen getRootScreenOrNull() {
        RootWindow window = ui.getTopLevelWindow();
        if (window == null) {
            return null;
        }

        return window.getFrameOwner();
    }

    @Override
    public Collection<WindowStack> getWorkAreaStacks() {
        WebAppWorkArea workArea = getConfiguredWorkArea();

        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            return tabSheetBehaviour.getTabComponentsStream()
                    .map(c -> ((TabWindowContainer) c))
                    .map(WindowStackImpl::new)
                    .collect(Collectors.toList());
        } else {
            TabWindowContainer windowContainer = (TabWindowContainer) workArea.getSingleWindowContainer().getWindowContainer();
            if (windowContainer != null) {
                return Collections.singleton(new WindowStackImpl(windowContainer));
            }
        }

        return Collections.emptyList();
    }

    /*
     * Legacy APIs
     */

    @Override
    public Collection<Window> getOpenWindows() {
        return getOpenedScreens().stream()
                .map(Screen::getWindow)
                .collect(Collectors.toList());
    }

    @Override
    public void selectWindowTab(Window window) {
        // todo

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean windowExist(WindowInfo windowInfo, Map<String, Object> params) {
        // todo

        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("IncorrectCreateGuiComponent")
    @Override
    public Window openWindow(WindowInfo windowInfo, OpenType openType, Map<String, Object> params) {
        params = createParametersMap(windowInfo, params);
        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        show(screen);
        return screen instanceof Window ? (Window) screen : new ScreenWrapper(screen);
    }

    @SuppressWarnings("IncorrectCreateGuiComponent")
    @Override
    public Window openWindow(WindowInfo windowInfo, OpenType openType) {
        Map<String, Object> params = createParametersMap(windowInfo, Collections.emptyMap());
        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        show(screen);
        return screen instanceof Window ? (Window) screen : new ScreenWrapper(screen);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Window.Editor openEditor(WindowInfo windowInfo, Entity item, OpenType openType, Datasource parentDs) {
        Map<String, Object> params = createParametersMap(windowInfo,
                Collections.singletonMap(WindowParams.ITEM.name(), item)
        );
        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        EditorScreen editorScreen = (EditorScreen) screen;
        if (editorScreen instanceof AbstractEditor) {
            ((AbstractEditor) editorScreen).setParentDs(parentDs);
        }
        editorScreen.setEntityToEdit(item);
        show(screen);
        return screen instanceof Window.Editor ? (Window.Editor) screen : new ScreenEditorWrapper(screen);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Window.Editor openEditor(WindowInfo windowInfo, Entity item, OpenType openType) {
        Map<String, Object> params = createParametersMap(windowInfo,
                Collections.singletonMap(WindowParams.ITEM.name(), item)
        );

        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        EditorScreen editorScreen = (EditorScreen) screen;
        editorScreen.setEntityToEdit(item);
        show(screen);
        return screen instanceof Window.Editor ? (Window.Editor) screen : new ScreenEditorWrapper(screen);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Window.Editor openEditor(WindowInfo windowInfo, Entity item, OpenType openType, Map<String, Object> params) {
        params = createParametersMap(windowInfo, params);
        params.put(WindowParams.ITEM.name(), item);

        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        EditorScreen editorScreen = (EditorScreen) screen;
        editorScreen.setEntityToEdit(item);
        show(screen);
        return screen instanceof Window.Editor ? (Window.Editor) screen : new ScreenEditorWrapper(screen);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Window.Editor openEditor(WindowInfo windowInfo, Entity item, OpenType openType, Map<String, Object> params,
                                    Datasource parentDs) {
        params = createParametersMap(windowInfo, params);
        params.put(WindowParams.ITEM.name(), item);

        MapScreenOptions options = new MapScreenOptions(params);

        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        EditorScreen editorScreen = (EditorScreen) screen;
        if (editorScreen instanceof AbstractEditor) {
            ((AbstractEditor) editorScreen).setParentDs(parentDs);
        }
        editorScreen.setEntityToEdit(item);
        show(screen);
        return screen instanceof Window.Editor ? (Window.Editor) screen : new ScreenEditorWrapper(screen);
    }

    @Override
    public Window.Lookup openLookup(WindowInfo windowInfo, Window.Lookup.Handler handler, OpenType openType,
                                    Map<String, Object> params) {
        params = createParametersMap(windowInfo, params);

        MapScreenOptions options = new MapScreenOptions(params);
        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        ((LookupScreen) screen).setSelectHandler(new SelectHandlerAdapter(handler));

        show(screen);

        return screen instanceof Window.Lookup ? (Window.Lookup) screen : new ScreenLookupWrapper(screen);
    }

    @Override
    public Window.Lookup openLookup(WindowInfo windowInfo, Window.Lookup.Handler handler, OpenType openType) {
        Map<String, Object> params = createParametersMap(windowInfo, Collections.emptyMap());

        MapScreenOptions options = new MapScreenOptions(params);
        Screen screen = create(windowInfo, openType.getOpenMode(), options);
        applyOpenTypeParameters(screen.getWindow(), openType);

        ((LookupScreen) screen).setSelectHandler(new SelectHandlerAdapter(handler));

        show(screen);

        return screen instanceof Window.Lookup ? (Window.Lookup) screen : new ScreenLookupWrapper(screen);
    }

    @Override
    public Frame openFrame(Frame parentFrame, com.haulmont.cuba.gui.components.Component parent, WindowInfo windowInfo) {
        return openFrame(parentFrame, parent, windowInfo, Collections.emptyMap());
    }

    @Override
    public Frame openFrame(Frame parentFrame, com.haulmont.cuba.gui.components.Component parent, WindowInfo windowInfo,
                           Map<String, Object> params) {
        return openFrame(parentFrame, parent, null, windowInfo, params);
    }

    @Override
    public Frame openFrame(Frame parentFrame, com.haulmont.cuba.gui.components.Component parent, @Nullable String id,
                           WindowInfo windowInfo, Map<String, Object> params) {
        ScreenFragment screenFragment;

        Fragments fragments = ui.getFragments();

        if (params != null && !params.isEmpty()) {
            screenFragment = fragments.create(parentFrame.getFrameOwner(), windowInfo, new MapScreenOptions(params));
        } else {
            screenFragment = fragments.create(parentFrame.getFrameOwner(), windowInfo);
        }

        if (id != null) {
            screenFragment.getFragment().setId(id);
        }

        if (parent instanceof ComponentContainer) {
            ComponentContainer container = (ComponentContainer) parent;
            for (com.haulmont.cuba.gui.components.Component c : container.getComponents()) {
                if (c instanceof com.haulmont.cuba.gui.components.Component.Disposable) {
                    com.haulmont.cuba.gui.components.Component.Disposable disposable =
                            (com.haulmont.cuba.gui.components.Component.Disposable) c;
                    if (!disposable.isDisposed()) {
                        disposable.dispose();
                    }
                }
                container.remove(c);
            }
            container.add(screenFragment.getFragment());
        }

        fragments.init(screenFragment);

        return screenFragment instanceof Frame ? (Frame) screenFragment : new ScreenFragmentWrapper(screenFragment);
    }

    @Override
    public void showNotification(String caption) {
        ui.getNotifications().create()
                .setCaption(caption)
                .show();
    }

    @Override
    public void showNotification(String caption, Frame.NotificationType type) {
        ui.getNotifications().create()
                .setCaption(caption)
                .setContentMode(Frame.NotificationType.isHTML(type) ? ContentMode.HTML : ContentMode.TEXT)
                .setType(convertNotificationType(type))
                .show();
    }

    @Override
    public void showNotification(String caption, String description, Frame.NotificationType type) {
        ui.getNotifications().create()
                .setCaption(caption)
                .setDescription(description)
                .setContentMode(Frame.NotificationType.isHTML(type) ? ContentMode.HTML : ContentMode.TEXT)
                .setType(convertNotificationType(type))
                .show();
    }

    protected NotificationType convertNotificationType(Frame.NotificationType type) {
        switch (type) {
            case TRAY:
            case TRAY_HTML:
                return NotificationType.TRAY;

            case ERROR:
            case ERROR_HTML:
                return NotificationType.ERROR;

            case HUMANIZED:
            case HUMANIZED_HTML:
                return NotificationType.HUMANIZED;

            case WARNING:
            case WARNING_HTML:
                return NotificationType.WARNING;

            default:
                throw new UnsupportedOperationException("Unsupported notification type");
        }
    }

    @Override
    public void showMessageDialog(String title, String message, Frame.MessageType messageType) {
        MessageDialog messageDialog = ui.getDialogs().createMessageDialog()
                .setCaption(title)
                .setMessage(message)
                .setType(convertMessageType(messageType.getMessageMode()))
                .setContentMode(
                        Frame.MessageMode.isHTML(messageType.getMessageMode()) ? ContentMode.HTML : ContentMode.TEXT
                );

        if (messageType.getWidth() != null) {
            messageDialog.setWidth(messageType.getWidth() + messageType.getWidthUnit().getSymbol());
        }
        if (messageType.getModal() != null) {
            messageDialog.setModal(messageType.getModal());
        }
        if (messageType.getCloseOnClickOutside() != null) {
            messageDialog.setCloseOnClickOutside(messageType.getCloseOnClickOutside());
        }
        if (messageType.getMaximized() != null) {
            messageDialog.setMaximized(messageType.getMaximized());
        }

        messageDialog.show();
    }

    protected Dialogs.MessageType convertMessageType(Frame.MessageMode messageMode) {
        switch (messageMode) {
            case CONFIRMATION:
            case CONFIRMATION_HTML:
                return Dialogs.MessageType.CONFIRMATION;

            case WARNING:
            case WARNING_HTML:
                return Dialogs.MessageType.WARNING;

            default:
                throw new UnsupportedOperationException("Unsupported message type");
        }
    }

    @Override
    public void showOptionDialog(String title, String message, Frame.MessageType messageType, Action[] actions) {
        OptionDialog optionDialog = ui.getDialogs().createOptionDialog()
                .setCaption(title)
                .setMessage(message)
                .setType(convertMessageType(messageType.getMessageMode()))
                .setActions(actions);

        if (messageType.getWidth() != null) {
            optionDialog.setWidth(messageType.getWidth() + messageType.getWidthUnit().getSymbol());
        }
        if (messageType.getMaximized() != null) {
            optionDialog.setMaximized(messageType.getMaximized());
        }

        optionDialog.show();
    }

    @Override
    public void showExceptionDialog(Throwable throwable) {
        showExceptionDialog(throwable, null, null);
    }

    @Override
    public void showExceptionDialog(Throwable throwable, @Nullable String caption, @Nullable String message) {
        ui.getDialogs().createExceptionDialog()
                .setCaption(caption)
                .setMessage(message)
                .setThrowable(throwable)
                .show();
    }

    @Override
    public void showWebPage(String url, @Nullable Map<String, Object> params) {
        ui.getWebBrowserTools().showWebPage(url, params);
    }

    /**
     * Check modifications and close all screens in all main windows.
     *
     * @param runIfOk a closure to run after all screens are closed
     */
    @Deprecated
    public void checkModificationsAndCloseAll(Runnable runIfOk) {
        checkModificationsAndCloseAll()
                .then(runIfOk);
    }

    /**
     * Check modifications and close all screens in all main windows.
     *
     * @param runIfOk     a closure to run after all screens are closed
     * @param runIfCancel a closure to run if there were modifications and a user canceled the operation
     */
    @Deprecated
    public void checkModificationsAndCloseAll(Runnable runIfOk, Runnable runIfCancel) {
        checkModificationsAndCloseAll()
                .then(runIfOk)
                .otherwise(runIfCancel);
    }

    /**
     * todo
     *
     * @return operation result
     */
    public OperationResult checkModificationsAndCloseAll() {
        throw new UnsupportedOperationException("TODO");
    }

    public void closeAllTabbedWindows() {
        closeAllTabbedWindowsExcept(null);
    }

    public void closeAllTabbedWindowsExcept(@Nullable com.vaadin.ui.ComponentContainer keepOpened) {
        throw new UnsupportedOperationException();
    }

    /**
     * Close all screens in all main windows (browser tabs).
     */
    public void closeAllWindows() {
        throw new UnsupportedOperationException(); // todo
    }

    /**
     * Close all screens in the main window (browser tab) this WindowManagerImpl belongs to.
     */
    public void closeAll() {
        throw new UnsupportedOperationException(); // todo
    }

    protected <T extends Screen> T createController(WindowInfo windowInfo, Window window, Class<T> screenClass) {
        Constructor<T> constructor;
        try {
            constructor = screenClass.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new DevelopmentException("No accessible constructor for screen class " + screenClass);
        }

        T controller;
        try {
            controller = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Unable to create instance of screen class " + screenClass);
        }

        return controller;
    }

    protected Window createWindow(WindowInfo windowInfo, Class<? extends Screen> screenClass,
                                  ScreenOpenDetails openDetails) {
        Window window;

        OpenMode openMode = openDetails.getOpenMode();
        switch (openMode) {
            case ROOT:
                // todo should be changed
                ui.beforeTopLevelWindowInit();

                window = uiComponents.create(RootWindow.NAME);
                break;

            case THIS_TAB:
            case NEW_TAB:
                window = uiComponents.create(TabWindow.NAME);
                break;

            case DIALOG:
                DialogWindow dialogWindow = uiComponents.create(DialogWindow.NAME);

                if (openDetails.isForceDialog()) {
                    ThemeConstants theme = ui.getApp().getThemeConstants();

                    dialogWindow.setDialogWidth(theme.get("cuba.web.WebWindowManager.forciblyDialog.width"));
                    dialogWindow.setDialogHeight(theme.get("cuba.web.WebWindowManager.forciblyDialog.height"));
                    dialogWindow.setResizable(true);
                } else {
                    DialogMode dialogMode = screenClass.getAnnotation(DialogMode.class);

                    if (dialogMode != null) {
                        dialogWindow.setModal(dialogMode.modal());
                        dialogWindow.setCloseable(dialogMode.closeable());
                        dialogWindow.setResizable(dialogMode.resizable());
                        dialogWindow.setCloseOnClickOutside(dialogMode.closeOnClickOutside());

                        if (StringUtils.isNotEmpty(dialogMode.width())) {
                            dialogWindow.setDialogWidth(dialogMode.width());
                        }
                        if (StringUtils.isNotEmpty(dialogMode.height())) {
                            dialogWindow.setDialogHeight(dialogMode.height());
                        }

                        dialogWindow.setWindowMode(dialogMode.windowMode());
                    }
                }

                window = dialogWindow;

                break;

            default:
                throw new UnsupportedOperationException("Unsupported launch mode " + openMode);
        }

        WindowContextImpl windowContext = new WindowContextImpl(window, openDetails.getOpenMode());
        ((WindowImplementation) window).setContext(windowContext);

        return window;
    }

    protected void checkMultiOpen(Screen screen) {
        // todo check if already opened, replace buggy int hash code
    }

    protected void checkPermissions(LaunchMode launchMode, WindowInfo windowInfo) {
        // ROOT windows are always permitted
        if (launchMode != OpenMode.ROOT) {
            boolean permitted = security.isScreenPermitted(windowInfo.getId());
            if (!permitted) {
                throw new AccessDeniedException(PermissionType.SCREEN, windowInfo.getId());
            }
        }
    }

    protected WindowInfo getScreenInfo(Class<? extends Screen> screenClass) {
        UiController uiController = screenClass.getAnnotation(UiController.class);
        if (uiController == null) {
            throw new IllegalArgumentException("No @UiController annotation for class " + screenClass);
        }

        String screenId = UiDescriptorUtils.getInferredScreenId(uiController, screenClass);

        return windowConfig.getWindowInfo(screenId);
    }

    protected void showRootWindow(Screen screen) {
        if (screen instanceof MainScreen) {
            MainScreen mainScreen = (MainScreen) screen;

            // bind system UI components to AbstractMainWindow
            walkComponents(screen.getWindow(), component -> {
                if (component instanceof AppWorkArea) {
                    mainScreen.setWorkArea((AppWorkArea) component);
                } else if (component instanceof UserIndicator) {
                    mainScreen.setUserIndicator((UserIndicator) component);
                } else if (component instanceof FoldersPane) {
                    mainScreen.setFoldersPane((FoldersPane) component);
                }

                return false;
            });
        }

        ui.setTopLevelWindow((RootWindow) screen.getWindow());

        if (screen instanceof Window.HasWorkArea) {
            AppWorkArea workArea = ((Window.HasWorkArea) screen).getWorkArea();
            if (workArea != null) {
                workArea.addStateChangeListener(new AppWorkArea.StateChangeListener() {
                    @Override
                    public void stateChanged(AppWorkArea.State newState) {
                        if (newState == AppWorkArea.State.WINDOW_CONTAINER) {
                            initTabShortcuts();

                            // listener used only once
                            getConfiguredWorkArea().removeStateChangeListener(this);
                        }
                    }
                });
            }
        }
    }

    protected void initTabShortcuts() {
        RootWindow topLevelWindow = ui.getTopLevelWindow();
        CubaOrderedActionsLayout actionsLayout = topLevelWindow.unwrap(CubaOrderedActionsLayout.class);

        if (getConfiguredWorkArea().getMode() == Mode.TABBED) {
            actionsLayout.addShortcutListener(createNextWindowTabShortcut(topLevelWindow));
            actionsLayout.addShortcutListener(createPreviousWindowTabShortcut(topLevelWindow));
        }
        actionsLayout.addShortcutListener(createCloseShortcut(topLevelWindow));
    }

    // used only for legacy screens
    @Deprecated
    protected Map<String, Object> createParametersMap(WindowInfo windowInfo, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>(params.size());

        Element element = windowInfo.getDescriptor();
        if (element != null) {
            Element paramsElement = element.element("params") != null ? element.element("params") : element;
            if (paramsElement != null) {
                @SuppressWarnings({"unchecked"})
                List<Element> paramElements = paramsElement.elements("param");
                for (Element paramElement : paramElements) {
                    String name = paramElement.attributeValue("name");
                    String value = paramElement.attributeValue("value");
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        Boolean booleanValue = Boolean.valueOf(value);
                        map.put(name, booleanValue);
                    } else {
                        map.put(name, value);
                    }
                }
            }
        }
        map.putAll(params);

        return map;
    }

    protected ShortcutListener createCloseShortcut(RootWindow topLevelWindow) {
        String closeShortcut = clientConfig.getCloseShortcut();
        KeyCombination combination = KeyCombination.create(closeShortcut);

        return new ShortcutListenerDelegate("onClose", combination.getKey().getCode(),
                KeyCombination.Modifier.codes(combination.getModifiers()))
                .withHandler((sender, target) ->
                        closeWindowByShortcut(topLevelWindow)
                );
    }

    protected ShortcutListener createNextWindowTabShortcut(RootWindow topLevelWindow) {
        String nextTabShortcut = clientConfig.getNextTabShortcut();
        KeyCombination combination = KeyCombination.create(nextTabShortcut);

        return new ShortcutListenerDelegate(
                "onNextTab", combination.getKey().getCode(),
                KeyCombination.Modifier.codes(combination.getModifiers())
        ).withHandler((sender, target) -> {
            WebAppWorkArea workArea = getConfiguredWorkArea();
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            if (tabSheet != null
                    && !hasModalWindow()
                    && tabSheet.getComponentCount() > 1) {
                com.vaadin.ui.Component selectedTabComponent = tabSheet.getSelectedTab();
                String tabId = tabSheet.getTab(selectedTabComponent);
                int tabPosition = tabSheet.getTabPosition(tabId);
                int newTabPosition = (tabPosition + 1) % tabSheet.getComponentCount();

                String newTabId = tabSheet.getTab(newTabPosition);
                tabSheet.setSelectedTab(newTabId);

                moveFocus(tabSheet, newTabId);
            }
        });
    }

    protected ShortcutListener createPreviousWindowTabShortcut(RootWindow topLevelWindow) {
        String previousTabShortcut = clientConfig.getPreviousTabShortcut();
        KeyCombination combination = KeyCombination.create(previousTabShortcut);

        return new ShortcutListenerDelegate("onPreviousTab", combination.getKey().getCode(),
                KeyCombination.Modifier.codes(combination.getModifiers())
        ).withHandler((sender, target) -> {
            WebAppWorkArea workArea = getConfiguredWorkArea();
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            if (tabSheet != null
                    && !hasModalWindow()
                    && tabSheet.getComponentCount() > 1) {
                com.vaadin.ui.Component selectedTabComponent = tabSheet.getSelectedTab();
                String selectedTabId = tabSheet.getTab(selectedTabComponent);
                int tabPosition = tabSheet.getTabPosition(selectedTabId);
                int newTabPosition = (tabSheet.getComponentCount() + tabPosition - 1) % tabSheet.getComponentCount();

                String newTabId = tabSheet.getTab(newTabPosition);
                tabSheet.setSelectedTab(newTabId);

                moveFocus(tabSheet, newTabId);
            }
        });
    }

    protected void closeWindowByShortcut(RootWindow topLevelWindow) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        if (workArea.getState() != AppWorkArea.State.WINDOW_CONTAINER) {
            return;
        }

        CubaUI ui = (CubaUI) workArea.getComponent().getUI();
        if (!ui.isAccessibleForUser(workArea.getComponent())) {
            LoggerFactory.getLogger(CubaTabSheet.class)
                    .debug("Ignore close shortcut attempt because workArea is inaccessible for user");
            return;
        }

        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            if (tabSheet != null) {
                TabWindowContainer layout = (TabWindowContainer) tabSheet.getSelectedTab();
                if (layout != null) {
                    tabSheet.focus();

                    WindowBreadCrumbs breadCrumbs = layout.getBreadCrumbs();

                    Window currentWindow = breadCrumbs.getCurrentWindow();

                    if (!canWindowBeClosed(currentWindow)) {
                        return;
                    }

                    if (isWindowClosePrevented(currentWindow, CloseOriginType.SHORTCUT)) {
                        return;
                    }

                    if (breadCrumbs.getWindows().isEmpty()) {
                        com.vaadin.ui.Component previousTab = tabSheet.getPreviousTab(layout);
                        if (previousTab != null) {
                            currentWindow.getFrameOwner()
                                    .close(FrameOwner.WINDOW_CLOSE_ACTION)
                                    .then(() -> tabSheet.setSelectedTab(previousTab));
                        } else {
                            currentWindow.getFrameOwner()
                                    .close(FrameOwner.WINDOW_CLOSE_ACTION);
                        }
                    } else {
                        currentWindow.getFrameOwner()
                                .close(FrameOwner.WINDOW_CLOSE_ACTION);
                    }
                }
            }
        } else {
            Iterator<WindowBreadCrumbs> it = getTabs(workArea).iterator();
            if (it.hasNext()) {
                Window currentWindow = it.next().getCurrentWindow();
                if (!isWindowClosePrevented(currentWindow, CloseOriginType.SHORTCUT)) {
                    ui.focus();

                    currentWindow.getFrameOwner()
                            .close(FrameOwner.WINDOW_CLOSE_ACTION);
                }
            }
        }
    }

    protected List<WindowBreadCrumbs> getTabs(WebAppWorkArea workArea) {
        TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

        List<WindowBreadCrumbs> allBreadCrumbs = new ArrayList<>();
        for (int i = 0; i < tabSheet.getComponentCount(); i++) {
            String tabId = tabSheet.getTab(i);

            TabWindowContainer tabComponent = (TabWindowContainer) tabSheet.getTabComponent(tabId);
            allBreadCrumbs.add(tabComponent.getBreadCrumbs());
        }
        return allBreadCrumbs;
    }

    protected void moveFocus(TabSheetBehaviour tabSheet, String tabId) {
        TabWindowContainer windowContainer = (TabWindowContainer) tabSheet.getTabComponent(tabId);
        Window window = windowContainer.getBreadCrumbs().getCurrentWindow();

        if (window != null) {
            boolean focused = false;
            String focusComponentId = window.getFocusComponent();
            if (focusComponentId != null) {
                com.haulmont.cuba.gui.components.Component focusComponent = window.getComponent(focusComponentId);
                if (focusComponent instanceof com.haulmont.cuba.gui.components.Component.Focusable
                        && focusComponent.isEnabledRecursive()
                        && focusComponent.isVisibleRecursive()) {
                    ((com.haulmont.cuba.gui.components.Component.Focusable) focusComponent).focus();
                    focused = true;
                }
            }

            if (!focused && window instanceof Window.Wrapper) {
                Window.Wrapper wrapper = (Window.Wrapper) window;
                focused = ((WebWindow) wrapper.getWrappedWindow()).findAndFocusChildComponent();
                if (!focused) {
                    tabSheet.focus();
                }
            }
        }
    }

    protected void showNewTabWindow(Screen screen) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        workArea.switchTo(AppWorkArea.State.WINDOW_CONTAINER);

        // close previous windows
        if (workArea.getMode() == Mode.SINGLE) {
            VerticalLayout mainLayout = workArea.getSingleWindowContainer();
            if (mainLayout.getComponentCount() > 0) {
                TabWindowContainer oldLayout = (TabWindowContainer) mainLayout.getComponent(0);
                WindowBreadCrumbs oldBreadCrumbs = oldLayout.getBreadCrumbs();
                if (oldBreadCrumbs != null) {
                    Window oldWindow = oldBreadCrumbs.getCurrentWindow();
                    oldWindow.closeAndRun(MAIN_MENU_ACTION_ID, () -> {
                        // todo implement
//                            showWindow(window, caption, message, WindowManager.OpenType.NEW_TAB, false)
                    });
                    return;
                }
            }
        } else {
            /* todo
            Integer hashCode = getWindowHashCode(window);
            com.vaadin.ui.ComponentContainer tab = null;
            if (hashCode != null && !multipleOpen) {
                tab = findTab(hashCode);
            }

            com.vaadin.ui.ComponentContainer oldLayout = tab;
            final WindowBreadCrumbs oldBreadCrumbs = tabs.get(oldLayout);

            if (oldBreadCrumbs != null
                    && windowOpenMode.containsKey(oldBreadCrumbs.getCurrentWindow().getFrame())
                    && !multipleOpen) {
                Window oldWindow = oldBreadCrumbs.getCurrentWindow();
                selectWindowTab(((Window.Wrapper) oldBreadCrumbs.getCurrentWindow()).getWrappedWindow());

                int tabPosition = -1;
                final TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
                String tabId = tabSheet.getTab(tab);
                if (tabId != null) {
                    tabPosition = tabSheet.getTabPosition(tabId);
                }

                final int finalTabPosition = tabPosition;
                oldWindow.closeAndRun(MAIN_MENU_ACTION_ID, () -> {
                    showWindow(window, caption, message, WindowManager.OpenType.NEW_TAB, false);

                    Window wrappedWindow = window;
                    if (window instanceof Window.Wrapper) {
                        wrappedWindow = ((Window.Wrapper) window).getWrappedWindow();
                    }

                    if (finalTabPosition >= 0 && finalTabPosition < tabSheet.getComponentCount() - 1) {
                        moveWindowTab(workArea, wrappedWindow, finalTabPosition);
                    }
                });
                return;
            }
            */
        }

        // work with new window
        createNewTabLayout(screen);
    }

    protected WindowBreadCrumbs createWindowBreadCrumbs(@SuppressWarnings("unused") Screen screen) {
        WebAppWorkArea appWorkArea = getConfiguredWorkArea();

        WindowBreadCrumbs windowBreadCrumbs = new WindowBreadCrumbs(appWorkArea.getMode());
        windowBreadCrumbs.setUI(ui);
        windowBreadCrumbs.setBeanLocator(beanLocator);
        windowBreadCrumbs.afterPropertiesSet();

        boolean showBreadCrumbs = webConfig.getShowBreadCrumbs() || appWorkArea.getMode() == Mode.SINGLE;
        windowBreadCrumbs.setVisible(showBreadCrumbs);

        return windowBreadCrumbs;
    }

    protected void createNewTabLayout(Screen screen) {
        WindowBreadCrumbs breadCrumbs = createWindowBreadCrumbs(screen);
        breadCrumbs.setWindowNavigateHandler(this::handleWindowBreadCrumbsNavigate);
        breadCrumbs.addWindow(screen.getWindow());

        TabWindowContainer windowContainer = new TabWindowContainerImpl();
        windowContainer.setPrimaryStyleName("c-app-window-wrap");
        windowContainer.setSizeFull();

        windowContainer.setBreadCrumbs(breadCrumbs);
        windowContainer.addComponent(breadCrumbs);

        Window window = screen.getWindow();

        com.vaadin.ui.Component windowComposition = window.unwrapComposition(com.vaadin.ui.Component.class);
        windowContainer.addComponent(windowComposition);

        WebAppWorkArea workArea = getConfiguredWorkArea();

        if (workArea.getMode() == Mode.TABBED) {
            windowContainer.addStyleName("c-app-tabbed-window");

            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

            String tabId;

            ScreenContext screenContext = UiControllerUtils.getScreenContext(screen);

            ScreenOptions options = screenContext.getScreenOptions();
            WindowInfo windowInfo = screenContext.getWindowInfo();

            com.vaadin.ui.ComponentContainer tab = findSameWindowTab(window, options);

            if (tab != null && !windowInfo.getMultipleOpen()) {
                tabSheet.replaceComponent(tab, windowContainer);
                tabSheet.removeComponent(tab);
                tabId = tabSheet.getTab(windowContainer);
            } else {
                tabId = "tab_" + uuidSource.createUuid();

                tabSheet.addTab(windowContainer, tabId);

                if (ui.isTestMode()) {
                    String id = "tab_" + window.getId();

                    tabSheet.setTabTestId(tabId, ui.getTestIdManager().getTestId(id));
                    tabSheet.setTabCubaId(tabId, id);
                }
            }
            TabWindow tabWindow = (TabWindow) window;

            String windowContentSwitchMode = tabWindow.getContentSwitchMode().name();
            ContentSwitchMode contentSwitchMode = ContentSwitchMode.valueOf(windowContentSwitchMode);
            tabSheet.setContentSwitchMode(tabId, contentSwitchMode);

            String formattedCaption = tabWindow.formatTabCaption();
            String formattedDescription = tabWindow.formatTabDescription();

            tabSheet.setTabCaption(tabId, formattedCaption);
            if (!Objects.equals(formattedCaption, formattedDescription)) {
                tabSheet.setTabDescription(tabId, formattedDescription);
            } else {
                tabSheet.setTabDescription(tabId, null);
            }

            tabSheet.setTabIcon(tabId, iconResolver.getIconResource(window.getIcon()));
            tabSheet.setTabClosable(tabId, true);
            tabSheet.setTabCloseHandler(windowContainer, this::handleTabWindowClose);
            tabSheet.setSelectedTab(windowContainer);
        } else {
            windowContainer.addStyleName("c-app-single-window");

            CubaSingleModeContainer mainLayout = workArea.getSingleWindowContainer();
            mainLayout.setWindowContainer(windowContainer);
        }
    }

    protected void showThisTabWindow(Screen screen) {
        WebAppWorkArea workArea = getConfiguredWorkArea();
        workArea.switchTo(AppWorkArea.State.WINDOW_CONTAINER);

        TabWindowContainer windowContainer;
        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            windowContainer = (TabWindowContainer) tabSheet.getSelectedTab();
        } else {
            windowContainer = (TabWindowContainer) workArea.getSingleWindowContainer().getWindowContainer();
        }

        if (windowContainer == null || windowContainer.getBreadCrumbs() == null) {
            throw new IllegalStateException("BreadCrumbs not found");
        }

        WindowBreadCrumbs breadCrumbs = windowContainer.getBreadCrumbs();
        Window currentWindow = breadCrumbs.getCurrentWindow();

        windowContainer.removeComponent(currentWindow.unwrapComposition(com.vaadin.ui.Layout.class));

        Window newWindow = screen.getWindow();
        com.vaadin.ui.Component newWindowComposition = newWindow.unwrapComposition(com.vaadin.ui.Component.class);

        windowContainer.addComponent(newWindowComposition);

        breadCrumbs.addWindow(newWindow);

        if (workArea.getMode() == Mode.TABBED) {
            TabSheetBehaviour tabSheet = workArea.getTabbedWindowContainer().getTabSheetBehaviour();
            String tabId = tabSheet.getTab(windowContainer);

            TabWindow tabWindow = (TabWindow) newWindow;

            String formattedCaption = tabWindow.formatTabCaption();
            String formattedDescription = tabWindow.formatTabDescription();

            tabSheet.setTabCaption(tabId, formattedCaption);
            if (!Objects.equals(formattedCaption, formattedDescription)) {
                tabSheet.setTabDescription(tabId, formattedDescription);
            } else {
                tabSheet.setTabDescription(tabId, null);
            }

            tabSheet.setTabIcon(tabId, iconResolver.getIconResource(newWindow.getIcon()));

            ContentSwitchMode contentSwitchMode = ContentSwitchMode.valueOf(tabWindow.getContentSwitchMode().name());
            tabSheet.setContentSwitchMode(tabId, contentSwitchMode);
        } else {
            windowContainer.markAsDirtyRecursive();
        }
    }

    protected void showDialogWindow(Screen screen) {
        DialogWindow window = (DialogWindow) screen.getWindow();

        CubaWindow vWindow = window.unwrapComposition(CubaWindow.class);
        vWindow.setErrorHandler(ui);

        String cubaId = "dialog_" + window.getId();
        if (ui.isTestMode()) {
            vWindow.setCubaId(cubaId);
        }
        if (ui.isPerformanceTestMode()) {
            vWindow.setId(ui.getTestIdManager().getTestId(cubaId));
        }

        if (hasModalWindow()) {
            // force modal
            window.setModal(true);
        }

        ui.addWindow(vWindow);
    }

    /**
     * @return workarea instance of the root screen
     * @throws IllegalStateException if there is no root screen or root screen does not have {@link AppWorkArea}
     */
    @Nonnull
    protected WebAppWorkArea getConfiguredWorkArea() {
        RootWindow topLevelWindow = ui.getTopLevelWindow();
        if (topLevelWindow == null) {
            throw new IllegalStateException("There is no root screen opened");
        }

        Screen controller = topLevelWindow.getFrameOwner();

        if (controller instanceof HasWorkArea) {
            AppWorkArea workArea = ((HasWorkArea) controller).getWorkArea();
            if (workArea != null) {
                return (WebAppWorkArea) workArea;
            }
        }

        throw new IllegalStateException("RootWindow does not have any configured work area");
    }

    protected void handleWindowBreadCrumbsNavigate(WindowBreadCrumbs breadCrumbs, Window window) {
        Runnable op = new Runnable() {
            @Override
            public void run() {
                Window currentWindow = breadCrumbs.getCurrentWindow();
                if (!currentWindow.isCloseable()) {
                    return;
                }

                if (window != currentWindow) {
                    if (!isWindowClosePrevented(currentWindow, CloseOriginType.BREADCRUMBS)) {
                        currentWindow.getFrameOwner()
                                .close(WINDOW_CLOSE_ACTION)
                                .then(this);
                    }
                }
            }
        };
        op.run();
    }

    protected void handleTabWindowClose(HasTabSheetBehaviour targetTabSheet, com.vaadin.ui.Component tabContent) {
        WindowBreadCrumbs tabBreadCrumbs = ((TabWindowContainer) tabContent).getBreadCrumbs();

        if (!canWindowBeClosed(tabBreadCrumbs.getCurrentWindow())) {
            return;
        }

        Runnable closeTask = new TabCloseTask(tabBreadCrumbs);
        closeTask.run();

        // it is needed to force redraw tabsheet if it has a lot of tabs and part of them are hidden
        targetTabSheet.markAsDirty();
    }

    public class TabCloseTask implements Runnable {
        protected WindowBreadCrumbs breadCrumbs;

        public TabCloseTask(WindowBreadCrumbs breadCrumbs) {
            this.breadCrumbs = breadCrumbs;
        }

        @Override
        public void run() {
            Window windowToClose = breadCrumbs.getCurrentWindow();
            if (windowToClose != null) {
                if (!isWindowClosePrevented(windowToClose, CloseOriginType.CLOSE_BUTTON)) {
                    windowToClose.getFrameOwner()
                            .close(WINDOW_CLOSE_ACTION)
                            .then(new TabCloseTask(breadCrumbs));
                }
            }
        }
    }

    protected boolean isWindowClosePrevented(Window window, Window.CloseOrigin closeOrigin) {
        Window.BeforeCloseEvent event = new Window.BeforeCloseEvent(window, closeOrigin);
        ((WebWindow) window).fireBeforeClose(event);

        return event.isClosePrevented();
    }

    protected boolean canWindowBeClosed(Window window) {
        if (!window.isCloseable()) {
            return false;
        }

        if (webConfig.getDefaultScreenCanBeClosed()) {
            return true;
        }

        String defaultScreenId = webConfig.getDefaultScreenId();

        if (webConfig.getUserCanChooseDefaultScreen()) {
            String userDefaultScreen = userSettingService.loadSetting(ClientType.WEB, "userDefaultScreen");
            defaultScreenId = StringUtils.isEmpty(userDefaultScreen) ? defaultScreenId : userDefaultScreen;
        }

        return !Objects.equals(window.getId(), defaultScreenId);
    }

    protected com.vaadin.ui.ComponentContainer findSameWindowTab(Window window, ScreenOptions options) {
        WebAppWorkArea workArea = getConfiguredWorkArea();

        TabSheetBehaviour tabSheetBehaviour = workArea.getTabbedWindowContainer().getTabSheetBehaviour();

        Iterator<com.vaadin.ui.Component> componentIterator = tabSheetBehaviour.getTabComponents();
        while (componentIterator.hasNext()) {
            TabWindowContainer component = (TabWindowContainer) componentIterator.next();
            Window currentWindow = component.getBreadCrumbs().getCurrentWindow();

//            todo include options hash into Window instance
//            if (hashCode.equals(getWindowHashCode(currentWindow))) {
//                return entry.getKey();
//            }
        }
        return null;
    }

    protected boolean hasModalWindow() {
        return ui.getWindows().stream()
                .anyMatch(com.vaadin.ui.Window::isModal);
    }

    @Deprecated
    protected void applyOpenTypeParameters(Window window, OpenType openType) {
        if (window instanceof DialogWindow) {
            DialogWindow dialogWindow = (DialogWindow) window;

            if (openType.getCloseOnClickOutside() != null) {
                dialogWindow.setCloseOnClickOutside(openType.getCloseOnClickOutside());
            }
            if (openType.getMaximized() != null) {
                dialogWindow.setWindowMode(openType.getMaximized() ? WindowMode.MAXIMIZED : WindowMode.NORMAL);
            }
            if (openType.getModal() != null) {
                dialogWindow.setModal(openType.getModal());
            }
            if (openType.getResizable() != null) {
                dialogWindow.setResizable(openType.getResizable());
            }
            if (openType.getWidth() != null) {
                dialogWindow.setDialogWidth(openType.getWidth() + openType.getWidthUnit().getSymbol());
            }
            if (openType.getHeight() != null) {
                dialogWindow.setDialogHeight(openType.getHeight() + openType.getHeightUnit().getSymbol());
            }
        }

        if (openType.getCloseable() != null) {
            window.setCloseable(openType.getCloseable());
        }
    }

    // todo message type parameters

    /**
     * Content of each tab of AppWorkArea TabSheet.
     */
    protected static class TabWindowContainerImpl extends CssLayout implements TabWindowContainer {
        protected WindowBreadCrumbs breadCrumbs;

        @Override
        public WindowBreadCrumbs getBreadCrumbs() {
            return breadCrumbs;
        }

        @Override
        public void setBreadCrumbs(WindowBreadCrumbs breadCrumbs) {
            this.breadCrumbs = breadCrumbs;
        }
    }

    protected static class ScreenOpenDetails {
        private boolean forceDialog;
        private OpenMode openMode;

        public ScreenOpenDetails(boolean forceDialog, OpenMode openMode) {
            this.forceDialog = forceDialog;
            this.openMode = openMode;
        }

        public boolean isForceDialog() {
            return forceDialog;
        }

        public OpenMode getOpenMode() {
            return openMode;
        }
    }

    protected class WindowStackImpl implements WindowStack {

        protected final TabWindowContainer windowContainer;

        public WindowStackImpl(TabWindowContainer windowContainer) {
            this.windowContainer = windowContainer;
        }

        @Override
        public Collection<Screen> getBreadcrumbs() {
            Deque<Window> windows = windowContainer.getBreadCrumbs().getWindows();
            Iterator<Window> windowIterator = windows.descendingIterator();

            List<Screen> screens = new ArrayList<>(windows.size());

            while (windowIterator.hasNext()) {
                Screen screen = windowIterator.next().getFrameOwner();
                screens.add(screen);
            }

            return screens;
        }
    }
}