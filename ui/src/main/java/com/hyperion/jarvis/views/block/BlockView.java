package com.hyperion.jarvis.views.block;


import com.hyperion.jarvis.backend.model.config.ActivationStatus;
import com.hyperion.jarvis.backend.model.config.BlockedHost;
import com.hyperion.jarvis.backend.model.config.Delay;
import com.hyperion.jarvis.backend.model.config.Phrase;
import com.hyperion.jarvis.backend.model.config.job.*;
import com.hyperion.jarvis.backend.services.ConfigService;
import com.hyperion.jarvis.backend.services.ConfigUtils;
import com.hyperion.jarvis.backend.services.JobRunner;
import com.hyperion.jarvis.views.main.MainView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.BinderValidationStatus;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsLast;


@Route(value = "block", layout = MainView.class)
@PageTitle("Block Websites and Phrases")
@CssImport(value = "./styles/views/block/block-view.css", include = "lumo-badge")
@JsModule("@vaadin/vaadin-lumo-styles/badge.js")
public class BlockView extends VerticalLayout implements AfterNavigationObserver {

    private static final Logger log = LoggerFactory.getLogger(BlockView.class);

    private ConfigService configService;
    private JobRunner jobRunner;

    private final Grid<Phrase> blockPhrases;
    private final Binder<Phrase> phraseBinder;

    private final Grid<BlockedHost> blockedHosts;
    private final Binder<BlockedHost> hostBinder;

    private final BlockedHost addNewHost;
    private final Phrase addNewPhrase;

    Notification notification;

    @Autowired
    private void setApplicationContext(ApplicationContext applicationContext) {
        this.configService = applicationContext.getBean(ConfigService.class);
        this.jobRunner = applicationContext.getBean(JobRunner.class);
    }

    public BlockView() {
        setSizeFull();
        setPadding(true);
        blockPhrases = new Grid<>();
        phraseBinder = new Binder<>(Phrase.class);
        addNewPhrase = new Phrase();
        addNewPhrase.setPhrase("Add new");
        initPhrasesGrid();

        blockedHosts = new Grid<>();
        hostBinder = new Binder<>(BlockedHost.class);
        addNewHost = new BlockedHost();
        addNewHost.setHost("Add new");
        initHostsGrid();

        add(new H3("Lockdown"));
        HorizontalLayout lockdownRow = new HorizontalLayout();
        lockdownRow.setPadding(false);
        lockdownRow.setSpacing(true);
        Button lockdownButton = new Button("Activate Lockdown");
        lockdownButton.getStyle().set("min-width", "fit-content");
        lockdownButton.addClickListener(e -> {
            configService.withFilterTransaction(filterConfig -> filterConfig.setLockdownActive(true));
            DisableLockdownJob job = new DisableLockdownJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Disable lockdown");
            jobRunner.queueJob(job);
        });
        lockdownRow.add(lockdownButton);
        Label lockdownDescription = new Label("If you find that you've discovered some way around your current filters, and you just need to block everything for a minute, this button allows you to do that. All images and videos will be blocked, no matter if they are currently whitelisted. The lockdown will automatically expire after the current delay");
        lockdownRow.add(lockdownDescription);
        add(lockdownRow);

        add(new H3("Blocked Hosts"));
        add(new Label("Here you can define specific hosts or IP addresses to block, if you find that having images and video blocked from them is not enough"));
        blockedHosts.setWidthFull();
        blockedHosts.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        add(blockedHosts);

        add(new H3("Blocked Phrases"));
        add(new Label("If you want to allow a site but block certain parts of it, you can define phrases to block. For example, maybe you want to allow youtube.com but block age-restricted videos. You could add a phrase \"age-restricted video\" to do that. Of note, phrases are case-insensitive in their application: they can safely be written as lowercase and will always match."));
        blockPhrases.setWidthFull();
        blockPhrases.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        add(blockPhrases);
    }

    private void initPhrasesGrid() {
        Grid.Column<Phrase> nameColumn = blockPhrases.addColumn(Phrase::getPhrase).setHeader("Phrase");
        blockPhrases.addComponentColumn(phrase -> {
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

        blockPhrases.addComponentColumn(phrase -> {
            if (!addNewPhrase.getPhrase().equals(phrase.getPhrase())) {
                Button button;
                if (phrase.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    button = new Button("Deactivate");
                    button.addClickListener(e -> {
                        DeactivatePhraseJob job = new DeactivatePhraseJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Deactivate Phrase: " + phrase.getPhrase(), phrase);
                        if (!jobRunner.queueJob(job)) {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findPhraseById(config, phrase.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DEACTIVATION)));
                            showNotification("The change will take effect after the current delay");
                        }
                        blockPhrases.getDataProvider().refreshItem(phrase);
                    });
                } else if (phrase.getCurrentStatus() == ActivationStatus.DISABLED
                        || phrase.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    button = new Button("Activate");
                    button.addClickListener(e -> {
                        // expecting configService to modify same instance used by UI
                        configService.withTransaction(config ->
                                ConfigUtils.findPhraseById(config, phrase.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.ACTIVE)));
                        configService.withFilterTransaction(filterConfig -> filterConfig.getBlockedPhrases().add(phrase.getPhrase()));
                        phrase.updateCurrentActivationStatus(ActivationStatus.ACTIVE);
                        blockPhrases.getDataProvider().refreshItem(phrase);
                    });
                } else {
                    button = new Button("Cancel");
                    button.addClickListener(e -> {
                        configService.withTransaction(config ->
                                ConfigUtils.findPhraseById(config, phrase.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(p.getLastActivationStatus())));
                        jobRunner.cancelPendingJobsForActivatable(phrase.getId());
                        blockPhrases.getDataProvider().refreshItem(phrase);
                    });
                }
                return button;
            } else {
                return new Span();
            }
        }).setHeader("Action");

        Editor<Phrase> editor = blockPhrases.getEditor();
        editor.setBinder(phraseBinder);
        editor.setBuffered(true);

        TextField phraseName = new TextField();
        phraseBinder.forField(phraseName).asRequired("*")
                .withValidator(Objects::nonNull, "*")
                .bind(Phrase::getPhrase, Phrase::setPhrase);
        nameColumn.setEditorComponent(phraseName);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<Phrase> editorColumn = blockPhrases.addComponentColumn(phrase -> {
            if (addNewPhrase.getPhrase().equals(phrase.getPhrase())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<Phrase> items = blockPhrases.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getPhrase().equals(addNewPhrase.getPhrase())).collect(Collectors.toList());
                    Phrase newPhrase = new Phrase();
                    newPhrase.updateCurrentActivationStatus(ActivationStatus.DISABLED);
                    items.add(newPhrase);
                    items.add(addNewPhrase);
                    blockPhrases.setItems(items);
                    editor.editItem(newPhrase);
                    phraseName.focus();
                });
                add.setEnabled(!editor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    if (configService.getCurrentDelay() == Delay.ZERO) {
                        editor.editItem(phrase);
                        phraseName.focus();
                    } else {
                        showNotification("Deactivate first to edit");
                    }
                });
                edit.setEnabled(!editor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    DeletePhraseJob job = new DeletePhraseJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Delete Phrase: " + phrase.getPhrase(), phrase);
                    if (phrase.getCurrentStatus().equals(ActivationStatus.DISABLED)) {
                        jobRunner.runJob(job);
                        doAfterNavigationPhrases();
                    } else {
                        if (jobRunner.queueJob(job)) {
                            doAfterNavigationPhrases();
                        } else {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findPhraseById(config, phrase.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DELETE)));
                            blockPhrases.getDataProvider().refreshItem(phrase);
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
            BinderValidationStatus<Phrase> validate = phraseBinder.validate();
            if (validate.isOk()) {
                Phrase item = editor.getItem();
                String itemOldName = item.getPhrase();
                editor.save();
                configService.withTransaction(config -> {
                    // remove old, by filtering where name != old name
                    List<Phrase> updated = config.getDefinedPhrases().stream()
                            .filter(phrase -> !phrase.getPhrase().equals(itemOldName)).collect(Collectors.toList());
                    // item is now the new one from editor.save
                    if (!updated.contains(item)) {
                        updated.add(item);
                    }
                    config.setDefinedPhrases(updated);
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            Phrase edited = editor.getItem();
            editor.cancel();
            if (edited.getPhrase() == null || edited.getPhrase().isBlank()) {
                List<Phrase> items = blockPhrases.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                blockPhrases.setItems(items);
            }
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        blockPhrases.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    private void initHostsGrid() {
        Grid.Column<BlockedHost> nameColumn = blockedHosts.addColumn(BlockedHost::getHost).setHeader("Host");
        blockedHosts.addComponentColumn(host -> {
            Span active = new Span();
            if (host.getCurrentStatus() != null) {
                if (host.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    active.setText("Active");
                    active.getElement().setAttribute("theme", "badge success");
                } else if (host.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    active.setText("Reactivate");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (host.getCurrentStatus() == ActivationStatus.PENDING_DELETE) {
                    active.setText("Pending Delete");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else if (host.getCurrentStatus() == ActivationStatus.PENDING_DEACTIVATION) {
                    active.setText("Pending Deactivation");
                    active.getElement().setAttribute("theme", "badge contrast");
                } else {
                    active.setText("Disabled");
                    active.getElement().setAttribute("theme", "badge contrast");
                }
            }
            return active;
        }).setHeader("Status");

        blockedHosts.addComponentColumn(host -> {
            if (!addNewHost.getHost().equals(host.getHost())) {
                Button button;
                if (host.getCurrentStatus() == ActivationStatus.ACTIVE) {
                    button = new Button("Deactivate");
                    button.addClickListener(e -> {
                        DeactivateHostJob job = new DeactivateHostJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Deactivate host: " + host.getHost(), host);
                        if (!jobRunner.queueJob(job)) {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findHostById(config, host.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DEACTIVATION)));
                            showNotification("The change will take effect after the current delay");
                        }
                        blockedHosts.getDataProvider().refreshItem(host);
                    });
                } else if (host.getCurrentStatus() == ActivationStatus.DISABLED
                        || host.getCurrentStatus() == ActivationStatus.NEEDS_REACTIVATION) {
                    button = new Button("Activate");
                    button.addClickListener(e -> {
                        // expecting configService to modify same instance used by UI
                        configService.withTransaction(config ->
                                ConfigUtils.findHostById(config, host.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.ACTIVE)));
                        configService.withFilterTransaction(filterConfig -> filterConfig.getBlockedHosts().add(host.getHost()));
                        host.updateCurrentActivationStatus(ActivationStatus.ACTIVE);
                        blockedHosts.getDataProvider().refreshItem(host);
                    });
                } else {
                    button = new Button("Cancel");
                    button.addClickListener(e -> {
                        configService.withTransaction(config ->
                                ConfigUtils.findHostById(config, host.getId())
                                        .ifPresent(p -> p.updateCurrentActivationStatus(p.getLastActivationStatus())));
                        jobRunner.cancelPendingJobsForActivatable(host.getId());
                        blockedHosts.getDataProvider().refreshItem(host);
                    });
                }
                return button;
            } else {
                return new Span();
            }
        }).setHeader("Action");

        Editor<BlockedHost> editor = blockedHosts.getEditor();
        editor.setBinder(hostBinder);
        editor.setBuffered(true);

        TextField hostName = new TextField();
        hostBinder.forField(hostName).asRequired("*")
                .withValidator(Objects::nonNull, "*")
                .bind(BlockedHost::getHost, BlockedHost::setHost);
        nameColumn.setEditorComponent(hostName);

        Collection<Button> editButtons = Collections
                .newSetFromMap(new WeakHashMap<>());

        Grid.Column<BlockedHost> editorColumn = blockedHosts.addComponentColumn(host -> {
            if (addNewHost.getHost().equals(host.getHost())) {
                Button add = new Button(new Icon(VaadinIcon.PLUS));
                add.addClassName("edit");
                add.addClickListener(e -> {
                    List<BlockedHost> items = blockedHosts.getDataProvider().fetch(new Query<>())
                            .filter(item -> !item.getHost().equals(addNewHost.getHost())).collect(Collectors.toList());
                    BlockedHost newHost = new BlockedHost();
                    newHost.updateCurrentActivationStatus(ActivationStatus.DISABLED);
                    items.add(newHost);
                    items.add(addNewHost);
                    blockedHosts.setItems(items);
                    editor.editItem(newHost);
                    hostName.focus();
                });
                add.setEnabled(!editor.isOpen());
                editButtons.add(add);
                return add;
            } else {
                Div buttons = new Div();
                Button edit = new Button(new Icon(VaadinIcon.EDIT));
                edit.addClickListener(e -> {
                    if (configService.getCurrentDelay() == Delay.ZERO) {
                        editor.editItem(host);
                        hostName.focus();
                    } else {
                        showNotification("Deactivate first to edit");
                    }
                });
                edit.setEnabled(!editor.isOpen());
                buttons.add(edit);
                Button delete = new Button(new Icon(VaadinIcon.CLOSE));
                delete.addClickListener(e -> {
                    DeleteHostJob job = new DeleteHostJob(LocalDateTime.now().plusSeconds(configService.getDelaySeconds()), "Delete host: " + host.getHost(), host);
                    if (host.getCurrentStatus().equals(ActivationStatus.DISABLED)) {
                        jobRunner.runJob(job);
                        doAfterNavigationHosts();
                    } else {
                        if (jobRunner.queueJob(job)) {
                            doAfterNavigationHosts();
                        } else {
                            // expecting configService to modify same instance used by UI
                            configService.withTransaction(config ->
                                    ConfigUtils.findHostById(config, host.getId())
                                            .ifPresent(p -> p.updateCurrentActivationStatus(ActivationStatus.PENDING_DELETE)));
                            blockedHosts.getDataProvider().refreshItem(host);
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
            BinderValidationStatus<BlockedHost> validate = hostBinder.validate();
            if (validate.isOk()) {
                BlockedHost item = editor.getItem();
                String itemOldName = item.getHost();
                editor.save();
                configService.withTransaction(config -> {
                    // remove old, by filtering where name != old name
                    List<BlockedHost> updated = config.getBlockedHosts().stream()
                            .filter(host -> !host.getHost().equals(itemOldName)).collect(Collectors.toList());
                    // item is now the new one from editor.save
                    if (!updated.contains(item)) {
                        updated.add(item);
                    }
                    config.setBlockedHosts(updated);
                });
            }
        });

        Button cancel = new Button(new Icon(VaadinIcon.CLOSE), e -> {
            BlockedHost edited = editor.getItem();
            editor.cancel();
            if (edited.getHost() == null || edited.getHost().isBlank()) {
                List<BlockedHost> items = blockedHosts.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
                items.remove(edited);
                blockedHosts.setItems(items);
            }
        });

        // Add a keypress listener that listens for an escape key up event.
        // Note! some browsers return key as Escape and some as Esc
        blockedHosts.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");

        Div buttons = new Div(saveGrid, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent afterNavigationEvent) {
        doAfterNavigation();
    }

    private void doAfterNavigation() {
        doAfterNavigationPhrases();
        doAfterNavigationHosts();
    }

    private void doAfterNavigationPhrases() {
        List<Phrase> items = configService.getConfig().getDefinedPhrases();
        List<Phrase> uiSafeItems = items.stream().filter(i -> !addNewPhrase.getPhrase().equals(i.getPhrase()))
                .sorted(comparing(Phrase::getPhrase, nullsLast(String.CASE_INSENSITIVE_ORDER))).collect(Collectors.toList());
        uiSafeItems.add(addNewPhrase);
        blockPhrases.setItems(uiSafeItems);
    }

    private void doAfterNavigationHosts() {
        List<BlockedHost> hostItems = configService.getConfig().getBlockedHosts();
        List<BlockedHost> uiSafeHosts = hostItems.stream().filter(i -> !addNewHost.getHost().equals(i.getHost()))
                .sorted(comparing(BlockedHost::getHost, nullsLast(String.CASE_INSENSITIVE_ORDER))).collect(Collectors.toList());
        uiSafeHosts.add(addNewHost);
        blockedHosts.setItems(uiSafeHosts);
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

