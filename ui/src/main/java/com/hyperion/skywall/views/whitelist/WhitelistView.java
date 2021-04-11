package com.hyperion.skywall.views.whitelist;


import com.hyperion.skywall.backend.model.config.ActivationStatus;
import com.hyperion.skywall.backend.model.config.Delay;
import com.hyperion.skywall.backend.model.config.Path;
import com.hyperion.skywall.backend.model.config.job.*;
import com.hyperion.skywall.backend.model.config.service.Host;
import com.hyperion.skywall.backend.model.config.service.Service;
import com.hyperion.skywall.backend.model.nlp.enumentity.FilterMode;
import com.hyperion.skywall.backend.services.ConfigService;
import com.hyperion.skywall.backend.services.ConfigUtils;
import com.hyperion.skywall.backend.services.JobRunner;
import com.hyperion.skywall.views.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;


@Route(value = "whitelist", layout = MainView.class)
@RouteAlias(value = "", layout = MainView.class)
@PageTitle("Whitelist Apps & Websites")
@CssImport(value = "./styles/views/whitelist/whitelist-view.css", include = "lumo-badge")
@JsModule("@vaadin/vaadin-lumo-styles/badge.js")
public class WhitelistView extends VerticalLayout implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(WhitelistView.class);

    private ConfigService configService;
    private JobRunner jobRunner;

    private final Grid<Service> definedServices;
    private final Binder<Service> serviceBinder;

    private final Grid<Host> hosts;
    private final Binder<Host> hostBinder;

    private final Grid<Path> whitelistedPaths;
    private final Binder<Path> whitelistedPathBinder;
    private final Path addNewPathWhitelist;

    private final Grid<Path> blacklistedPaths;
    private final Binder<Path> blacklistedPathBinder;
    private final Path addNewPathBlacklist;

    private final Host addNewHost;
    private final Service addNewService;

    private Editor<Host> hostsEditor;

    private Notification notification;

    @Autowired
    private void setApplicationContext(ApplicationContext applicationContext) {
        this.configService = applicationContext.getBean(ConfigService.class);
        this.jobRunner = applicationContext.getBean(JobRunner.class);
    }

    public WhitelistView() {
        setSizeFull();
        setPadding(false);
        definedServices = new Grid<>();
        serviceBinder = new Binder<>(Service.class);
        addNewService = new Service();
        addNewService.setName("Add new");
        initServicesGrid();

        hosts = new Grid<>();
        hostBinder = new Binder<>(Host.class);
        addNewHost = new Host();
        addNewHost.setHost("Add new");
        initHostsGrid();

        whitelistedPaths = new Grid<>();
        whitelistedPathBinder = new Binder<>(Path.class);
        blacklistedPaths = new Grid<>();
        blacklistedPathBinder = new Binder<>(Path.class);
        addNewPathWhitelist = new Path();
        addNewPathWhitelist.setPath("Add new");
        addNewPathBlacklist = new Path();
        addNewPathBlacklist.setPath("Add new");
        initWhitelistedPathsGrid();
        initBlacklistedPathsGrid();

        Tabs sections = new Tabs();
        sections.setWidthFull();
        Map<Tab, Component> tabsToPages = new HashMap<>();

        Tab websitesTab = new Tab("Websites");
        Div websitesPage = generateWebsitesPage();
        tabsToPages.put(websitesTab, websitesPage);

        Tab appsTab = new Tab("Apps");
        tabsToPages.put(appsTab, generateAppsPage());

        VerticalLayout tabActivePage = new VerticalLayout();
        tabActivePage.setPadding(false);
        tabActivePage.add(websitesPage);

        sections.add(websitesTab, appsTab);
        sections.setFlexGrowForEnclosedTabs(1);
        sections.addSelectedChangeListener(event -> {
            Component selectedPage = tabsToPages.get(sections.getSelectedTab());
            selectedPage.setVisible(true);
            tabActivePage.removeAll();
            tabActivePage.add(selectedPage);
        });

        add(sections);
        add(tabActivePage);
    }

    private Div generateWebsitesPage() {
        Div root = new Div();
        Div primary = new Div();
        primary.setSizeFull();
        primary.add(createPrimaryFormLayout());

        Div secondary = new Div();
        secondary.setSizeFull();
        secondary.add(createFormLayout());

        VerticalLayout searchBar = new VerticalLayout();
        searchBar.setWidthFull();
        searchBar.setPadding(true);

        HorizontalLayout fieldRow = new HorizontalLayout();
        fieldRow.setPadding(false);
        fieldRow.setSpacing(true);
        TextField filter = new TextField();
        filter.addValueChangeListener(event -> {
            if (event.getValue() != null && !event.getValue().matches("^\\s*$")) {
                List<Service> matchingServices = configService.getConfig().getDefinedServices().stream()
                        .filter(service -> service.getHosts().stream()
                                .anyMatch(host -> host.getHost().contains(event.getValue())))
                        .collect(Collectors.toList());
                definedServices.setItems(matchingServices);
            } else {
                doAfterNavigationServices();
            }
        });
        filter.setValueChangeMode(ValueChangeMode.EAGER);
        filter.setLabel("Search for a host name");
        fieldRow.add(filter);

        Button cancelLastChange = new Button("Cancel Last Change");
        cancelLastChange.addClickListener(e -> jobRunner.cancelLastPendingServiceActivation());
        Button cancelAllChanges = new Button("Cancel All Changes");
        cancelAllChanges.addClickListener(e -> jobRunner.cancelSetDelayJobs());
        StreamResource download = new StreamResource("services.json", () -> configService.exportDefinedServices());
        Anchor export = new Anchor(download, "");
        Button exportButton = new Button("Export Configuration");
        export.add(exportButton);
        export.setTarget("_blank");
        export.getElement().setAttribute("download", "services.json");
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setDropAllowed(false);
        upload.setUploadButton(new Button("Import Configuration"));
        upload.addSucceededListener(e -> {
            if (configService.importDefinedServices(buffer.getInputStream())) {
                doAfterNavigationServices();
            } else {
                showNotification("Invalid input file format");
            }
        });
        fieldRow.add(cancelLastChange, cancelAllChanges, export, upload);
        fieldRow.setVerticalComponentAlignment(Alignment.END, cancelLastChange);
        fieldRow.setVerticalComponentAlignment(Alignment.END, cancelAllChanges);
        fieldRow.setVerticalComponentAlignment(Alignment.END, export);
        fieldRow.setVerticalComponentAlignment(Alignment.END, upload);

        H3 title = new H3("How Whitelisting Works");
        Label description = new Label("Here you can whitelist websites and collections of hosts, grouping them together under custom categories called services. Activation is controlled by the current delay, and if you made a mistake and don't want to activate a service as-is, simply use the buttons to cancel the pending change. There are two types of whitelisting, standard and passthrough. In standard, traffic passes through the filter and is inspected. If you wrote a rule to allow A.com, standard mode allows the filter to inspect the HTML and javascript of A.com and allow images and material from B.com without you having to write a rule for B.com. Passthrough mode typically should not be used for websites, but instead for desktop programs that may have issues with the certificates the filter presents to applications on your computer. You should only try passthrough mode if standard mode doesn't work with an application. Passthrough mode allows limited wildcard syntax such as (.*).example.com");

        searchBar.add(title, description, fieldRow, new Hr());
        root.add(searchBar);

        HorizontalLayout splitLayout = new HorizontalLayout();
        splitLayout.setSizeFull();
        primary.getStyle().set("width", "50%");
        secondary.getStyle().set("width", "50%");
        splitLayout.add(primary, secondary);

        root.add(splitLayout);
        return root;
    }

    private Div generateAppsPage() {
        Div root = new Div();
        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.add(new H3("Whitelisted Processes"));
        verticalLayout.add(new Label("Most non-browser processes (e.g. Microsoft Word, video games, etc) can be safely trusted, as they only communicate with a fixed set of domains. Rather than write and keep up with many individual rules for the domains a process talks to, simply whitelist a process here. You don't have to specify the full path to the process, you can simply provide a path to a folder and all programs under that folder will be allowed through the filter."));
        verticalLayout.add(whitelistedPaths);
        verticalLayout.add(new H3("Blacklisted Processes"));
        verticalLayout.add(new Label("Any processes that you want to block completely can be defined here"));
        verticalLayout.add(blacklistedPaths);
        root.add(verticalLayout);
        return root;
    }

    private void initBlacklistedPathsGrid() {
        Grid.Column<Path> nameColumn = blacklistedPaths.addColumn(Path::getPath).setHeader("Path");
        blacklistedPaths.addComponentColumn(phrase -> {
            Span active = new Span();
            if (phrase.getCurrentStatus() != null) {
                if (phrase.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    active.setText("Active");
                    active.getElement().setAttribute("theme", "badge success");
                } else if (phrase.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    active.setText("Reactivate");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (phrase.getCurrentStatus() == ActivationStatus.PENDING_DELETE) {
                    active.setText("Pending Delete");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (phrase.getCurrentStatus() == ActivationStatus.PENDING_DEACTIVATION) {
                    active.setText("Pending Deactivation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else {
                    active.setText("Disabled");
                    active.getElement().setAttribute("theme", "badge contrast");
                }
            }
            return active;
        }).setHeader("Status");

        blacklistedPaths.addComponentColumn(path -> {
            if (!addNewPathBlacklist.getPath().equals(path.getPath())) {
                Button button;
                if (path.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    button = new Button("Deactivate");
                    button.addClickListener(e -> {
                        DeactivateBlacklistedPathJob job = new DeactivateBlacklistedPathJob(
                                LocalDateTime.now().plusSeconds(configService.getDelaySeconds()),
                                "Deactivate Path: " + path.getPath(), path);
                        if (!jobRunner.queueJob(job)) {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findBlacklistedPathById(config, path.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DEACTIVATION)));
                            showNotification("The change will take effect after the current delay");
                        }
                        blacklistedPaths.getDataProvider().refreshItem(path);
                    });
                } else if (path.getCurrentStatus() == ActivationStatus.DISABLED
                        || path.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    button = new Button("Activate");
                    button.addClickListener(e -> {
                        // expecting configService to modify same instance used by UI
                        configService.withTransaction(config ->
                                ConfigUtils.findBlacklistedPathById(config, path.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.ACTIVE)));
                        configService.withProcessTransaction(config -> config.getBlacklistedPaths().add(path.getPath()));
                        path.updateCurrentActivationStatus(ActivationStatus.ACTIVE);
                        blacklistedPaths.getDataProvider().refreshItem(path);
                    });
                } else {
                    button = new Button("Cancel");
                    button.addClickListener(e -> {
                        configService.withTransaction(config ->
                                ConfigUtils.findBlacklistedPathById(config, path.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(p.getLastActivationStatus())));
                        jobRunner.cancelPendingJobsForActivatable(path.getId());
                        blacklistedPaths.getDataProvider().refreshItem(path);
                    });
                }
                return button;
            } else {
                return new Span();
            }
        }).setHeader("Action");

        Editor<Path> editor = blacklistedPaths.getEditor();
        editor.setBinder(blacklistedPathBinder);
        editor.setBuffered(true);

        TextField pathName = new TextField();
        blacklistedPathBinder.forField(pathName).asRequired("*")
                .withValidator(Objects::nonNull, "*")
                .bind(Path::getPath, Path::setPath);
        nameColumn.setEditorComponent(pathName);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<Path> editorColumn = blacklistedPaths.addComponentColumn(path -> {
            if (addNewPathBlacklist.getPath().equals(path.getPath())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<Path> items = blacklistedPaths.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getPath().equals(addNewPathBlacklist.getPath())).collect(Collectors.toList());
                    Path newPhrase = new Path();
                    newPhrase.updateCurrentActivationStatus(ActivationStatus.DISABLED);
                    items.add(newPhrase);
                    items.add(addNewPathBlacklist);
                    blacklistedPaths.setItems(items);
                    editor.editItem(newPhrase);
                    pathName.focus();
                });
                add.setEnabled(!editor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    if (configService.getCurrentDelay() == Delay.ZERO) {
                        editor.editItem(path);
                        pathName.focus();
                    } else {
                        showNotification("Deactivate first to edit");
                    }
                });
                edit.setEnabled(!editor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    DeleteBlacklistedPathJob job = new DeleteBlacklistedPathJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Delete Path: " + path.getPath(), path);
                    if (path.getCurrentStatus().equals(ActivationStatus.DISABLED)) {
                        jobRunner.runJob(job);
                        doAfterNavigationBlacklistedPaths();
                    } else {
                        if (jobRunner.queueJob(job)) {
                            doAfterNavigationBlacklistedPaths();
                        } else {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findBlacklistedPathById(config, path.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DELETE)));
                            blacklistedPaths.getDataProvider().refreshItem(path);
                            showNotification("The change will take effect after the current delay");
                        }
                    }
                });
                editButtons.add(edit);
                editButtons.add(delete);
                buttons.add(delete);
                return buttons;
            }
        }).setHeader("Edit");

        editor.addOpenListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));
        editor.addCloseListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));

        Button saveGrid = new Button(new Icon(VaadinIcon.CHECK), e -> {
            BinderValidationStatus<Path> validate = blacklistedPathBinder.validate();
            if (validate.isOk()) {
                Path item = editor.getItem();
                String itemOldName = item.getPath();
                editor.save();
                configService.withTransaction(config -> {
                    // remove old, by filtering where name != old name
                    List<Path> updated = config.getBlacklistedPaths().stream()
                            .filter(phrase -> !phrase.getPath().equals(itemOldName)).collect(Collectors.toList());
                    // item is now the new one from editor.save
                    if (!updated.contains(item)) {
                        updated.add(item);
                    }
                    config.setBlacklistedPaths(updated);
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            Path edited = editor.getItem();
            editor.cancel();
            if (edited.getPath() == null || edited.getPath().isBlank()) {
                List<Path> items = blacklistedPaths.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                blacklistedPaths.setItems(items);
            }
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        blacklistedPaths.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    private void initWhitelistedPathsGrid() {
        Grid.Column<Path> nameColumn = whitelistedPaths.addColumn(Path::getPath).setHeader("Path");
        whitelistedPaths.addComponentColumn(path -> {
            Span active = new Span();
            if (path.getCurrentStatus() != null) {
                if (path.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    active.setText("Active");
                    active.getElement().setAttribute("theme", "badge success");
                } else if (path.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    active.setText("Reactivate");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (path.getCurrentStatus() == ActivationStatus.PENDING_DELETE) {
                    active.setText("Pending Delete");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (path.getCurrentStatus() == ActivationStatus.PENDING_DEACTIVATION) {
                    active.setText("Pending Deactivation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (path.getCurrentStatus() == ActivationStatus.PENDING_ACTIVATION) {
                    active.setText("Pending Activation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else {
                    active.setText("Disabled");
                    active.getElement().setAttribute("theme", "badge contrast");
                }
            }
            return active;
        }).setHeader("Status");

        whitelistedPaths.addComponentColumn(path -> {
            if (!addNewPathWhitelist.getPath().equals(path.getPath())) {
                Button button;
                if (path.getCurrentStatus() == ActivationStatus.ACTIVE)  {
                    button = new Button("Deactivate");
                    button.addClickListener(e -> {
                        UpdateWhitelistedPathJob job = new UpdateWhitelistedPathJob(LocalDateTime.now(), "Deactivate Path: " + path.getPath(), path, ActivationStatus.DISABLED);
                        jobRunner.runJob(job);
                        whitelistedPaths.getDataProvider().refreshItem(path);
                    });
                } else if (path.getCurrentStatus() == ActivationStatus.DISABLED
                        || path.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    button = new Button("Activate");
                    button.addClickListener(e -> {
                        UpdateWhitelistedPathJob job = new UpdateWhitelistedPathJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Activate Path: " + path.getPath(), path, ActivationStatus.ACTIVE);
                        if (!jobRunner.queueJob(job)) {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findWhitelistedPathById(config, path.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_ACTIVATION)));
                            showNotification("Job queued. The change will take effect after the current delay expires");
                        }
                        whitelistedPaths.getDataProvider().refreshItem(path);
                    });
                } else {
                    button = new Button("Cancel");
                    button.addClickListener(e -> {
                        configService.withTransaction(config ->
                                ConfigUtils.findWhitelistedPathById(config, path.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(p.getLastActivationStatus())));
                        jobRunner.cancelPendingJobsForActivatable(path.getId());
                        whitelistedPaths.getDataProvider().refreshItem(path);
                    });
                }
                return button;
            } else {
                return new Span();
            }
        }).setHeader("Action");

        Editor<Path> editor = whitelistedPaths.getEditor();
        editor.setBinder(whitelistedPathBinder);
        editor.setBuffered(true);

        TextField processName = new TextField();
        whitelistedPathBinder.forField(processName).asRequired("Mandatory field")
                .withValidator(Objects::nonNull, "Mandatory field")
                .bind(Path::getPath, Path::setPath);
        nameColumn.setEditorComponent(processName);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<Path> editorColumn = whitelistedPaths.addComponentColumn(path -> {
            if (addNewPathWhitelist.getPath().equals(path.getPath())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<Path> items = whitelistedPaths.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getPath().equals(addNewPathWhitelist.getPath())).collect(Collectors.toList());
                    Path newPath = new Path();
                    newPath.updateCurrentActivationStatus(ActivationStatus.DISABLED);
                    items.add(newPath);
                    items.add(addNewPathWhitelist);
                    whitelistedPaths.setItems(items);
                    editor.editItem(newPath);
                    processName.focus();
                });
                add.setEnabled(!editor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    editor.editItem(path);
                    processName.focus();
                });
                edit.setEnabled(!editor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    List<Path> items = whitelistedPaths.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                    items.remove(path);
                    whitelistedPaths.setItems(items);

                    DeleteWhitelistedPathJob job = new DeleteWhitelistedPathJob(LocalDateTime.now(), "Delete Path: " + path.getPath(), path);
                    jobRunner.runJob(job);
                });
                editButtons.add(edit);
                editButtons.add(delete);
                buttons.add(delete);
                return buttons;
            }
        }).setHeader("Edit");

        editor.addOpenListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));
        editor.addCloseListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));

        Button saveGrid = new Button(new Icon(VaadinIcon.CHECK), e -> {
            BinderValidationStatus<Path> validate = whitelistedPathBinder.validate();
            if (validate.isOk()) {
                Path item = editor.getItem();
                String itemOldName = item.getPath();
                editor.save();
                configService.withTransaction(config -> {
                    // remove old, by filtering where name != old name
                    List<Path> updated = config.getWhitelistedPaths().stream()
                            .filter(path -> !path.getPath().equals(itemOldName)).collect(Collectors.toList());
                    // item is now the new one from editor.save
                    if (!updated.contains(item)) {
                        updated.add(item);
                    }
                    config.setWhitelistedPaths(updated);
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            Path edited = editor.getItem();
            editor.cancel();
            if (edited.getPath() == null || edited.getPath().isBlank()) {
                List<Path> items = whitelistedPaths.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                whitelistedPaths.setItems(items);
            }
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        whitelistedPaths.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    private void initServicesGrid() {
        Grid.Column<Service> nameColumn = definedServices.addColumn(Service::getName).setHeader("Service");
        definedServices.addComponentColumn(service -> {
            Span active = new Span();
            if (service.getCurrentStatus() != null) {
                if (service.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    active.setText("Active");
                    active.getElement().setAttribute("theme", "badge success");
                } else if (service.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    active.setText("Reactivate");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (service.getCurrentStatus() == ActivationStatus.PENDING_DELETE) {
                    active.setText("Pending Delete");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (service.getCurrentStatus() == ActivationStatus.PENDING_DEACTIVATION) {
                    active.setText("Pending Deactivation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (service.getCurrentStatus() == ActivationStatus.PENDING_ACTIVATION) {
                    active.setText("Pending Activation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else {
                    active.setText("Disabled");
                    active.getElement().setAttribute("theme", "badge contrast");
                }
            }
            return active;
        }).setHeader("Status");

        definedServices.addComponentColumn(service -> {
            if (!addNewService.getName().equals(service.getName())) {
                Button button;
                if (service.getCurrentStatus() == ActivationStatus.ACTIVE)  {
                    button = new Button("Deactivate");
                    button.addClickListener(e -> {
                        UpdateServiceJob job = new UpdateServiceJob(LocalDateTime.now(), "Deactivate Service: " + service.getName(), service, ActivationStatus.DISABLED);
                        jobRunner.runJob(job);
                        definedServices.getDataProvider().refreshItem(service);
                    });
                } else if (service.getCurrentStatus() == ActivationStatus.DISABLED
                        || service.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    button = new Button("Activate");
                    button.addClickListener(e -> {
                        UpdateServiceJob job = new UpdateServiceJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Activate Service: " + service.getName(), service, ActivationStatus.ACTIVE);
                        if (!jobRunner.queueJob(job)) {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findServiceById(config, service.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_ACTIVATION)));
                            showNotification("Job queued. The change will take effect after the current delay expires");
                        }
                        definedServices.getDataProvider().refreshItem(service);
                    });
                } else {
                    button = new Button("Cancel");
                    button.addClickListener(e -> {
                        configService.withTransaction(config ->
                                ConfigUtils.findServiceById(config, service.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(p.getLastActivationStatus())));
                        jobRunner.cancelPendingJobsForActivatable(service.getId());
                        definedServices.getDataProvider().refreshItem(service);
                    });
                }
                return button;
            } else {
                return new Span();
            }
        }).setHeader("Action");

        Editor<Service> editor = definedServices.getEditor();
        editor.setBinder(serviceBinder);
        editor.setBuffered(true);

        TextField serviceName = new TextField();
        serviceBinder.forField(serviceName).asRequired("Mandatory field")
                .withValidator(Objects::nonNull, "Mandatory field")
                .bind(Service::getName, Service::setName);
        nameColumn.setEditorComponent(serviceName);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<Service> editorColumn = definedServices.addComponentColumn(service -> {
            if (addNewService.getName().equals(service.getName())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<Service> items = definedServices.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getName().equals(addNewService.getName())).collect(Collectors.toList());
                    Service newService = new Service();
                    newService.updateCurrentActivationStatus(ActivationStatus.DISABLED);
                    items.add(newService);
                    items.add(addNewService);
                    definedServices.setItems(items);
                    editor.editItem(newService);
                    serviceName.focus();
                });
                add.setEnabled(!editor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    editor.editItem(service);
                    serviceName.focus();
                });
                edit.setEnabled(!editor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    List<Service> items = definedServices.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                    items.remove(service);
                    definedServices.setItems(items);

                    hosts.setItems(Collections.emptyList());

                    DeleteServiceJob job = new DeleteServiceJob(LocalDateTime.now(), "Deactivate Service: " + service.getName(), service);
                    jobRunner.runJob(job);
                });
                editButtons.add(edit);
                editButtons.add(delete);
                buttons.add(delete);
                return buttons;
            }
        }).setHeader("Edit");

        editor.addOpenListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));
        editor.addCloseListener(e -> editButtons.forEach(button -> button.setEnabled(!editor.isOpen())));

        Button saveGrid = new Button(new Icon(VaadinIcon.CHECK), e -> {
            BinderValidationStatus<Service> validate = serviceBinder.validate();
            if (validate.isOk()) {
                Service item = editor.getItem();
                String itemOldName = item.getName();
                editor.save();
                configService.withTransaction(config -> {
                    // remove old, by filtering where name != old name
                    List<Service> updated = config.getDefinedServices().stream()
                            .filter(service -> !service.getName().equals(itemOldName)).collect(Collectors.toList());
                    // item is now the new one from editor.save
                    if (!updated.contains(item)) {
                        updated.add(item);
                    }
                    config.setDefinedServices(updated);
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            Service edited = editor.getItem();
            editor.cancel();
            if (edited.getName() == null || edited.getName().isBlank()) {
                List<Service> items = definedServices.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                definedServices.setItems(items);
            }
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        definedServices.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);

        definedServices.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() == null)  {
                hosts.setItems(Collections.emptyList());
            } else if (!addNewService.getName().equals(e.getValue().getName())) {
                if (hostsEditor != null && hostsEditor.isOpen()) {
                    hostsEditor.cancel();
                }
                ArrayList<Host> items = new ArrayList<>(e.getValue().getHosts());
                items.sort(comparing(Host::getHost, nullsLast(String.CASE_INSENSITIVE_ORDER)));
                items.add(addNewHost);
                hosts.setItems(items);
            }
        });
    }

    private void initHostsGrid() {
        Grid.Column<Host> hostColumn = hosts.addColumn(Host::getHost).setHeader("Host");
        Grid.Column<Host> bypassColumn = hosts.addColumn(Host::getFilterMode).setHeader("Filter Mode");

        hostsEditor = hosts.getEditor();
        hostsEditor.setBinder(hostBinder);
        hostsEditor.setBuffered(true);

        TextField hostName = new TextField();
        hostBinder.forField(hostName).asRequired("*").withValidator(Objects::nonNull, "*")
                .bind(Host::getHost, Host::setHost);
        hostColumn.setEditorComponent(hostName);

        ComboBox<FilterMode> bypassType = new ComboBox<>();
        bypassType.setItems(FilterMode.values());
        hostBinder.forField(bypassType).asRequired("*").withValidator(Objects::nonNull, "*")
                .bind(Host::getFilterMode, Host::setFilterMode);
        bypassColumn.setEditorComponent(bypassType);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<Host> editorColumn = hosts.addComponentColumn(host -> {
            if (addNewHost.getHost().equals(host.getHost())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<Host> items = hosts.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getHost().equals(addNewHost.getHost())).collect(Collectors.toList());
                    Host newHost = new Host();
                    items.add(newHost);
                    List<Host> combinedItems = new ArrayList<>(items);
                    combinedItems.add(addNewHost);
                    hosts.setItems(combinedItems);
                    hostsEditor.editItem(newHost);
                    hostName.focus();
                });
                add.setEnabled(!hostsEditor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    hostsEditor.editItem(host);
                    hostName.focus();
                });
                edit.setEnabled(!hostsEditor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    List<Host> items = hosts.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                    items.remove(host);
                    hosts.setItems(items);
                    // need the service change to hit at the file level before running the below
                    configService.withTransaction(config -> {
                        Service current = definedServices.asSingleSelect().getValue();
                        current.getHosts().remove(host);
                    });

                    // the add 1, remove 1 case.
                    // adding 1 means we would be restarting the timer
                    // if we then come along and delete 1, we can't just run the activate service job or it puts
                    // in the pending change from the add. We'd need a granularity change to have hosts in a service
                    // have individual activation times to instantly handle that case, rather than a group based
                    // service activation time. So we can only instantly process a host deletion if the service was
                    // active with no other pending additions. Otherwise, if it was already pending reactivation
                    // the change will be picked up on activation, and if it is disabled leave it that way
                    Service current = definedServices.asSingleSelect().getValue();
                    if (current.getCurrentStatus() == ActivationStatus.ACTIVE) {
                        UpdateServiceJob job = new UpdateServiceJob(LocalDateTime.now(), "Activate Service: " + current.getName(), current, ActivationStatus.ACTIVE);
                        jobRunner.runJob(job);
                    }
                });
                editButtons.add(edit);
                editButtons.add(delete);
                buttons.add(delete);
                return buttons;
            }
        }).setHeader("Edit");

        hostsEditor.addOpenListener(e -> editButtons.forEach(button -> button.setEnabled(!hostsEditor.isOpen())));
        hostsEditor.addCloseListener(e -> editButtons.forEach(button -> button.setEnabled(!hostsEditor.isOpen())));

        Button saveGrid = new Button(new Icon(VaadinIcon.CHECK), e -> {
            BinderValidationStatus<Host> validate = hostBinder.validate();
            if (validate.isOk()) {
                Host editorItem = hostsEditor.getItem();
                hostsEditor.save();
                Service current = definedServices.asSingleSelect().getValue();
                // vaadin has a strange bug where we can't define equals and hashcode on Host, or else the fields in
                // the UI won't close
                // here we ensure that there is only ever 1 row for a given hostname, by removing existing host
                // and adding the new one. This captures the case where a host existed, but instead of editing it directly
                // the user created a new host with the same name but a different filter mode
                int startingSize = current.getHosts().size();
                current.setHosts(current.getHosts().stream().filter(host -> !host.getHost().equals(editorItem.getHost())).collect(Collectors.toList()));
                boolean isDup = current.getHosts().size() < startingSize;
                if (isDup) {
                    List<Host> properItems = hosts.getDataProvider().fetch(new Query<>()).filter(host -> !host.getHost().equals(editorItem.getHost())
                            && !host.getHost().equals(addNewHost.getHost())).collect(Collectors.toList());
                    properItems.add(editorItem);
                    properItems.add(addNewHost);
                    hosts.setItems(properItems);
                }
                current.getHosts().add(editorItem);

                // since UI items are disconnected from backend, match based on name,
                // remove if present, and add new
                configService.withTransaction(config -> {
                    ConfigUtils.findServiceById(config, current.getId())
                            .ifPresent(obj -> {
                                obj.setHosts(obj.getHosts().stream().filter(host -> !host.getHost().equals(editorItem.getHost())).collect(Collectors.toList()));
                                obj.getHosts().add(editorItem);
                            });
                    if (current.getCurrentStatus() == ActivationStatus.ACTIVE) {
                        current.updateCurrentActivationStatus(ActivationStatus.NEEDS_REACTIVATION);
                        definedServices.getDataProvider().refreshItem(current);
                    }
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            Host edited = hostsEditor.getItem();
            hostsEditor.cancel();
            if (edited.getHost() == null || edited.getHost().isBlank()
                || edited.getFilterMode() == null) {
                List<Host> items = hosts.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                hosts.setItems(items);
            }
            hostsEditor.cancel();
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        hosts.getElement().addEventListener("keyup", event -> hostsEditor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    private Component createPrimaryFormLayout() {
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setSizeFull();
        formLayout.add(new Label("Define a grouping of hosts for a program or website, which you can toggle on or off as a unit"));
        formLayout.add(definedServices);
        return formLayout;
    }

    private Component createFormLayout() {
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setSizeFull();
        formLayout.add(new Label("Define individual IP addresses or hosts that this website or program uses."));
        formLayout.add(hosts);
        return formLayout;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent afterNavigationEvent) {
        doAfterNavigationServices();
        doAfterNavigationWhitelistedPaths();
        doAfterNavigationBlacklistedPaths();
    }

    private void doAfterNavigationServices() {
        List<Service> items = configService.getConfig().getDefinedServices();
        List<Service> uiSafeItems = items.stream().filter(i -> !addNewService.getName().equals(i.getName()))
                .sorted(comparing(Service::getName, nullsLast(String.CASE_INSENSITIVE_ORDER))).collect(Collectors.toList());
        uiSafeItems.add(addNewService);
        definedServices.setItems(uiSafeItems);
    }

    private void doAfterNavigationWhitelistedPaths() {
        List<Path> pathItems = configService.getConfig().getWhitelistedPaths();
        List<Path> uiSafePaths = pathItems.stream().filter(i -> !addNewPathWhitelist.getPath().equals(i.getPath()))
                .sorted(comparing(Path::getPath, nullsLast(String.CASE_INSENSITIVE_ORDER))).collect(Collectors.toList());
        uiSafePaths.add(addNewPathWhitelist);
        whitelistedPaths.setItems(uiSafePaths);
    }

    private void doAfterNavigationBlacklistedPaths() {
        List<Path> pathItems = configService.getConfig().getBlacklistedPaths();
        List<Path> uiSafePaths = pathItems.stream().filter(i -> !addNewPathBlacklist.getPath().equals(i.getPath()))
                .sorted(comparing(Path::getPath, nullsLast(String.CASE_INSENSITIVE_ORDER))).collect(Collectors.toList());
        uiSafePaths.add(addNewPathBlacklist);
        blacklistedPaths.setItems(uiSafePaths);
    }

    private void showNotification(String message) {
        if (notification != null && notification.isOpened()) {
            notification.close();
        }
        notification = new Notification(message, 5000, Notification.Position.BOTTOM_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        notification.open();
    }
}

