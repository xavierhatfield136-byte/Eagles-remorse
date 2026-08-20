package app.ui;

import app.config.GameConfig;
import app.config.GameMode;
import app.config.ExperienceSettings;
import app.config.MultiplayerLaunchConfig;
import app.config.MultiplayerMissionChoice;
import app.config.PlayerTeamChoice;
import app.config.PostAlphaFeatureFlags;
import app.persistence.ExperienceSettingsStore;
import app.persistence.MenuSettingsStore;
import app.support.AppInfo;
import app.support.MenuDisplay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public final class MainMenuPanel extends JPanel {
    private static final long MENU_BG_SEED = 0x5A17C0DEL;
    private static final String NO_CHECKPOINT_MESSAGE =
            "No active campaign. Begin a campaign to establish your fleet.";
    private static final String[] CAMPAIGN_SLOT_IDS = {"slot-1", "slot-2", "slot-3"};
    private static final String[] CAMPAIGN_SLOT_LABELS = {"Save 1", "Save 2", "Save 3"};
    private static final String[] CUSTOM_BATTLE_ROLE_IDS = {
            "PICKET", "PATROL", "STEALTH_SHIP", "FIGHTER", "BOMBER", "PD_CRAFT", "DRONE",
            "FRIGATE", "ARTILLERY_SHIP", "MISSILE_BOAT", "CIWS_CORVETTE", "LIGHT_CRUISER",
            "MEDIUM_CRUISER", "CRUISER", "BATTLECRUISER", "BATTLESHIP", "DREADNOUGHT",
            "SUPERSHIP", "TRANSPORT_TITAN", "BULWARK_TITAN", "CARRIER_SUPPORT_TITAN",
            "VANGUARD_TITAN", "INTERDICTION_TITAN", "COMMAND_INTEL_TITAN",
            "BOARDING_RECOVERY_TITAN", "ARTILLERY_TITAN", "SHIELD_BASTION_TITAN",
            "FLEET_TELEPORTER_TITAN", "ELITE_SUPERSHIP_COMMAND_TITAN",
            "ELITE_REINFORCEMENTS_TITAN", "MOBILE_STATION_TITAN", "HYPERWEAPON_TITAN",
            "MOTHERSHIP", "CARRIER", "DRONE_CARRIER", "TRANSPORT", "MINER", "HAULER",
            "BASE", "STATIC_TURRET"
    };
    private final long backgroundStartNs = System.nanoTime();
    private long lastBattleUpdateNs = backgroundStartNs;
    private final Timer backgroundTimer;
    private MenuBattleView menuBattleView;
    private final JButton continueCampaignButton;
    private final JButton deleteCampaignButton;
    private final JLabel continueCampaignLabel;
    private final JButton[] campaignSlotButtons = new JButton[CAMPAIGN_SLOT_IDS.length];
    private final JButton[] campaignSlotDeleteButtons = new JButton[CAMPAIGN_SLOT_IDS.length];
    private final JLabel[] campaignSlotLabels = new JLabel[CAMPAIGN_SLOT_IDS.length];
    private final ResumeCampaignProvider resumeCampaignProvider;
    private final SpaceBackgroundPainter spaceBackgroundPainter;

    public MainMenuPanel(Consumer<GameConfig> onStart,
                         Runnable onCredits,
                         Runnable onQuit,
                         ResumeCampaignProvider resumeCampaignProvider,
                         SpaceBackgroundPainter spaceBackgroundPainter) {
        this.resumeCampaignProvider = resumeCampaignProvider;
        this.spaceBackgroundPainter = spaceBackgroundPainter;
        Dimension preferredSize = MenuDisplay.preferredWindowSize();
        double uiScale = MenuDisplay.scaleFor(preferredSize);

        setPreferredSize(preferredSize);
        setBackground(Color.BLACK);
        setFocusable(true);
        backgroundTimer = new Timer(33, e -> {
            long now = System.nanoTime();
            double delta = Math.min(0.08, Math.max(0.0, (now - lastBattleUpdateNs) / 1_000_000_000.0));
            lastBattleUpdateNs = now;
            if (menuBattleView != null && isShowing()) {
                menuBattleView.update(delta);
            }
            repaint();
        });
        backgroundTimer.setCoalesce(true);
        backgroundTimer.start();

        JLabel title = new JLabel(AppInfo.APP_NAME.toUpperCase());
        title.setForeground(Color.WHITE);
        title.setFont(MenuDisplay.font("Consolas", Font.BOLD, 44, uiScale));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        GameMode[] missionModes = new GameMode[]{
                GameMode.LAST_STAND,
                GameMode.RESOURCE_RUSH,
                GameMode.FOUR_TEAM_DOMINATION,
                GameMode.CUSTOM_BATTLES,
                GameMode.SHOOTING_RANGE,
                GameMode.SHOWCASE
        };
        JComboBox<GameMode> modeBox = new JComboBox<>(missionModes);
        styleCombo(modeBox);
        scaleCombo(modeBox, uiScale);

        JComboBox<String> mapBox = new JComboBox<>(new String[]{
                "Small (5000 x 5000)",
                "Medium (10000 x 10000)",
                "Large (20000 x 20000)"
        });
        styleCombo(mapBox);
        scaleCombo(mapBox, uiScale);
        JComboBox<PlayerTeamChoice> teamBox = new JComboBox<>();
        styleCombo(teamBox);
        scaleCombo(teamBox, uiScale);

        JTextField seedField = new JTextField("0", 12);
        styleField(seedField);
        scaleField(seedField, uiScale);

        JButton start = createMenuButton("Launch Mission", new Color(38, 105, 142), uiScale);
        JButton credits = createMenuButton("Credits", new Color(48, 62, 82), uiScale);
        JButton quit = createMenuButton("Quit", new Color(100, 63, 73), uiScale);
        continueCampaignButton = createMenuButton("Continue Campaign", new Color(63, 134, 91), uiScale);
        deleteCampaignButton = createMenuButton("Delete Save", new Color(117, 58, 70), uiScale);
        JButton newCampaign = createMenuButton("Campaign", new Color(25, 57, 82), uiScale);
        JButton campaignOps = createMenuButton("Open World Campaign", new Color(33, 86, 128), uiScale);
        JButton linearCampaign = createMenuButton("Linear Campaign", new Color(82, 91, 126), uiScale);
        JButton customBattle = createMenuButton("Custom Battle", new Color(30, 61, 88), uiScale);
        JButton multiplayerButton = createMenuButton("Multiplayer", new Color(28, 70, 72), uiScale);
        JButton customShipCreator = createMenuButton("Shipyard", new Color(34, 58, 78), uiScale);
        JButton galaxyMapTest = createMenuButton("Galaxy Map Test", new Color(72, 103, 150), uiScale);
        JButton tutorialStart = createMenuButton("Commander's Academy", new Color(31, 68, 102), uiScale);
        JButton settingsButton = createMenuButton("Settings", new Color(43, 53, 76), uiScale);
        JButton experienceButton = createMenuButton("Accessibility", new Color(64, 80, 116), uiScale);
        JButton controlsButton = createMenuButton("Controls", new Color(65, 91, 126), uiScale);
        newCampaign.setName("newCampaignButton");
        customBattle.setName("customBattleButton");
        multiplayerButton.setName("multiplayerButton");
        customShipCreator.setName("customShipCreatorButton");
        tutorialStart.setName("tutorialStartButton");
        settingsButton.setName("settingsButton");
        controlsButton.setName(InputBindingsDialog.CONTROLS_BUTTON_NAME);
        JButton backFromCampaign = createMenuButton("Back", new Color(48, 62, 82), uiScale);
        JButton backFromBattle = createMenuButton("Back", new Color(48, 62, 82), uiScale);
        JButton backFromMultiplayer = createMenuButton("Back", new Color(48, 62, 82), uiScale);
        JButton backFromSettings = createMenuButton("Back", new Color(48, 62, 82), uiScale);
        JButton backFromDeveloper = createMenuButton("Back", new Color(48, 62, 82), uiScale);
        JButton hostBattle = createMenuButton("Create Lobby", new Color(53, 123, 126), uiScale);
        JButton joinBattle = createMenuButton("Join Lobby", new Color(79, 102, 151), uiScale);
        JButton diagnosticsBattle = createMenuButton("Diagnostics", new Color(86, 77, 122), uiScale);
        hostBattle.setName("multiplayerHostBattleButton");
        joinBattle.setName("multiplayerJoinBattleButton");
        diagnosticsBattle.setName("multiplayerDiagnosticsButton");
        JTextField directAddressField = new JTextField("127.0.0.1:46717", 16);
        directAddressField.setName("multiplayerDirectAddressField");
        styleField(directAddressField);
        scaleField(directAddressField, uiScale);
        JTextField multiplayerNameField = new JTextField("Player", 16);
        multiplayerNameField.setName("multiplayerPlayerNameField");
        styleField(multiplayerNameField);
        scaleField(multiplayerNameField, uiScale);
        JComboBox<MultiplayerMissionChoice> multiplayerMissionBox =
                new JComboBox<>(MultiplayerMissionChoice.values());
        multiplayerMissionBox.setName("multiplayerMissionSelector");
        styleCombo(multiplayerMissionBox);
        scaleCombo(multiplayerMissionBox, uiScale);
        JLabel versionLabel = new JLabel("Version " + AppInfo.VERSION);
        versionLabel.setForeground(new Color(188, 201, 216));
        versionLabel.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, uiScale));
        continueCampaignLabel = bodyLabel("", uiScale);
        continueCampaignLabel.setForeground(new Color(196, 219, 192, 220));
        for (int i = 0; i < campaignSlotButtons.length; i++) {
            campaignSlotButtons[i] = createMenuButton(CAMPAIGN_SLOT_LABELS[i], new Color(68, 111, 154), uiScale);
            campaignSlotButtons[i].setName("campaignSlotButton" + i);
            campaignSlotDeleteButtons[i] = createMenuButton("Delete", new Color(107, 56, 68), uiScale);
            campaignSlotDeleteButtons[i].setName("campaignSlotDeleteButton" + i);
            campaignSlotLabels[i] = bodyLabel("", uiScale);
            campaignSlotLabels[i].setName("campaignSlotSummaryLabel" + i);
            campaignSlotLabels[i].setForeground(new Color(204, 222, 238, 216));
        }

        JLabel fullscreenHint = metaLabel("Alt+Enter toggles fullscreen during battle", uiScale);
        MenuSettingsStore.MenuSettings persisted = MenuSettingsStore.load();
        final ExperienceSettings[] experience = {ExperienceSettingsStore.load()};
        GameMode persistedMode = MenuSettingsStore.resolveMode(persisted.modeName);
        modeBox.setSelectedItem(isMissionSetupMode(persistedMode) ? persistedMode : GameMode.LAST_STAND);
        mapBox.setSelectedIndex(Math.max(0, Math.min(mapBox.getItemCount() - 1, persisted.mapIndex)));
        syncTeamOptionsForMode((GameMode) modeBox.getSelectedItem(), teamBox, persisted.playerTeamId);
        seedField.setText(persisted.seedText);
        directAddressField.setText(persisted.multiplayerDirectAddress);
        multiplayerNameField.setText(persisted.multiplayerPlayerName);
        multiplayerMissionBox.setSelectedItem(MultiplayerMissionChoice.fromMissionId(persisted.multiplayerMissionId));
        final String[] selectedCampaignStartupPreset = {""};

        java.util.function.Consumer<GameMode> persistSettings = (selectedMode) -> {
            MenuSettingsStore.MenuSettings save = new MenuSettingsStore.MenuSettings();
            GameMode currentMode = (selectedMode != null) ? selectedMode : (GameMode) modeBox.getSelectedItem();
            save.modeName = (currentMode == null) ? GameMode.CAMPAIGN_OPS.name() : currentMode.name();
            save.mapIndex = mapBox.getSelectedIndex();
            save.randomEvents = true;
            save.seedText = seedField.getText();
            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            save.playerTeamId = (choice == null) ? 0 : choice.teamId();
            save.multiplayerDirectAddress = multiplayerDirectAddressOrDefault(directAddressField.getText());
            save.multiplayerMissionId = multiplayerSelectedMissionId(multiplayerMissionBox);
            save.multiplayerPlayerName = multiplayerPlayerNameOrDefault(multiplayerNameField.getText());
            MenuSettingsStore.save(save);
        };

        java.util.function.Consumer<GameMode> startWithMode = (overrideMode) -> {
            GameMode mode = (overrideMode != null) ? overrideMode : (GameMode) modeBox.getSelectedItem();
            if (mode == null) mode = GameMode.CAMPAIGN_OPS;

            int w = 5000, h = 5000;
            if (mapBox.getSelectedIndex() == 1) { w = 10000; h = 10000; }
            if (mapBox.getSelectedIndex() == 2) { w = 20000; h = 20000; }

            long seed;
            try {
                seed = Long.parseLong(seedField.getText().trim());
            } catch (Exception ex) {
                seed = System.nanoTime();
            }
            if (seed == 0) seed = System.nanoTime();

            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            int playerTeamId = (choice == null) ? 0 : choice.teamId();
            persistSettings.accept(mode);
            boolean resumeCampaign = mode == GameMode.FLEET;
            if (mode == GameMode.CUSTOM_BATTLES) {
                GameConfig customConfig = buildCustomBattleConfig(this, uiScale, w, h, seed, playerTeamId);
                if (customConfig == null) return;
                onStart.accept(customConfig.withExperience(experience[0]));
                return;
            }
            if (mode == GameMode.CAMPAIGN_OPS && experience[0].tacticalOnly) {
                onStart.accept(new GameConfig(GameMode.CUSTOM_BATTLES, w, h, true, seed, false,
                        playerTeamId).withExperience(experience[0]));
                return;
            }
            GameConfig launch = new GameConfig(mode, w, h, true, seed, false, playerTeamId, resumeCampaign);
            if (mode == GameMode.CAMPAIGN_OPS && !resumeCampaign) {
                launch = launch.withAutoLaunchCampaignStartSite(true);
            }
            onStart.accept(launch.withExperience(experience[0]));
        };
        experienceButton.addActionListener(e -> {
            ExperienceSettings edited = ExperienceSettingsDialog.show(this, experience[0]);
            if (edited != null) {
                experience[0] = edited;
                ExperienceSettingsStore.save(edited);
            }
        });
        controlsButton.addActionListener(e -> InputBindingsDialog.show(this, uiScale));

        start.addActionListener(e -> startWithMode.accept(null));

        credits.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            onCredits.run();
        });

        quit.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            onQuit.run();
        });

        modeBox.addActionListener(e -> {
            GameMode selectedMode = (GameMode) modeBox.getSelectedItem();
            PlayerTeamChoice selected = (PlayerTeamChoice) teamBox.getSelectedItem();
            int preferredTeamId = (selected == null) ? 0 : selected.teamId();
            syncTeamOptionsForMode(selectedMode, teamBox, preferredTeamId);
            if (selectedMode != null) {
                multiplayerMissionBox.setSelectedItem(MultiplayerMissionChoice.fromGameMode(selectedMode));
            }
            persistSettings.accept(selectedMode);
        });
        mapBox.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        seedField.addActionListener(e -> persistSettings.accept((GameMode) modeBox.getSelectedItem()));
        seedField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistSettings.accept((GameMode) modeBox.getSelectedItem());
            }
        });

        tutorialStart.addActionListener(e -> startWithMode.accept(GameMode.TUTORIAL));
        continueCampaignButton.addActionListener(e -> {
            ResumeCampaignState checkpoint = loadResumeCampaignState();
            if (!checkpoint.available() || checkpoint.config() == null) {
                refreshCampaignCheckpointState();
                return;
            }
            persistSettings.accept(GameMode.CAMPAIGN_OPS);
            onStart.accept(checkpoint.config().withExperience(experience[0]));
        });
        deleteCampaignButton.addActionListener(e -> {
            ResumeCampaignState checkpoint = loadResumeCampaignState();
            if (!checkpoint.available()) {
                refreshCampaignCheckpointState();
                return;
            }
            if (confirmDeleteCampaignSave("primary campaign save and autosaves", checkpoint.summaryText())) {
                deleteCampaignSave("primary");
            }
        });
        for (int i = 0; i < campaignSlotButtons.length; i++) {
            final int slotIndex = i;
            campaignSlotButtons[i].addActionListener(e -> {
                String slotId = CAMPAIGN_SLOT_IDS[slotIndex];
                ResumeCampaignState checkpoint = loadResumeCampaignState(slotId);
                persistSettings.accept(GameMode.CAMPAIGN_OPS);
                if (checkpoint.available() && checkpoint.config() != null) {
                    onStart.accept(checkpoint.config().withCampaignSlot(slotId).withExperience(experience[0]));
                    return;
                }
                int w = 5000;
                int h = 5000;
                if (mapBox.getSelectedIndex() == 1) {
                    w = 10000;
                    h = 10000;
                }
                if (mapBox.getSelectedIndex() == 2) {
                    w = 20000;
                    h = 20000;
                }
                long seed;
                try {
                    seed = Long.parseLong(seedField.getText().trim());
                } catch (Exception ex) {
                    seed = System.nanoTime();
                }
                if (seed == 0) seed = System.nanoTime();
                PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
                int playerTeamId = (choice == null) ? 0 : choice.teamId();
                String startupPreset = selectedCampaignStartupPreset[0] == null ? "" : selectedCampaignStartupPreset[0];
                GameConfig launch = new GameConfig(GameMode.CAMPAIGN_OPS, w, h, true, seed, false,
                        playerTeamId, false,
                        1, "", "",
                        startupPreset)
                        .withCampaignSlot(slotId);
                if (startupPreset.isBlank()) {
                    launch = launch.withAutoLaunchCampaignStartSite(true);
                }
                onStart.accept(launch.withExperience(experience[0]));
            });
            campaignSlotDeleteButtons[i].addActionListener(e -> {
                String slotId = CAMPAIGN_SLOT_IDS[slotIndex];
                ResumeCampaignState checkpoint = loadResumeCampaignState(slotId);
                if (!checkpoint.available()) {
                    refreshCampaignCheckpointState();
                    return;
                }
                if (confirmDeleteCampaignSave(CAMPAIGN_SLOT_LABELS[slotIndex], checkpoint.summaryText())) {
                    deleteCampaignSave(slotId);
                }
            });
        }
        customShipCreator.addActionListener(e -> openCustomShipCreator(this));
        galaxyMapTest.addActionListener(e -> {
            persistSettings.accept(GameMode.CAMPAIGN_OPS);
            int w = 20000;
            int h = 20000;
            long seed;
            try {
                seed = Long.parseLong(seedField.getText().trim());
            } catch (Exception ex) {
                seed = System.nanoTime();
            }
            if (seed == 0) seed = System.nanoTime();
            PlayerTeamChoice choice = (PlayerTeamChoice) teamBox.getSelectedItem();
            int playerTeamId = (choice == null) ? 0 : choice.teamId();
            onStart.accept(new GameConfig(
                    GameMode.CAMPAIGN_OPS,
                    w, h, true, seed, false,
                    playerTeamId, false,
                    1, "", "",
                    "galaxy_map_test").withExperience(experience[0]));
        });
        hostBattle.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            int port = multiplayerPortFromAddress(directAddressField.getText());
            String hostAddress = multiplayerHostFromAddress(directAddressField.getText());
            onStart.accept(multiplayerLaunchConfig(
                    MultiplayerLaunchConfig.host(port, hostAddress)
                            .withMissionId(multiplayerSelectedMissionId(multiplayerMissionBox))
                            .withPlayerName(multiplayerPlayerNameOrDefault(multiplayerNameField.getText())))
                    .withExperience(experience[0]));
        });
        joinBattle.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            onStart.accept(multiplayerLaunchConfig(
                    MultiplayerLaunchConfig.client(
                            multiplayerDirectAddressOrDefault(directAddressField.getText()),
                            "")
                            .withMissionId(multiplayerSelectedMissionId(multiplayerMissionBox))
                            .withPlayerName(multiplayerPlayerNameOrDefault(multiplayerNameField.getText())))
                    .withExperience(experience[0]));
        });
        diagnosticsBattle.addActionListener(e -> {
            persistSettings.accept((GameMode) modeBox.getSelectedItem());
            int port = multiplayerPortFromAddress(directAddressField.getText());
            String hostAddress = multiplayerHostFromAddress(directAddressField.getText());
            onStart.accept(multiplayerLaunchConfig(
                    MultiplayerLaunchConfig.host(port, hostAddress)
                            .withDiagnosticsHarness(true)
                            .withMissionId(multiplayerSelectedMissionId(multiplayerMissionBox))
                            .withPlayerName(multiplayerPlayerNameOrDefault(multiplayerNameField.getText())))
                    .withExperience(experience[0]));
        });

        JPanel titleStack = transparentPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(eyebrowLabel("Fleet Command", uiScale, new Color(115, 204, 225)));
        titleStack.add(Box.createVerticalStrut(MenuDisplay.scaled(5, uiScale)));
        titleStack.add(title);
        titleStack.add(Box.createVerticalStrut(MenuDisplay.scaled(5, uiScale)));
        titleStack.add(bodyLabel("Continue your war, begin a campaign, or prepare an independent engagement.", uiScale));

        JPanel headerPanel = transparentPanel();
        headerPanel.setLayout(new BorderLayout(MenuDisplay.scaled(24, uiScale), 0));
        headerPanel.add(titleStack, BorderLayout.WEST);

        JPanel headerBadges = new FlowPanel(FlowLayout.RIGHT, MenuDisplay.scaled(10, uiScale), MenuDisplay.scaled(8, uiScale));
        headerBadges.add(createBadge("Version " + AppInfo.VERSION, new Color(15, 29, 45, 220),
                new Color(74, 138, 174, 170), uiScale));
        headerBadges.add(createBadge("Command Center", new Color(37, 29, 43, 214),
                new Color(204, 139, 79, 160), uiScale));
        headerPanel.add(headerBadges, BorderLayout.EAST);

        JPanel missionForm = new JPanel(new GridBagLayout());
        missionForm.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(8, uiScale));
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.weightx = 0;
        missionForm.add(label("Battle Type:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(modeBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Map:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(mapBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Your Faction:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(teamBox, c);

        c.gridy++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        missionForm.add(label("Seed:", uiScale), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        missionForm.add(seedField, c);

        JPanel missionFormShell = createInsetPanel(new Color(9, 19, 31, 190), new Color(76, 130, 161, 130), uiScale);
        missionFormShell.add(missionForm);

        CardLayout menuCardsLayout = new CardLayout();
        JPanel menuCards = transparentPanel();
        menuCards.setLayout(menuCardsLayout);

        JPanel commandPanel = transparentPanel();
        commandPanel.setLayout(new BorderLayout(MenuDisplay.scaled(28, uiScale), 0));
        JPanel continueCard = createSectionPanel(new Color(83, 170, 111, 165), uiScale);
        continueCard.setName("continueCampaignCard");
        continueCard.add(eyebrowLabel("Continue Campaign", uiScale, new Color(152, 229, 168)));
        continueCard.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        continueCard.add(sectionTitle("Return To The War", uiScale));
        continueCard.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        continueCard.add(continueCampaignLabel);
        continueCard.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));
        continueCard.add(continueCampaignButton);
        continueCard.setPreferredSize(new Dimension(MenuDisplay.scaled(340, uiScale), MenuDisplay.scaled(222, uiScale)));
        continueCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, MenuDisplay.scaled(222, uiScale)));

        JLabel commandHint = metaLabel("Select a command vector", uiScale);
        commandHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel commandList = transparentPanel();
        commandList.setLayout(new BoxLayout(commandList, BoxLayout.Y_AXIS));
        commandList.add(continueCard);
        commandList.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));
        commandList.add(commandHint);
        commandList.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        addCommandButton(commandList, newCampaign, "Start or load an open-world or linear campaign.", uiScale);
        addCommandButton(commandList, tutorialStart, "Learn fleet command through a short combat campaign.", uiScale);
        addCommandButton(commandList, customBattle, "Build a fleet and create an independent engagement.", uiScale);
        if (multiplayerEntryPointEnabled()) {
            addCommandButton(commandList, multiplayerButton, "Host or join a direct custom battle lobby.", uiScale);
        }
        commandList.add(commandSeparator(uiScale));
        JPanel utilityGrid = transparentPanel();
        utilityGrid.setLayout(new GridLayout(2, 2, MenuDisplay.scaled(8, uiScale), MenuDisplay.scaled(8, uiScale)));
        prepareCommandButton(customShipCreator, "Design and inspect custom hulls.", uiScale);
        prepareCommandButton(settingsButton, "Adjust accessibility and control bindings.", uiScale);
        prepareCommandButton(credits, "View the project credits.", uiScale);
        prepareCommandButton(quit, "Exit Eagles Remorse.", uiScale);
        utilityGrid.add(customShipCreator);
        utilityGrid.add(settingsButton);
        utilityGrid.add(credits);
        utilityGrid.add(quit);
        utilityGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        utilityGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, MenuDisplay.scaled(84, uiScale)));
        commandList.add(utilityGrid);

        JPanel battlePanel = createMenuBattlePanel(uiScale);
        battlePanel.setName("mainMenuBattlePanel");
        JPanel battleShell = createBattleShell(battlePanel, uiScale);

        JPanel commandSplit = transparentPanel();
        commandSplit.setLayout(new BorderLayout(MenuDisplay.scaled(28, uiScale), 0));
        commandList.setPreferredSize(new Dimension(MenuDisplay.scaled(360, uiScale), 1));
        commandSplit.add(commandList, BorderLayout.WEST);
        commandSplit.add(battleShell, BorderLayout.CENTER);
        commandPanel.add(commandSplit, BorderLayout.CENTER);
        if (developerMenuEnabled()) {
            JButton developerTools = createMenuButton("Developer Tools", new Color(86, 77, 122), uiScale);
            developerTools.setName("developerToolsButton");
            JPanel devRow = transparentPanel();
            devRow.setLayout(new BorderLayout());
            devRow.add(developerTools, BorderLayout.EAST);
            battleShell.add(devRow, BorderLayout.SOUTH);
            developerTools.addActionListener(e -> menuCardsLayout.show(menuCards, "developer"));
        }

        JPanel campaignPanel = createSectionPanel(new Color(48, 146, 197, 160), uiScale);
        campaignPanel.setLayout(new BorderLayout(0, MenuDisplay.scaled(18, uiScale)));
        campaignPanel.add(subMenuHeader("Campaign", "Choose Campaign Type", backFromCampaign,
                uiScale, new Color(115, 204, 225)), BorderLayout.NORTH);
        JLabel campaignTypeLabel = bodyLabel("Selected: Open World Campaign", uiScale);
        JPanel campaignChoicePanel = verticalPanel();
        campaignChoicePanel.add(campaignTypeLabel);
        campaignChoicePanel.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));
        JPanel campaignTypeGrid = transparentPanel();
        campaignTypeGrid.setLayout(new GridLayout(1, 2, MenuDisplay.scaled(12, uiScale), 0));
        campaignTypeGrid.add(campaignOps);
        campaignTypeGrid.add(linearCampaign);
        campaignChoicePanel.add(campaignTypeGrid);
        campaignChoicePanel.add(Box.createVerticalStrut(MenuDisplay.scaled(16, uiScale)));
        campaignChoicePanel.add(bodyLabel("<html><div style='width:560px;'>"
                + "Open World Campaign starts the dynamic strategic war. Linear Campaign starts the structured mission sequence. "
                + "Choose the campaign type here, then start or load a save slot from the left column."
                + "</div></html>", uiScale));

        JPanel slotPanel = createInsetPanel(new Color(14, 28, 43, 196), new Color(91, 135, 181, 130), uiScale);
        slotPanel.setPreferredSize(new Dimension(MenuDisplay.scaled(580, uiScale), MenuDisplay.scaled(440, uiScale)));
        slotPanel.add(eyebrowLabel("Select Save Slot", uiScale, new Color(151, 203, 234)));
        slotPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        for (int i = 0; i < campaignSlotButtons.length; i++) {
            JPanel row = transparentPanel();
            row.setName("campaignSlotRow" + i);
            row.setLayout(new BorderLayout(MenuDisplay.scaled(10, uiScale), 0));
            campaignSlotButtons[i].setPreferredSize(new Dimension(MenuDisplay.scaled(104, uiScale), MenuDisplay.scaled(38, uiScale)));
            campaignSlotDeleteButtons[i].setPreferredSize(new Dimension(MenuDisplay.scaled(88, uiScale), MenuDisplay.scaled(38, uiScale)));
            JPanel slotButtons = transparentPanel();
            slotButtons.setLayout(new GridLayout(1, 2, MenuDisplay.scaled(6, uiScale), 0));
            slotButtons.add(campaignSlotButtons[i]);
            slotButtons.add(campaignSlotDeleteButtons[i]);
            row.add(slotButtons, BorderLayout.WEST);
            row.add(campaignSlotLabels[i], BorderLayout.CENTER);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(MenuDisplay.scaled(520, uiScale), MenuDisplay.scaled(78, uiScale)));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, MenuDisplay.scaled(78, uiScale)));
            slotPanel.add(row);
            if (i < campaignSlotButtons.length - 1) {
                slotPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
            }
        }
        slotPanel.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        deleteCampaignButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        slotPanel.add(deleteCampaignButton);

        JPanel campaignBody = transparentPanel();
        campaignBody.setLayout(new BorderLayout(MenuDisplay.scaled(24, uiScale), 0));
        campaignBody.add(slotPanel, BorderLayout.WEST);
        campaignBody.add(campaignChoicePanel, BorderLayout.CENTER);
        campaignPanel.add(campaignBody, BorderLayout.CENTER);

        JPanel customBattlePanel = createSectionPanel(new Color(221, 139, 75, 145), uiScale);
        customBattlePanel.setLayout(new BorderLayout(0, MenuDisplay.scaled(18, uiScale)));
        customBattlePanel.add(subMenuHeader("Custom Battle", "Build A Custom Engagement", backFromBattle,
                uiScale, new Color(235, 176, 111)), BorderLayout.NORTH);
        JPanel customBattleBody = verticalPanel();
        customBattleBody.add(bodyLabel("Choose the battle type, map scale, faction, and seed before launching.", uiScale));
        customBattleBody.add(Box.createVerticalStrut(MenuDisplay.scaled(16, uiScale)));
        customBattleBody.add(missionFormShell);
        customBattleBody.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));
        JPanel battleActions = new JPanel(new GridLayout(1, 1, 0, 0));
        battleActions.setOpaque(false);
        battleActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        battleActions.add(start);
        customBattleBody.add(battleActions);
        customBattlePanel.add(customBattleBody, BorderLayout.CENTER);

        JPanel multiplayerPanel = createSectionPanel(new Color(64, 151, 158, 150), uiScale);
        multiplayerPanel.setName("multiplayerEntryPanel");
        multiplayerPanel.setLayout(new BorderLayout(0, MenuDisplay.scaled(18, uiScale)));
        multiplayerPanel.add(subMenuHeader("Multiplayer", "Host Or Join A Game", backFromMultiplayer,
                uiScale, new Color(119, 217, 208)), BorderLayout.NORTH);
        JPanel multiplayerBody = verticalPanel();
        JPanel multiplayerForm = createInsetPanel(new Color(8, 26, 33, 196), new Color(64, 151, 158, 135), uiScale);
        multiplayerForm.add(formRow("Player Name:", multiplayerNameField, uiScale));
        multiplayerForm.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        multiplayerForm.add(formRow("Server Address:", directAddressField, uiScale));
        multiplayerForm.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        multiplayerForm.add(formRow("Mission:", multiplayerMissionBox, uiScale));
        multiplayerBody.add(multiplayerForm);
        multiplayerBody.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));
        JPanel multiplayerActions = new JPanel(new GridLayout(1, 2, MenuDisplay.scaled(12, uiScale), 0));
        multiplayerActions.setOpaque(false);
        multiplayerActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        multiplayerActions.add(hostBattle);
        multiplayerActions.add(joinBattle);
        multiplayerBody.add(multiplayerActions);
        JLabel debugInfo = metaLabel("Protocol 1  |  Build " + AppInfo.VERSION + "  |  Manifest V1", uiScale);
        debugInfo.setName("multiplayerDebugInfoLabel");
        multiplayerBody.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        multiplayerBody.add(debugInfo);
        multiplayerPanel.add(multiplayerBody, BorderLayout.CENTER);

        JPanel settingsPanel = createSectionPanel(new Color(92, 110, 159, 150), uiScale);
        settingsPanel.setLayout(new BorderLayout(0, MenuDisplay.scaled(18, uiScale)));
        settingsPanel.add(subMenuHeader("Settings", "Controls And Accessibility", backFromSettings,
                uiScale, new Color(176, 199, 239)), BorderLayout.NORTH);
        JPanel settingsActions = transparentPanel();
        settingsActions.setLayout(new GridLayout(0, 1, 0, MenuDisplay.scaled(10, uiScale)));
        settingsActions.add(experienceButton);
        settingsActions.add(controlsButton);
        settingsPanel.add(settingsActions, BorderLayout.CENTER);

        JPanel developerPanel = createSectionPanel(new Color(86, 77, 122, 150), uiScale);
        developerPanel.setLayout(new BorderLayout(0, MenuDisplay.scaled(18, uiScale)));
        developerPanel.add(subMenuHeader("Development Build Only", "Developer Tools", backFromDeveloper,
                uiScale, new Color(199, 185, 238)), BorderLayout.NORTH);
        JPanel developerActions = transparentPanel();
        developerActions.setLayout(new GridLayout(0, 1, 0, MenuDisplay.scaled(10, uiScale)));
        developerActions.add(galaxyMapTest);
        developerActions.add(diagnosticsBattle);
        developerPanel.add(developerActions, BorderLayout.CENTER);

        menuCards.add(commandPanel, "command");
        menuCards.add(campaignPanel, "campaign");
        menuCards.add(customBattlePanel, "custom");
        if (multiplayerEntryPointEnabled()) {
            menuCards.add(multiplayerPanel, "multiplayer");
        }
        menuCards.add(settingsPanel, "settings");
        if (developerMenuEnabled()) {
            menuCards.add(developerPanel, "developer");
        }

        newCampaign.addActionListener(e -> menuCardsLayout.show(menuCards, "campaign"));
        customBattle.addActionListener(e -> menuCardsLayout.show(menuCards, "custom"));
        multiplayerButton.addActionListener(e -> menuCardsLayout.show(menuCards, "multiplayer"));
        settingsButton.addActionListener(e -> menuCardsLayout.show(menuCards, "settings"));
        backFromCampaign.addActionListener(e -> menuCardsLayout.show(menuCards, "command"));
        backFromBattle.addActionListener(e -> menuCardsLayout.show(menuCards, "command"));
        backFromMultiplayer.addActionListener(e -> menuCardsLayout.show(menuCards, "command"));
        backFromSettings.addActionListener(e -> menuCardsLayout.show(menuCards, "command"));
        backFromDeveloper.addActionListener(e -> menuCardsLayout.show(menuCards, "command"));
        campaignOps.addActionListener(e -> {
            selectedCampaignStartupPreset[0] = "";
            campaignTypeLabel.setText("Selected: Open World Campaign");
        });
        linearCampaign.addActionListener(e -> {
            selectedCampaignStartupPreset[0] = "linear_campaign";
            campaignTypeLabel.setText("Selected: Linear Campaign");
        });

        JPanel footerPanel = transparentPanel();
        footerPanel.setLayout(new BorderLayout(MenuDisplay.scaled(12, uiScale), 0));
        footerPanel.add(Box.createHorizontalStrut(MenuDisplay.scaled(1, uiScale)), BorderLayout.WEST);
        JPanel footerInfo = transparentPanel();
        footerInfo.setLayout(new BoxLayout(footerInfo, BoxLayout.Y_AXIS));
        versionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        fullscreenHint.setAlignmentX(Component.RIGHT_ALIGNMENT);
        footerInfo.add(versionLabel);
        footerInfo.add(Box.createVerticalStrut(MenuDisplay.scaled(4, uiScale)));
        footerInfo.add(fullscreenHint);
        footerPanel.add(footerInfo, BorderLayout.EAST);

        JPanel rootContent = createMenuContent(uiScale);
        rootContent.add(headerPanel, BorderLayout.NORTH);
        rootContent.add(menuCards, BorderLayout.CENTER);
        rootContent.add(footerPanel, BorderLayout.SOUTH);

        setLayout(new GridBagLayout());
        GridBagConstraints rootConstraints = new GridBagConstraints();
        rootConstraints.gridx = 0;
        rootConstraints.gridy = 0;
        rootConstraints.weightx = 1.0;
        rootConstraints.weighty = 1.0;
        rootConstraints.fill = GridBagConstraints.BOTH;
        add(wrapMenuCard(rootContent, uiScale), rootConstraints);

        // Convenience: Alt+Enter toggles fullscreen in-game, but in menu we can at least
        // show that this is the toggle key later (Step 3+).
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK), "noop");

        // Dev hotkey: quick-start Four Team Domination (uses current map size/seed settings).
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "dev_four_team");
        getActionMap().put("dev_four_team", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                startWithMode.accept(GameMode.FOUR_TEAM_DOMINATION);
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "dev_galaxy_map_test");
        getActionMap().put("dev_galaxy_map_test", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                galaxyMapTest.doClick();
            }
        });

        refreshCampaignCheckpointState();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();
        double t = (System.nanoTime() - backgroundStartNs) / 1_000_000_000.0;
        double camX = 900.0 + t * 16.0;
        double camY = 520.0 + Math.sin(t * 0.16) * 180.0;
        if (spaceBackgroundPainter != null) {
            spaceBackgroundPainter.paint(g2, camX, camY, w, h, MENU_BG_SEED);
        }
        drawMenuAtmosphere(g2, w, h, t);
        g2.dispose();
    }

    void stopBackgroundTimerForTests() {
        backgroundTimer.stop();
    }

    private static JLabel label(String text, double scale) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(255, 255, 255, 210));
        l.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 17, scale));
        return l;
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.setFocusable(false);
        combo.setBackground(new Color(8, 19, 31));
        combo.setForeground(new Color(236, 242, 248));
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(82, 139, 164)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                label.setBackground(isSelected ? new Color(42, 111, 150) : new Color(8, 19, 31));
                label.setForeground(new Color(236, 242, 248));
                return label;
            }
        });
    }

    private static void styleField(JTextField field) {
        field.setBackground(new Color(8, 19, 31));
        field.setForeground(new Color(236, 242, 248));
        field.setCaretColor(Color.WHITE);
        field.setSelectionColor(new Color(55, 131, 166));
    }

    private static void styleCheckBox(JCheckBox checkBox, double scale) {
        checkBox.setOpaque(false);
        checkBox.setForeground(new Color(223, 232, 243));
        checkBox.setFont(MenuDisplay.font("Consolas", Font.BOLD, 14, scale));
        checkBox.setFocusPainted(false);
        checkBox.setSelected(true);
    }

    private static JButton createMenuButton(String text, Color fill, double scale) {
        return new MenuButton(text, fill, scale);
    }

    static boolean multiplayerEntryPointEnabled() {
        return PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.MULTIPLAYER_CUSTOM_MISSIONS)
                || PostAlphaFeatureFlags.enabled(PostAlphaFeatureFlags.Feature.MULTIPLAYER_CUSTOM_BATTLE);
    }

    private static boolean developerMenuEnabled() {
        return Boolean.getBoolean("game.dev.menu")
                || "true".equalsIgnoreCase(System.getenv("GAME_DEV_MENU"));
    }

    private static void addCommandButton(JPanel parent, JButton button, String description, double uiScale) {
        if (parent == null || button == null) return;
        prepareCommandButton(button, description, uiScale);
        parent.add(button);
        parent.add(Box.createVerticalStrut(MenuDisplay.scaled(6, uiScale)));
    }

    private static void prepareCommandButton(JButton button, String description, double uiScale) {
        if (button == null) return;
        button.setToolTipText(description);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(MenuDisplay.scaled(280, uiScale), MenuDisplay.scaled(38, uiScale)));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, MenuDisplay.scaled(38, uiScale)));
    }

    private static JComponent commandSeparator(double uiScale) {
        JComponent separator = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(111, 154, 185, 70));
                int y = getHeight() / 2;
                g2.drawLine(0, y, getWidth(), y);
                g2.dispose();
            }
        };
        separator.setOpaque(false);
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setPreferredSize(new Dimension(1, MenuDisplay.scaled(16, uiScale)));
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, MenuDisplay.scaled(16, uiScale)));
        return separator;
    }

    private static JPanel createBattleShell(JPanel battlePanel, double uiScale) {
        JPanel shell = new JPanel(new BorderLayout(0, MenuDisplay.scaled(10, uiScale))) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(2, 6, 12, 56), 0, h,
                        new Color(2, 5, 10, 90)));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(98, 150, 183, 72));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(12, uiScale),
                MenuDisplay.scaled(12, uiScale),
                MenuDisplay.scaled(12, uiScale),
                MenuDisplay.scaled(12, uiScale)));
        shell.add(battlePanel, BorderLayout.CENTER);
        return shell;
    }

    private JPanel createMenuBattlePanel(double uiScale) {
        try {
            Class<?> panelClass = Class.forName("MainMenuBattlePanel");
            Object instance = panelClass.getConstructor(double.class).newInstance(uiScale);
            if (instance instanceof JPanel panel) {
                if (instance instanceof MenuBattleView view) {
                    menuBattleView = view;
                }
                return panel;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The real ship attract mode lives in the default package with the combat classes.
        }
        return createMenuBattleUnavailablePanel(uiScale);
    }

    private static JPanel createMenuBattleUnavailablePanel(double uiScale) {
        JPanel panel = transparentPanel();
        panel.setLayout(new GridBagLayout());
        JLabel label = bodyLabel("Tactical attract mode unavailable in this build.", uiScale);
        label.setForeground(new Color(198, 211, 226, 180));
        panel.add(label);
        return panel;
    }

    private static JPanel formRow(String labelText, JComponent field, double uiScale) {
        JPanel row = transparentPanel();
        row.setLayout(new BorderLayout(MenuDisplay.scaled(10, uiScale), 0));
        JLabel label = label(labelText, uiScale);
        label.setPreferredSize(new Dimension(MenuDisplay.scaled(150, uiScale), MenuDisplay.scaled(34, uiScale)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JPanel verticalPanel() {
        JPanel panel = transparentPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JPanel subMenuHeader(String eyebrow, String title, JButton backButton, double uiScale, Color accent) {
        JPanel header = transparentPanel();
        header.setLayout(new BorderLayout(MenuDisplay.scaled(18, uiScale), 0));
        JPanel titleStack = verticalPanel();
        titleStack.add(eyebrowLabel(eyebrow, uiScale, accent));
        titleStack.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        titleStack.add(sectionTitle(title, uiScale));
        header.add(titleStack, BorderLayout.WEST);
        backButton.setPreferredSize(new Dimension(MenuDisplay.scaled(160, uiScale), MenuDisplay.scaled(38, uiScale)));
        JPanel backWrap = transparentPanel();
        backWrap.setLayout(new BorderLayout());
        backWrap.add(backButton, BorderLayout.NORTH);
        header.add(backWrap, BorderLayout.EAST);
        return header;
    }

    private static JPanel buildMultiplayerEntryPanel(JButton hostBattle,
                                                     JButton joinBattle,
                                                     JButton diagnosticsBattle,
                                                     JTextField directAddressField,
                                                     JTextField playerNameField,
                                                     JComboBox<MultiplayerMissionChoice> missionBox,
                                                     double uiScale) {
        JPanel panel = createInsetPanel(new Color(8, 26, 33, 196), new Color(64, 151, 158, 135), uiScale);
        panel.setName("multiplayerEntryPanel");
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = eyebrowLabel("Multiplayer Setup", uiScale, new Color(119, 217, 208));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));

        JPanel addressRow = transparentPanel();
        addressRow.setName("multiplayerDirectAddressRow");
        addressRow.setLayout(new BorderLayout(MenuDisplay.scaled(8, uiScale), 0));
        addressRow.add(label("Address:", uiScale), BorderLayout.WEST);
        addressRow.add(directAddressField, BorderLayout.CENTER);
        panel.add(addressRow);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));

        JPanel nameRow = transparentPanel();
        nameRow.setName("multiplayerPlayerNameRow");
        nameRow.setLayout(new BorderLayout(MenuDisplay.scaled(8, uiScale), 0));
        nameRow.add(label("Name:", uiScale), BorderLayout.WEST);
        nameRow.add(playerNameField, BorderLayout.CENTER);
        panel.add(nameRow);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));

        JPanel missionRow = transparentPanel();
        missionRow.setName("multiplayerMissionRow");
        missionRow.setLayout(new BorderLayout(MenuDisplay.scaled(8, uiScale), 0));
        missionRow.add(label("Mission:", uiScale), BorderLayout.WEST);
        missionRow.add(missionBox, BorderLayout.CENTER);
        panel.add(missionRow);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));

        JLabel debugInfo = metaLabel("Protocol 1  |  Build " + AppInfo.VERSION + "  |  Manifest V1", uiScale);
        debugInfo.setName("multiplayerDebugInfoLabel");
        debugInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(debugInfo);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));

        JPanel actions = new JPanel(new GridLayout(1, 3, MenuDisplay.scaled(10, uiScale), 0));
        actions.setName("multiplayerActionRow");
        actions.setOpaque(false);
        actions.add(hostBattle);
        actions.add(joinBattle);
        actions.add(diagnosticsBattle);
        panel.add(actions);
        return panel;
    }

    private static void showMultiplayerHostDialog(Component parent, String directAddress, double uiScale) {
        JOptionPane.showMessageDialog(parent,
                multiplayerDialogBody("Host Battle", directAddress, uiScale),
                "Multiplayer Custom Battle",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showMultiplayerJoinDialog(Component parent, String directAddress, double uiScale) {
        JOptionPane.showMessageDialog(parent,
                multiplayerDialogBody("Join Battle", directAddress, uiScale),
                "Multiplayer Custom Battle",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JComponent multiplayerDialogBody(String action, String directAddress, double uiScale) {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(9, 17, 29));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(10, uiScale),
                MenuDisplay.scaled(12, uiScale),
                MenuDisplay.scaled(10, uiScale),
                MenuDisplay.scaled(12, uiScale)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = sectionTitle(action, uiScale);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        String addressText = multiplayerDirectAddressOrDefault(directAddress);
        JLabel address = metaLabel("Direct address: " + addressText, uiScale);
        address.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = bodyLabel("<html><div style='width:390px;'>"
                + "V1 acceptance launcher is ready for test builds. Use the command below from the project root."
                + "</div></html>", uiScale);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextArea command = new JTextArea(multiplayerAcceptanceCommand(action, addressText));
        command.setName("multiplayerAcceptanceCommandText");
        command.setEditable(false);
        command.setLineWrap(true);
        command.setWrapStyleWord(true);
        command.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 12, uiScale));
        command.setForeground(new Color(211, 232, 228));
        command.setBackground(new Color(4, 10, 18));
        command.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(64, 132, 150, 140)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(8, uiScale),
                        MenuDisplay.scaled(8, uiScale),
                        MenuDisplay.scaled(8, uiScale),
                        MenuDisplay.scaled(8, uiScale))));
        panel.add(title);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(8, uiScale)));
        panel.add(address);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        panel.add(status);
        panel.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));
        panel.add(command);
        return panel;
    }

    static String multiplayerAcceptanceCommandForTests(String action, String directAddress) {
        return multiplayerAcceptanceCommand(action, multiplayerDirectAddressOrDefault(directAddress));
    }

    static GameConfig multiplayerLaunchConfigForTests(String action, String directAddress) {
        return multiplayerLaunchConfigForTests(action, directAddress, MultiplayerMissionChoice.DEFAULT_MISSION_ID);
    }

    static GameConfig multiplayerLaunchConfigForTests(String action, String directAddress, String missionId) {
        return multiplayerLaunchConfigForTests(action, directAddress, missionId, "Player");
    }

    static GameConfig multiplayerLaunchConfigForTests(String action, String directAddress,
                                                      String missionId, String playerName) {
        boolean host = action != null && action.toLowerCase(java.util.Locale.ROOT).contains("host");
        if (host) {
            return multiplayerLaunchConfig(MultiplayerLaunchConfig.host(
                    multiplayerPortFromAddress(directAddress),
                    multiplayerHostFromAddress(directAddress)).withMissionId(missionId)
                    .withPlayerName(multiplayerPlayerNameOrDefault(playerName)));
        }
        return multiplayerLaunchConfig(MultiplayerLaunchConfig.client(
                multiplayerDirectAddressOrDefault(directAddress), "").withMissionId(missionId)
                .withPlayerName(multiplayerPlayerNameOrDefault(playerName)));
    }

    private static String multiplayerAcceptanceCommand(String action, String directAddress) {
        boolean host = action != null && action.toLowerCase(java.util.Locale.ROOT).contains("host");
        if (host) {
            return ".\\gradlew.bat \"-PmpPort=46717\" \"-PmpTimeoutMs=60000\" "
                    + "\"-PmpReport=build/reports/multiplayer-lan-host-acceptance.txt\" "
                    + "multiplayerLanAcceptanceHost";
        }
        return ".\\gradlew.bat \"-PmpAddress=" + directAddress + "\" \"-PmpTimeoutMs=60000\" "
                + "\"-PmpReport=build/reports/multiplayer-lan-client-acceptance.txt\" "
                + "multiplayerLanAcceptanceClient";
    }

    private static String multiplayerDirectAddressOrDefault(String directAddress) {
        String trimmed = directAddress == null ? "" : directAddress.trim();
        return trimmed.isEmpty() ? "127.0.0.1:46717" : trimmed;
    }

    private static String multiplayerPlayerNameOrDefault(String playerName) {
        String trimmed = playerName == null ? "" : playerName.trim();
        if (trimmed.isEmpty()) return "Player";
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private static GameConfig multiplayerLaunchConfig(MultiplayerLaunchConfig launch) {
        return new GameConfig(GameMode.CUSTOM_BATTLES, 3600, 2200, true,
                System.nanoTime(), false, 0, false,
                1, "FRIGATE", "FRIGATE").withMultiplayerLaunch(launch);
    }

    private static String multiplayerSelectedMissionId(JComboBox<MultiplayerMissionChoice> missionBox) {
        Object selected = missionBox == null ? null : missionBox.getSelectedItem();
        if (selected instanceof MultiplayerMissionChoice choice) return choice.missionId();
        return MultiplayerMissionChoice.DEFAULT_MISSION_ID;
    }

    private static int multiplayerPortFromAddress(String directAddress) {
        String text = multiplayerDirectAddressOrDefault(directAddress);
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            try {
                int port = Integer.parseInt(text.substring(colon + 1).trim());
                if (port > 0 && port <= 65_535) return port;
            } catch (RuntimeException ignored) {
            }
        }
        return 46717;
    }

    private static String multiplayerHostFromAddress(String directAddress) {
        String text = multiplayerDirectAddressOrDefault(directAddress);
        int colon = text.lastIndexOf(':');
        return colon > 0 ? text.substring(0, colon).trim() : "";
    }

    private static JPanel createMenuContent(double scale) {
        JPanel panel = new JPanel(new BorderLayout(0, MenuDisplay.scaled(14, scale)));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(
                0,
                0,
                0,
                0));
        return panel;
    }

    private static JPanel wrapMenuCard(JPanel content, double scale) {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(7, 16, 28, 232), 0, h,
                        new Color(4, 9, 17, 224)));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(77, 156, 190, 125));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(new Color(255, 255, 255, 22));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
                int rail = MenuDisplay.scaled(7, scale);
                g2.setPaint(new GradientPaint(0, 0, new Color(67, 171, 203, 170),
                        w, 0, new Color(224, 133, 71, 128)));
                g2.fillRect(0, 0, w, rail);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(34, scale),
                MenuDisplay.scaled(42, scale),
                MenuDisplay.scaled(24, scale),
                MenuDisplay.scaled(42, scale)));
        GridBagConstraints contentConstraints = new GridBagConstraints();
        contentConstraints.gridx = 0;
        contentConstraints.gridy = 0;
        contentConstraints.weightx = 1.0;
        contentConstraints.weighty = 1.0;
        contentConstraints.fill = GridBagConstraints.BOTH;
        card.add(content, contentConstraints);
        return card;
    }

    private static JLabel sectionTitle(String text, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 26, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel bodyLabel(String html, double scale) {
        JLabel label = new JLabel(html);
        label.setForeground(new Color(214, 228, 242, 210));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static boolean isMissionSetupMode(GameMode mode) {
        return mode == GameMode.LAST_STAND
                || mode == GameMode.RESOURCE_RUSH
                || mode == GameMode.FOUR_TEAM_DOMINATION
                || mode == GameMode.CUSTOM_BATTLES
                || mode == GameMode.SHOOTING_RANGE
                || mode == GameMode.SHOWCASE;
    }

    public void refreshCampaignCheckpointState() {
        ResumeCampaignState checkpoint = loadResumeCampaignState();
        continueCampaignButton.setEnabled(checkpoint.available() && checkpoint.config() != null);
        continueCampaignButton.setText(checkpoint.available() ? "Continue Campaign" : "No Active Campaign");
        deleteCampaignButton.setEnabled(checkpoint.available());
        String primarySummary = checkpoint.available()
                ? checkpoint.summaryText()
                : "<b>No active campaign</b><br>Begin a campaign to establish your fleet.";
        continueCampaignLabel.setText("<html><div style='width:250px;'>" + primarySummary + "</div></html>");
        for (int i = 0; i < campaignSlotButtons.length; i++) {
            ResumeCampaignState slot = loadResumeCampaignState(CAMPAIGN_SLOT_IDS[i]);
            campaignSlotButtons[i].setText(slot.available() ? "Load" : "Start");
            campaignSlotButtons[i].setEnabled(true);
            campaignSlotDeleteButtons[i].setEnabled(slot.available());
            String summary = slot.available()
                    ? slot.summaryText()
                    : "Empty slot. Start a campaign here; F10 saves back to this file.";
            campaignSlotLabels[i].setText("<html><div style='width:245px;'><b>"
                    + CAMPAIGN_SLOT_LABELS[i] + "</b><br>" + summary + "</div></html>");
        }
    }

    private boolean confirmDeleteCampaignSave(String label, String summary) {
        String details = (summary == null || summary.isBlank()) ? "" : "\n\n" + stripHtml(summary);
        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete " + label + "?" + details,
                "Delete Campaign Save",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }

    private void deleteCampaignSave(String slotId) {
        if (resumeCampaignProvider != null && resumeCampaignProvider.delete(slotId)) {
            refreshCampaignCheckpointState();
        } else {
            Toolkit.getDefaultToolkit().beep();
            refreshCampaignCheckpointState();
        }
    }

    private static String stripHtml(String text) {
        return text == null ? "" : text
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private ResumeCampaignState loadResumeCampaignState() {
        if (resumeCampaignProvider == null) {
            return ResumeCampaignState.unavailable(NO_CHECKPOINT_MESSAGE);
        }
        ResumeCampaignState checkpoint = resumeCampaignProvider.load();
        if (checkpoint == null) {
            return ResumeCampaignState.unavailable(NO_CHECKPOINT_MESSAGE);
        }
        return checkpoint;
    }

    private ResumeCampaignState loadResumeCampaignState(String slotId) {
        if (resumeCampaignProvider == null) {
            return ResumeCampaignState.unavailable("Empty slot.");
        }
        ResumeCampaignState checkpoint = resumeCampaignProvider.load(slotId);
        if (checkpoint == null) {
            return ResumeCampaignState.unavailable("Empty slot.");
        }
        return checkpoint;
    }

    private static GameConfig toFleetResumeConfig(GameConfig config) {
        if (config == null) return null;
        return new GameConfig(
                GameMode.FLEET,
                config.worldW,
                config.worldH,
                config.randomEvents,
                config.seed,
                config.fullscreen,
                config.playerTeamId,
                true);
    }

    private static void scaleCombo(JComboBox<?> combo, double scale) {
        combo.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        combo.setPreferredSize(new Dimension(MenuDisplay.scaled(240, scale), MenuDisplay.scaled(34, scale)));
    }

    private static void scaleField(JTextField field, double scale) {
        field.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 15, scale));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(82, 139, 164)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale),
                        MenuDisplay.scaled(5, scale),
                        MenuDisplay.scaled(8, scale))
        ));
        field.setPreferredSize(new Dimension(MenuDisplay.scaled(240, scale), MenuDisplay.scaled(34, scale)));
        field.setColumns(Math.max(8, MenuDisplay.scaled(12, scale)));
    }

    private static void drawMenuAtmosphere(Graphics2D g2, int w, int h, double t) {
        g2.setColor(new Color(5, 11, 20, 122));
        g2.fillRect(0, 0, w, h);

        Paint oldPaint = g2.getPaint();
        g2.setPaint(new GradientPaint(0, 0, new Color(4, 8, 16, 180), 0, h / 2f, new Color(4, 8, 16, 28)));
        g2.fillRect(0, 0, w, h / 2);
        g2.setPaint(new GradientPaint(0, h, new Color(3, 6, 12, 220), 0, h / 2f, new Color(3, 6, 12, 0)));
        g2.fillRect(0, h / 2, w, h / 2);
        g2.setPaint(new GradientPaint(0, 0, new Color(3, 8, 18, 150), w / 4f, 0, new Color(3, 8, 18, 0)));
        g2.fillRect(0, 0, w / 3, h);
        g2.setPaint(new GradientPaint(w, 0, new Color(18, 6, 7, 138), w * 0.72f, 0, new Color(18, 6, 7, 0)));
        g2.fillRect((int) Math.round(w * 0.67), 0, (int) Math.round(w * 0.33), h);
        g2.setColor(new Color(72, 157, 194, 26));
        int drift = (int) Math.round(Math.sin(t * 0.18) * 24.0);
        for (int i = 0; i < 5; i++) {
            int y = (int) Math.round(h * (0.18 + i * 0.13));
            g2.drawLine((int) Math.round(w * 0.04) + drift, y,
                    (int) Math.round(w * 0.36) + drift, y - MenuDisplay.scaled(46, 1.0));
        }
        g2.setColor(new Color(226, 132, 74, 22));
        for (int i = 0; i < 4; i++) {
            int y = (int) Math.round(h * (0.34 + i * 0.11));
            g2.drawLine((int) Math.round(w * 0.68) - drift, y,
                    (int) Math.round(w * 0.96) - drift, y + MenuDisplay.scaled(40, 1.0));
        }
        g2.setPaint(oldPaint);
    }

    private static JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel eyebrowLabel(String text, double scale, Color color) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setForeground(color);
        label.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, scale));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel metaLabel(String text, double scale) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(167, 181, 198, 210));
        label.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 13, scale));
        return label;
    }

    private static JLabel createBadge(String text, Color fill, Color border, double scale) {
        JLabel badge = new JLabel(text);
        badge.setOpaque(true);
        badge.setBackground(fill);
        badge.setForeground(new Color(233, 240, 247));
        badge.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, scale));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(10, scale),
                        MenuDisplay.scaled(6, scale),
                        MenuDisplay.scaled(10, scale))));
        return badge;
    }

    private static JPanel createSectionPanel(Color accent, double scale) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(9, 20, 34, 214), 0, h,
                        new Color(6, 13, 24, 196)));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(accent);
                g2.fillRect(0, 0, MenuDisplay.scaled(5, scale), h);
                g2.setColor(new Color(255, 255, 255, 26));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(22, scale),
                MenuDisplay.scaled(30, scale),
                MenuDisplay.scaled(20, scale),
                MenuDisplay.scaled(30, scale)));
        return panel;
    }

    private static JPanel createInsetPanel(Color fill, Color border, double scale) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(10, scale),
                MenuDisplay.scaled(16, scale),
                MenuDisplay.scaled(10, scale),
                MenuDisplay.scaled(16, scale)));
        return panel;
    }

    private static boolean paintThemedFrame(Graphics2D g2, String slot, int w, int h,
                                            Color topFill, Color bottomFill, int arc) {
        if (g2 == null || w <= 0 || h <= 0) return false;
        Image image = ThemeArt.get(slot);
        if (image == null) return false;
        g2.setPaint(new GradientPaint(0, 0, topFill, 0, h, bottomFill));
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.drawImage(image, 0, 0, w, h, null);
        return true;
    }

    private static Color darker(Color color, float factor) {
        if (color == null) return new Color(8, 16, 24, 180);
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        return new Color(
                Math.max(0, Math.round(color.getRed() * factor)),
                Math.max(0, Math.round(color.getGreen() * factor)),
                Math.max(0, Math.round(color.getBlue() * factor)),
                color.getAlpha());
    }

    private static void syncTeamOptionsForMode(GameMode mode, JComboBox<PlayerTeamChoice> teamBox, int preferredTeamId) {
        if (teamBox == null) return;
        PlayerTeamChoice[] allowed = allowedTeamsForMode(mode);
        teamBox.removeAllItems();
        for (PlayerTeamChoice t : allowed) teamBox.addItem(t);

        PlayerTeamChoice preferred = PlayerTeamChoice.forTeamId(preferredTeamId);
        boolean found = false;
        for (PlayerTeamChoice t : allowed) {
            if (t.teamId() == preferred.teamId()) {
                found = true;
                break;
            }
        }
        teamBox.setSelectedItem(found ? preferred : allowed[0]);
        teamBox.setEnabled(allowed.length > 1);
    }

    private static PlayerTeamChoice[] allowedTeamsForMode(GameMode mode) {
        if (mode == null) return new PlayerTeamChoice[]{PlayerTeamChoice.TEAM_A};
        return switch (mode) {
            case RESOURCE_RUSH, SHOOTING_RANGE -> new PlayerTeamChoice[]{
                    PlayerTeamChoice.TEAM_A,
                    PlayerTeamChoice.TEAM_B,
                    PlayerTeamChoice.TEAM_C,
                    PlayerTeamChoice.TEAM_D
            };
            case CUSTOM_BATTLES -> new PlayerTeamChoice[]{
                    PlayerTeamChoice.TEAM_A,
                    PlayerTeamChoice.TEAM_B,
                    PlayerTeamChoice.TEAM_C,
                    PlayerTeamChoice.TEAM_D,
                    PlayerTeamChoice.TEAM_E
            };
            case FOUR_TEAM_DOMINATION -> new PlayerTeamChoice[]{
                    PlayerTeamChoice.TEAM_A,
                    PlayerTeamChoice.TEAM_B,
                    PlayerTeamChoice.TEAM_C,
                    PlayerTeamChoice.TEAM_D
            };
            default -> new PlayerTeamChoice[]{PlayerTeamChoice.TEAM_A};
        };
    }

    private static void openCustomShipCreator(Component parent) {
        try {
            Class<?> dialogClass = Class.forName("CustomShipBuilderDialog");
            java.lang.reflect.Method show = dialogClass.getMethod("show", Component.class);
            show.invoke(null, parent);
        } catch (ReflectiveOperationException ex) {
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            JOptionPane.showMessageDialog(parent,
                    "Custom shipyard is unavailable: " + message,
                    "Shipyard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static GameConfig buildCustomBattleConfig(Component parent,
                                                      double uiScale,
                                                      int worldW,
                                                      int worldH,
                                                      long seed,
                                                      int playerTeamId) {
        PlayerTeamChoice playerTeam = PlayerTeamChoice.forTeamId(playerTeamId);
        PlayerTeamChoice defaultEnemyTeam = defaultCustomBattleEnemyTeam(playerTeam);
        JComboBox<PlayerTeamChoice> enemyTeamBox = new JComboBox<>(PlayerTeamChoice.values());
        styleCombo(enemyTeamBox);
        scaleCombo(enemyTeamBox, uiScale);
        enemyTeamBox.setSelectedItem(defaultEnemyTeam);

        LinkedHashMap<String, JSpinner> friendlySpinners = createCustomBattleSpinners(uiScale);
        LinkedHashMap<String, JSpinner> enemySpinners = createCustomBattleSpinners(uiScale);
        applyCustomBattleDraft(CustomBattleDraft.createDefault(playerTeam.teamId(), defaultEnemyTeam.teamId()),
                friendlySpinners, enemySpinners, enemyTeamBox);

        JPanel form = new JPanel();
        form.setOpaque(true);
        form.setBackground(new Color(9, 17, 29));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 113, 154)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(18, uiScale),
                        MenuDisplay.scaled(18, uiScale),
                        MenuDisplay.scaled(16, uiScale),
                        MenuDisplay.scaled(18, uiScale))));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(bodyLabel("<html><div style='width:620px;'>"
                + "Launch a battle sandbox with the player inside a mothership. Add exact hull counts to both sides and mix factions however you want."
                + "</div></html>", uiScale));
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(14, uiScale)));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, MenuDisplay.scaled(12, uiScale), 0));
        topRow.setOpaque(true);
        topRow.setBackground(new Color(12, 23, 38));
        topRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(62, 94, 128)),
                BorderFactory.createEmptyBorder(
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale),
                        MenuDisplay.scaled(10, uiScale))));
        JLabel friendlyTeamLabel = label("Player Team: " + playerTeam, uiScale);
        friendlyTeamLabel.setForeground(new Color(214, 228, 242, 210));
        topRow.add(friendlyTeamLabel);
        topRow.add(label("Enemy Team:", uiScale));
        topRow.add(enemyTeamBox);
        form.add(topRow);
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(12, uiScale)));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);
        tabs.setFont(MenuDisplay.font("Consolas", Font.BOLD, 13, uiScale));
        tabs.setBackground(new Color(10, 18, 30));
        tabs.setForeground(new Color(232, 239, 247));
        tabs.addTab("Friendly Fleet", wrapCustomBattleRosterPanel(buildCustomBattleRosterPanel(friendlySpinners, uiScale), uiScale));
        tabs.addTab("Enemy Fleet", wrapCustomBattleRosterPanel(buildCustomBattleRosterPanel(enemySpinners, uiScale), uiScale));
        form.add(tabs);
        form.add(Box.createVerticalStrut(MenuDisplay.scaled(10, uiScale)));

        JPanel toolsRow = transparentPanel();
        toolsRow.setLayout(new FlowLayout(FlowLayout.LEFT, MenuDisplay.scaled(10, uiScale), 0));
        JComboBox<CustomBattlePreset> presetBox = new JComboBox<>(CustomBattlePreset.values());
        styleCombo(presetBox);
        scaleCombo(presetBox, uiScale);
        presetBox.setSelectedItem(CustomBattlePreset.FULL_FLEET_BATTLE);
        JButton presetButton = createMenuButton("Apply Preset", new Color(80, 114, 170), uiScale);
        JButton clearButton = createMenuButton("Clear Counts", new Color(93, 72, 86), uiScale);
        presetButton.addActionListener(e -> applyCustomBattleDraft(
                ((CustomBattlePreset) presetBox.getSelectedItem()).createDraft(
                        playerTeam.teamId(),
                        ((PlayerTeamChoice) enemyTeamBox.getSelectedItem()).teamId()),
                friendlySpinners,
                enemySpinners,
                enemyTeamBox));
        presetBox.addActionListener(e -> applyCustomBattleDraft(
                ((CustomBattlePreset) presetBox.getSelectedItem()).createDraft(
                        playerTeam.teamId(),
                        ((PlayerTeamChoice) enemyTeamBox.getSelectedItem()).teamId()),
                friendlySpinners,
                enemySpinners,
                enemyTeamBox));
        clearButton.addActionListener(e -> clearCustomBattleSpinners(friendlySpinners, enemySpinners));
        toolsRow.add(presetBox);
        toolsRow.add(presetButton);
        toolsRow.add(clearButton);
        form.add(toolsRow);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    parent,
                    form,
                    "Custom Battles Setup",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            PlayerTeamChoice enemyTeam = (PlayerTeamChoice) enemyTeamBox.getSelectedItem();
            if (enemyTeam == null) enemyTeam = defaultEnemyTeam;
            if (enemyTeam.teamId() == playerTeam.teamId()) {
                JOptionPane.showMessageDialog(parent,
                        "Enemy team must be different from the player team.",
                        "Custom Battles",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            String friendlyRoster = serializeCustomBattleRoster(friendlySpinners);
            String enemyRoster = serializeCustomBattleRoster(enemySpinners);
            if (enemyRoster.isBlank()) {
                JOptionPane.showMessageDialog(parent,
                        "Add at least one enemy ship before launching.",
                        "Custom Battles",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            return new GameConfig(
                    GameMode.CUSTOM_BATTLES,
                    worldW,
                    worldH,
                    true,
                    seed,
                    false,
                    playerTeam.teamId(),
                    false,
                    enemyTeam.teamId(),
                    friendlyRoster,
                    enemyRoster);
        }
    }

    private static LinkedHashMap<String, JSpinner> createCustomBattleSpinners(double uiScale) {
        LinkedHashMap<String, JSpinner> spinners = new LinkedHashMap<>();
        for (String roleId : CUSTOM_BATTLE_ROLE_IDS) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 64, 1));
            styleSpinner(spinner, uiScale);
            spinners.put(roleId, spinner);
        }
        return spinners;
    }

    private static JPanel buildCustomBattleRosterPanel(LinkedHashMap<String, JSpinner> spinners, double uiScale) {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(12, 21, 35));
        panel.setBorder(BorderFactory.createEmptyBorder(
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(10, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(10, uiScale)));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(
                MenuDisplay.scaled(5, uiScale),
                MenuDisplay.scaled(8, uiScale),
                MenuDisplay.scaled(5, uiScale),
                MenuDisplay.scaled(8, uiScale));
        c.gridy = 0;

        int idx = 0;
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            int column = idx % 2;
            if (column == 0 && idx > 0) c.gridy++;
            c.gridx = column * 2;
            c.anchor = GridBagConstraints.LINE_END;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0.0;
            panel.add(label(formatCustomBattleRoleLabel(entry.getKey()) + ":", uiScale), c);

            c.gridx = column * 2 + 1;
            c.anchor = GridBagConstraints.LINE_START;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            panel.add(entry.getValue(), c);
            idx++;
        }
        return panel;
    }

    private static JScrollPane wrapCustomBattleRosterPanel(JPanel panel, double uiScale) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setOpaque(true);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(88, 124, 159)));
        scrollPane.getViewport().setBackground(new Color(12, 21, 35));
        scrollPane.getViewport().setOpaque(true);
        scrollPane.setBackground(new Color(12, 21, 35));
        scrollPane.getVerticalScrollBar().setUnitIncrement(MenuDisplay.scaled(16, uiScale));
        scrollPane.setPreferredSize(new Dimension(MenuDisplay.scaled(680, uiScale), MenuDisplay.scaled(360, uiScale)));
        return scrollPane;
    }

    private static void styleSpinner(JSpinner spinner, double uiScale) {
        spinner.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 14, uiScale));
        spinner.setPreferredSize(new Dimension(MenuDisplay.scaled(88, uiScale), MenuDisplay.scaled(30, uiScale)));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            styleField(field);
            field.setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    private static void applyCustomBattleDraft(CustomBattleDraft draft,
                                               LinkedHashMap<String, JSpinner> friendlySpinners,
                                               LinkedHashMap<String, JSpinner> enemySpinners,
                                               JComboBox<PlayerTeamChoice> enemyTeamBox) {
        if (draft == null) return;
        if (enemyTeamBox != null) {
            enemyTeamBox.setSelectedItem(PlayerTeamChoice.forTeamId(draft.enemyTeamId));
        }
        applyCustomBattleCounts(friendlySpinners, draft.friendlyCounts);
        applyCustomBattleCounts(enemySpinners, draft.enemyCounts);
    }

    private static void applyCustomBattleCounts(LinkedHashMap<String, JSpinner> spinners, LinkedHashMap<String, Integer> counts) {
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            int amount = 0;
            if (counts != null && counts.containsKey(entry.getKey())) {
                amount = Math.max(0, counts.get(entry.getKey()));
            }
            entry.getValue().setValue(amount);
        }
    }

    private static void clearCustomBattleSpinners(LinkedHashMap<String, JSpinner> friendlySpinners,
                                                  LinkedHashMap<String, JSpinner> enemySpinners) {
        applyCustomBattleCounts(friendlySpinners, new LinkedHashMap<>());
        applyCustomBattleCounts(enemySpinners, new LinkedHashMap<>());
    }

    private static String serializeCustomBattleRoster(LinkedHashMap<String, JSpinner> spinners) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, JSpinner> entry : spinners.entrySet()) {
            Object value = entry.getValue().getValue();
            int count = (value instanceof Number number) ? number.intValue() : 0;
            if (count <= 0) continue;
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey()).append('=').append(count);
        }
        return out.toString();
    }

    private static PlayerTeamChoice defaultCustomBattleEnemyTeam(PlayerTeamChoice playerTeam) {
        if (playerTeam == null || playerTeam == PlayerTeamChoice.TEAM_A) return PlayerTeamChoice.TEAM_B;
        return PlayerTeamChoice.TEAM_A;
    }

    private static String formatCustomBattleRoleLabel(String roleId) {
        if (roleId == null || roleId.isBlank()) return "Ship";
        String[] parts = roleId.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }

    private static final class CustomBattleDraft {
        final int friendlyTeamId;
        final int enemyTeamId;
        final LinkedHashMap<String, Integer> friendlyCounts;
        final LinkedHashMap<String, Integer> enemyCounts;

        private CustomBattleDraft(int friendlyTeamId,
                                  int enemyTeamId,
                                  LinkedHashMap<String, Integer> friendlyCounts,
                                  LinkedHashMap<String, Integer> enemyCounts) {
            this.friendlyTeamId = friendlyTeamId;
            this.enemyTeamId = enemyTeamId;
            this.friendlyCounts = friendlyCounts;
            this.enemyCounts = enemyCounts;
        }

        static CustomBattleDraft createDefault(int friendlyTeamId, int enemyTeamId) {
            LinkedHashMap<String, Integer> friendly = new LinkedHashMap<>();
            friendly.put("FRIGATE", 4);
            friendly.put("CIWS_CORVETTE", 2);
            friendly.put("LIGHT_CRUISER", 2);
            friendly.put("BATTLECRUISER", 1);
            friendly.put("CARRIER", 1);
            friendly.put("SUPERSHIP", 1);

            LinkedHashMap<String, Integer> enemy = new LinkedHashMap<>();
            enemy.put("FRIGATE", 6);
            enemy.put("MISSILE_BOAT", 3);
            enemy.put("LIGHT_CRUISER", 2);
            enemy.put("BATTLESHIP", 1);
            enemy.put("INTERDICTION_TITAN", 1);
            enemy.put("MOTHERSHIP", 1);

            return new CustomBattleDraft(friendlyTeamId, enemyTeamId, friendly, enemy);
        }
    }

    private enum CustomBattlePreset {
        LIGHT_WEIGHT("Light Weight"),
        MEDIUM("Medium"),
        FULL_FLEET_BATTLE("Full Fleet Battle"),
        THE_LAGGENING("The Laggening"),
        WHY("Why");

        private final String label;

        CustomBattlePreset(String label) {
            this.label = label;
        }

        CustomBattleDraft createDraft(int friendlyTeamId, int enemyTeamId) {
            return switch (this) {
                case LIGHT_WEIGHT -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 3),
                                entry("CIWS_CORVETTE", 1),
                                entry("LIGHT_CRUISER", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 4),
                                entry("MISSILE_BOAT", 1),
                                entry("LIGHT_CRUISER", 1)
                        ));
                case MEDIUM -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 6),
                                entry("CIWS_CORVETTE", 2),
                                entry("LIGHT_CRUISER", 2),
                                entry("BATTLECRUISER", 1),
                                entry("CARRIER", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 8),
                                entry("MISSILE_BOAT", 3),
                                entry("LIGHT_CRUISER", 2),
                                entry("BATTLECRUISER", 1),
                                entry("BATTLESHIP", 1)
                        ));
                case FULL_FLEET_BATTLE -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FRIGATE", 10),
                                entry("CIWS_CORVETTE", 4),
                                entry("MISSILE_BOAT", 3),
                                entry("LIGHT_CRUISER", 3),
                                entry("CRUISER", 2),
                                entry("BATTLECRUISER", 2),
                                entry("BATTLESHIP", 1),
                                entry("CARRIER", 1),
                                entry("SUPERSHIP", 1)
                        ),
                        mapOf(
                                entry("FRIGATE", 12),
                                entry("CIWS_CORVETTE", 4),
                                entry("MISSILE_BOAT", 4),
                                entry("LIGHT_CRUISER", 3),
                                entry("CRUISER", 2),
                                entry("BATTLECRUISER", 2),
                                entry("BATTLESHIP", 1),
                                entry("INTERDICTION_TITAN", 1),
                                entry("MOTHERSHIP", 1)
                        ));
                case THE_LAGGENING -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FIGHTER", 16),
                                entry("BOMBER", 10),
                                entry("DRONE", 12),
                                entry("FRIGATE", 16),
                                entry("CIWS_CORVETTE", 8),
                                entry("MISSILE_BOAT", 8),
                                entry("LIGHT_CRUISER", 6),
                                entry("CRUISER", 4),
                                entry("BATTLECRUISER", 3),
                                entry("BATTLESHIP", 2),
                                entry("CARRIER", 2),
                                entry("SUPERSHIP", 1),
                                entry("MOTHERSHIP", 1)
                        ),
                        mapOf(
                                entry("FIGHTER", 18),
                                entry("BOMBER", 12),
                                entry("DRONE", 14),
                                entry("FRIGATE", 18),
                                entry("CIWS_CORVETTE", 8),
                                entry("MISSILE_BOAT", 10),
                                entry("LIGHT_CRUISER", 6),
                                entry("CRUISER", 4),
                                entry("BATTLECRUISER", 3),
                                entry("BATTLESHIP", 2),
                                entry("DRONE_CARRIER", 2),
                                entry("INTERDICTION_TITAN", 1),
                                entry("HYPERWEAPON_TITAN", 1)
                        ));
                case WHY -> draft(friendlyTeamId, enemyTeamId,
                        mapOf(
                                entry("FIGHTER", 32),
                                entry("BOMBER", 20),
                                entry("PD_CRAFT", 16),
                                entry("DRONE", 24),
                                entry("FRIGATE", 26),
                                entry("CIWS_CORVETTE", 14),
                                entry("MISSILE_BOAT", 14),
                                entry("LIGHT_CRUISER", 10),
                                entry("CRUISER", 8),
                                entry("BATTLECRUISER", 5),
                                entry("BATTLESHIP", 4),
                                entry("DREADNOUGHT", 2),
                                entry("CARRIER", 3),
                                entry("DRONE_CARRIER", 2),
                                entry("SUPERSHIP", 2),
                                entry("MOTHERSHIP", 1),
                                entry("FLEET_TELEPORTER_TITAN", 1),
                                entry("ELITE_SUPERSHIP_COMMAND_TITAN", 1)
                        ),
                        mapOf(
                                entry("FIGHTER", 36),
                                entry("BOMBER", 24),
                                entry("PD_CRAFT", 18),
                                entry("DRONE", 28),
                                entry("FRIGATE", 30),
                                entry("CIWS_CORVETTE", 16),
                                entry("MISSILE_BOAT", 16),
                                entry("LIGHT_CRUISER", 12),
                                entry("CRUISER", 8),
                                entry("BATTLECRUISER", 6),
                                entry("BATTLESHIP", 4),
                                entry("DREADNOUGHT", 2),
                                entry("CARRIER", 2),
                                entry("DRONE_CARRIER", 3),
                                entry("SUPERSHIP", 2),
                                entry("MOTHERSHIP", 1),
                                entry("INTERDICTION_TITAN", 1),
                                entry("HYPERWEAPON_TITAN", 1),
                                entry("ELITE_REINFORCEMENTS_TITAN", 1)
                        ));
            };
        }

        @Override
        public String toString() {
            return label;
        }

        private static CustomBattleDraft draft(int friendlyTeamId,
                                               int enemyTeamId,
                                               LinkedHashMap<String, Integer> friendly,
                                               LinkedHashMap<String, Integer> enemy) {
            return new CustomBattleDraft(friendlyTeamId, enemyTeamId, friendly, enemy);
        }

        @SafeVarargs
        private static LinkedHashMap<String, Integer> mapOf(Map.Entry<String, Integer>... entries) {
            LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
            if (entries == null) return map;
            for (Map.Entry<String, Integer> entry : entries) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
                map.put(entry.getKey(), entry.getValue());
            }
            return map;
        }

        private static Map.Entry<String, Integer> entry(String roleId, int count) {
            return Map.entry(roleId, count);
        }
    }

    private static final class MenuBattlePanel extends JPanel {
        private final MenuBattleSimulation simulation;
        private final double uiScale;

        MenuBattlePanel(MenuBattleSimulation simulation, double uiScale) {
            this.simulation = simulation;
            this.uiScale = uiScale;
            setOpaque(false);
            setMinimumSize(new Dimension(MenuDisplay.scaled(560, uiScale), MenuDisplay.scaled(360, uiScale)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(2, 8, 16, 126), 0, h,
                    new Color(3, 5, 10, 54)));
            g2.fillRect(0, 0, w, h);
            drawBattleGrid(g2, w, h, uiScale);
            if (simulation != null) {
                simulation.draw(g2, w, h, uiScale);
            }

            int pad = MenuDisplay.scaled(18, uiScale);
            g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 12, uiScale));
            FontMetrics titleFm = g2.getFontMetrics();
            int titleY = pad + titleFm.getAscent();
            g2.setColor(new Color(125, 214, 231, 210));
            g2.drawString("TACTICAL ATTRACT MODE", pad, titleY);
            g2.setFont(MenuDisplay.font("Consolas", Font.PLAIN, 12, uiScale));
            FontMetrics subtitleFm = g2.getFontMetrics();
            int subtitleY = titleY + Math.max(MenuDisplay.scaled(14, uiScale),
                    titleFm.getDescent() + subtitleFm.getAscent() + MenuDisplay.scaled(4, uiScale));
            g2.setColor(new Color(198, 211, 226, 166));
            g2.drawString("Disposable combat sandbox - no campaign state attached",
                    pad, subtitleY);
            g2.dispose();
        }

        private static void drawBattleGrid(Graphics2D g2, int w, int h, double uiScale) {
            g2.setColor(new Color(96, 157, 190, 25));
            int gap = Math.max(24, MenuDisplay.scaled(58, uiScale));
            for (int x = gap; x < w; x += gap) {
                g2.drawLine(x, 0, x, h);
            }
            for (int y = gap; y < h; y += gap) {
                g2.drawLine(0, y, w, y);
            }
            g2.setColor(new Color(255, 255, 255, 16));
            for (int y = 0; y < h; y += 4) {
                g2.drawLine(0, y, w, y);
            }
        }
    }

    private static final class MenuBattleSimulation {
        private static final int MAX_SHIPS = 10;
        private final Random random;
        private final List<MenuShip> ships = new ArrayList<>();
        private final List<MenuShot> shots = new ArrayList<>();
        private final List<MenuExplosion> explosions = new ArrayList<>();
        private double replacementTimer = 2.8;
        private double scenarioResetTimer = 0.0;
        private int scenarioIndex = 0;

        MenuBattleSimulation(long seed) {
            random = new Random(seed);
            startBattleScenario();
        }

        void update(double delta) {
            if (delta <= 0.0) return;
            delta = Math.min(delta, 0.08);

            if (scenarioResetTimer > 0.0) {
                scenarioResetTimer -= delta;
                updateExplosions(delta);
                if (scenarioResetTimer <= 0.0) {
                    startBattleScenario();
                }
                return;
            }

            updateShips(delta);
            updateShots(delta);
            updateExplosions(delta);
            ships.removeIf(ship -> ship.hp <= 0.0);

            int blue = livingShips(0);
            int red = livingShips(1);
            if ((blue == 0 || red == 0) && !ships.isEmpty()) {
                scenarioResetTimer = 4.0 + random.nextDouble() * 3.0;
                return;
            }
            if (ships.isEmpty()) {
                startBattleScenario();
                return;
            }

            replacementTimer -= delta;
            if (replacementTimer <= 0.0) {
                if (ships.size() < MAX_SHIPS && blue > 0 && red > 0) {
                    spawnReplacement(blue <= red ? 0 : 1);
                }
                replacementTimer = 2.5 + random.nextDouble() * 3.8;
            }
        }

        void draw(Graphics2D g2, int width, int height, double uiScale) {
            drawContrails(g2, width, height);
            for (MenuShot shot : shots) {
                shot.draw(g2, width, height, uiScale);
            }
            for (MenuShip ship : ships) {
                ship.draw(g2, width, height, uiScale);
            }
            for (MenuExplosion explosion : explosions) {
                explosion.draw(g2, width, height);
            }

            g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 11, uiScale));
            String status = "BLUE " + livingShips(0) + "  /  RED " + livingShips(1);
            int x = Math.max(10, width - MenuDisplay.scaled(190, uiScale));
            int y = Math.max(22, height - MenuDisplay.scaled(22, uiScale));
            g2.setColor(new Color(7, 12, 20, 138));
            g2.fillRoundRect(x - 10, y - 18, MenuDisplay.scaled(178, uiScale), 28, 8, 8);
            g2.setColor(new Color(218, 229, 240, 188));
            g2.drawString(status, x, y);
        }

        private void startBattleScenario() {
            ships.clear();
            shots.clear();
            explosions.clear();
            scenarioResetTimer = 0.0;
            replacementTimer = 2.5 + random.nextDouble() * 2.5;
            scenarioIndex++;

            boolean titanPass = scenarioIndex % 5 == 0 || random.nextDouble() < 0.12;
            int blueCount = titanPass ? 2 + random.nextInt(2) : 3 + random.nextInt(2);
            int redCount = titanPass ? 4 + random.nextInt(3) : 3 + random.nextInt(3);
            for (int i = 0; i < blueCount; i++) {
                spawnShip(0, i, blueCount, titanPass && i == 0);
            }
            for (int i = 0; i < redCount; i++) {
                spawnShip(1, i, redCount, false);
            }
        }

        private void spawnReplacement(int team) {
            spawnShip(team, livingShips(team), Math.max(3, livingShips(team) + 1), false);
        }

        private void spawnShip(int team, int index, int count, boolean titan) {
            double lane = (index + 1.0) / (count + 1.0);
            double x = team == 0
                    ? 0.10 + random.nextDouble() * 0.10
                    : 0.90 - random.nextDouble() * 0.10;
            double y = 0.14 + lane * 0.72 + (random.nextDouble() - 0.5) * 0.05;
            String label = titan ? "TITAN" : switch (random.nextInt(5)) {
                case 0 -> "CRUISER";
                case 1 -> "FRIGATE";
                case 2 -> "MISSILE";
                case 3 -> "CARRIER";
                default -> "PICKET";
            };
            MenuShip ship = new MenuShip(team, label, x, clamp01(y), titan,
                    random.nextDouble(), random.nextDouble() * Math.PI * 2.0);
            ship.vx = team == 0 ? 0.020 + random.nextDouble() * 0.018 : -0.020 - random.nextDouble() * 0.018;
            ship.vy = (random.nextDouble() - 0.5) * 0.016;
            ship.cooldown = 0.4 + random.nextDouble() * 1.2;
            ships.add(ship);
        }

        private void updateShips(double delta) {
            for (MenuShip ship : ships) {
                if (ship.hp <= 0.0) continue;
                MenuShip target = nearestEnemy(ship);
                if (target != null) {
                    double dx = target.x - ship.x;
                    double dy = target.y - ship.y;
                    double dist = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
                    double desiredRange = ship.titan ? 0.40 : 0.31;
                    double approach = dist > desiredRange ? 0.030 : -0.010;
                    double strafe = Math.sin(ship.phase) * 0.020;
                    double targetVx = dx / dist * approach;
                    double targetVy = dy / dist * approach + strafe;
                    ship.vx += (targetVx - ship.vx) * Math.min(1.0, delta * 0.9);
                    ship.vy += (targetVy - ship.vy) * Math.min(1.0, delta * 0.9);
                    ship.cooldown -= delta;
                    if (ship.cooldown <= 0.0 && dist < 0.58) {
                        fire(ship, target);
                        ship.cooldown = (ship.titan ? 0.55 : 0.78) + random.nextDouble() * 0.85;
                    }
                }
                ship.phase += delta * (1.1 + ship.size * 8.0);
                ship.x = clamp(ship.x + ship.vx * delta, 0.05, 0.95);
                ship.y = clamp(ship.y + ship.vy * delta, 0.10, 0.92);
            }
        }

        private void updateShots(double delta) {
            Iterator<MenuShot> iterator = shots.iterator();
            while (iterator.hasNext()) {
                MenuShot shot = iterator.next();
                shot.update(delta);
                if (shot.target != null && shot.target.hp > 0.0) {
                    double dx = shot.target.x - shot.x;
                    double dy = shot.target.y - shot.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < shot.target.size * 0.65 + 0.012) {
                        shot.target.hp -= shot.damage;
                        explosions.add(new MenuExplosion(shot.x, shot.y, shot.team == 0
                                ? new Color(95, 188, 255) : new Color(255, 116, 92), 0.18));
                        if (shot.target.hp <= 0.0) {
                            explosions.add(new MenuExplosion(shot.target.x, shot.target.y,
                                    shot.target.team == 0 ? new Color(76, 159, 232) : new Color(235, 91, 74),
                                    shot.target.titan ? 1.25 : 0.72));
                        }
                        iterator.remove();
                        continue;
                    }
                }
                if (shot.ttl <= 0.0 || shot.x < -0.08 || shot.x > 1.08 || shot.y < -0.08 || shot.y > 1.08) {
                    iterator.remove();
                }
            }
        }

        private void updateExplosions(double delta) {
            Iterator<MenuExplosion> iterator = explosions.iterator();
            while (iterator.hasNext()) {
                MenuExplosion explosion = iterator.next();
                explosion.age += delta;
                if (explosion.age >= explosion.life) {
                    iterator.remove();
                }
            }
        }

        private void fire(MenuShip ship, MenuShip target) {
            double dx = target.x - ship.x;
            double dy = target.y - ship.y;
            double dist = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
            double speed = ship.titan ? 0.72 : 0.56;
            shots.add(new MenuShot(ship.team, target, ship.x, ship.y,
                    dx / dist * speed, dy / dist * speed, ship.titan ? 38.0 : 18.0));
        }

        private MenuShip nearestEnemy(MenuShip ship) {
            MenuShip best = null;
            double bestDist = Double.MAX_VALUE;
            for (MenuShip other : ships) {
                if (other == ship || other.team == ship.team || other.hp <= 0.0) continue;
                double dx = other.x - ship.x;
                double dy = other.y - ship.y;
                double d2 = dx * dx + dy * dy;
                if (d2 < bestDist) {
                    bestDist = d2;
                    best = other;
                }
            }
            return best;
        }

        private int livingShips(int team) {
            int count = 0;
            for (MenuShip ship : ships) {
                if (ship.team == team && ship.hp > 0.0) count++;
            }
            return count;
        }

        private void drawContrails(Graphics2D g2, int width, int height) {
            for (MenuShip ship : ships) {
                int x = (int) Math.round(ship.x * width);
                int y = (int) Math.round(ship.y * height);
                int tailX = (int) Math.round((ship.x - ship.vx * 5.8) * width);
                int tailY = (int) Math.round((ship.y - ship.vy * 5.8) * height);
                g2.setStroke(new BasicStroke((float) Math.max(1.0, ship.size * width * 0.18)));
                g2.setColor(ship.team == 0 ? new Color(72, 174, 255, 58) : new Color(255, 108, 86, 52));
                g2.drawLine(tailX, tailY, x, y);
            }
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private static final class MenuShip {
        final int team;
        final String label;
        final boolean titan;
        final double maxHp;
        final double size;
        double x;
        double y;
        double vx;
        double vy;
        double hp;
        double cooldown;
        double phase;

        MenuShip(int team, String label, double x, double y, boolean titan, double sizeJitter, double phase) {
            this.team = team;
            this.label = label;
            this.x = x;
            this.y = y;
            this.titan = titan;
            this.size = titan ? 0.062 : 0.030 + sizeJitter * 0.010;
            this.maxHp = titan ? 240.0 : 82.0 + this.size * 650.0;
            this.hp = maxHp;
            this.phase = phase;
        }

        void draw(Graphics2D g2, int width, int height, double uiScale) {
            int px = (int) Math.round(x * width);
            int py = (int) Math.round(y * height);
            int len = Math.max(18, (int) Math.round(size * width));
            int beam = Math.max(8, (int) Math.round(size * height * 0.58));
            double angle = Math.atan2(vy, vx);
            Color hull = team == 0 ? new Color(81, 166, 229) : new Color(224, 86, 72);
            Color core = team == 0 ? new Color(155, 219, 255) : new Color(255, 177, 130);
            Polygon hullShape = orientedHull(px, py, len, beam, angle);

            g2.setColor(new Color(0, 0, 0, 110));
            g2.translate(2, 3);
            g2.fillPolygon(hullShape);
            g2.translate(-2, -3);
            g2.setColor(new Color(hull.getRed(), hull.getGreen(), hull.getBlue(), titan ? 232 : 218));
            g2.fillPolygon(hullShape);
            g2.setColor(new Color(235, 243, 250, 92));
            g2.drawPolygon(hullShape);
            g2.setColor(core);
            int coreSize = Math.max(3, beam / 3);
            g2.fillOval(px - coreSize / 2, py - coreSize / 2, coreSize, coreSize);

            int barW = Math.max(22, len);
            int barY = py + beam + 8;
            g2.setColor(new Color(3, 7, 12, 160));
            g2.fillRect(px - barW / 2, barY, barW, 3);
            g2.setColor(team == 0 ? new Color(99, 216, 174, 198) : new Color(255, 154, 100, 198));
            g2.fillRect(px - barW / 2, barY, Math.max(1, (int) Math.round(barW * hp / maxHp)), 3);

            if (titan) {
                g2.setFont(MenuDisplay.font("Consolas", Font.BOLD, 10, uiScale));
                g2.setColor(new Color(231, 239, 247, 160));
                g2.drawString(label, px - barW / 2, barY + 15);
            }
        }

        private static Polygon orientedHull(int cx, int cy, int len, int beam, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int[][] points = {
                    {len / 2, 0},
                    {len / 8, -beam / 2},
                    {-len / 2, -beam / 3},
                    {-len / 3, 0},
                    {-len / 2, beam / 3},
                    {len / 8, beam / 2}
            };
            Polygon polygon = new Polygon();
            for (int[] point : points) {
                int x = cx + (int) Math.round(point[0] * cos - point[1] * sin);
                int y = cy + (int) Math.round(point[0] * sin + point[1] * cos);
                polygon.addPoint(x, y);
            }
            return polygon;
        }
    }

    private static final class MenuShot {
        final int team;
        final MenuShip target;
        final double vx;
        final double vy;
        final double damage;
        double x;
        double y;
        double ttl = 1.35;

        MenuShot(int team, MenuShip target, double x, double y, double vx, double vy, double damage) {
            this.team = team;
            this.target = target;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = damage;
        }

        void update(double delta) {
            x += vx * delta;
            y += vy * delta;
            ttl -= delta;
        }

        void draw(Graphics2D g2, int width, int height, double uiScale) {
            int x1 = (int) Math.round(x * width);
            int y1 = (int) Math.round(y * height);
            int x0 = (int) Math.round((x - vx * 0.030) * width);
            int y0 = (int) Math.round((y - vy * 0.030) * height);
            Color color = team == 0 ? new Color(94, 196, 255, 228) : new Color(255, 111, 79, 228);
            g2.setStroke(new BasicStroke(Math.max(1.2f, (float) MenuDisplay.scaled(2, uiScale))));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
            g2.drawLine(x0, y0, x1, y1);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(color);
            g2.fillOval(x1 - 2, y1 - 2, 4, 4);
        }
    }

    private static final class MenuExplosion {
        final double x;
        final double y;
        final Color color;
        final double scale;
        final double life;
        double age = 0.0;

        MenuExplosion(double x, double y, Color color, double scale) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.scale = scale;
            this.life = 0.42 + scale * 0.28;
        }

        void draw(Graphics2D g2, int width, int height) {
            double p = Math.max(0.0, Math.min(1.0, age / life));
            int alpha = (int) Math.round((1.0 - p) * 210.0);
            int radius = (int) Math.round((8.0 + p * 38.0) * scale);
            int px = (int) Math.round(x * width);
            int py = (int) Math.round(y * height);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
            g2.fillOval(px - radius, py - radius, radius * 2, radius * 2);
            g2.setColor(new Color(255, 240, 181, Math.max(0, alpha / 2)));
            g2.drawOval(px - radius - 3, py - radius - 3, radius * 2 + 6, radius * 2 + 6);
        }
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class FlowPanel extends JPanel {
        FlowPanel(int align, int hgap, int vgap) {
            super(new FlowLayout(align, hgap, vgap));
            setOpaque(false);
        }
    }

    private static final class MenuButton extends JButton {
        private final Color baseFill;

        MenuButton(String text, Color fill, double scale) {
            super(text);
            this.baseFill = fill;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(true);
            setForeground(Color.WHITE);
            setFont(MenuDisplay.font("Consolas", Font.BOLD, 15, scale));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(BorderFactory.createEmptyBorder(
                    MenuDisplay.scaled(8, scale),
                    MenuDisplay.scaled(18, scale),
                    MenuDisplay.scaled(8, scale),
                    MenuDisplay.scaled(18, scale)));
            setMargin(new Insets(0, 0, 0, 0));
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            ButtonModel model = getModel();
            Color fill = !isEnabled()
                    ? blend(baseFill, new Color(30, 38, 47), 0.55)
                    : model.isPressed()
                    ? blend(baseFill, Color.BLACK, 0.24)
                    : model.isRollover()
                    ? blend(baseFill, Color.WHITE, 0.14)
                    : baseFill;

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 48), 0, h / 2f, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(0, 0, w, Math.max(1, h / 2), 8, 8);
            g2.setColor(new Color(255, 255, 255, isEnabled() ? 70 : 24));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            if (isFocusOwner()) {
                g2.setStroke(new BasicStroke(2.4f));
                g2.setColor(new Color(255, 232, 124, 235));
                g2.drawRoundRect(3, 3, Math.max(1, w - 7), Math.max(1, h - 7), 8, 8);
                g2.setColor(new Color(20, 26, 34, 210));
                g2.drawRoundRect(6, 6, Math.max(1, w - 13), Math.max(1, h - 13), 6, 6);
            }
            g2.dispose();

            super.paintComponent(g);
        }
    }

    private static Color blend(Color base, Color mix, double mixAmount) {
        double clamped = Math.max(0.0, Math.min(1.0, mixAmount));
        double keep = 1.0 - clamped;
        int red = (int) Math.round(base.getRed() * keep + mix.getRed() * clamped);
        int green = (int) Math.round(base.getGreen() * keep + mix.getGreen() * clamped);
        int blue = (int) Math.round(base.getBlue() * keep + mix.getBlue() * clamped);
        int alpha = (int) Math.round(base.getAlpha() * keep + mix.getAlpha() * clamped);
        return new Color(red, green, blue, alpha);
    }

    @FunctionalInterface
    public interface ResumeCampaignProvider {
        ResumeCampaignState load();

        default ResumeCampaignState load(String slotId) {
            return load();
        }

        default boolean delete(String slotId) {
            return false;
        }
    }

    @FunctionalInterface
    public interface SpaceBackgroundPainter {
        void paint(Graphics2D g2, double camX, double camY, int width, int height, long seed);
    }

    public interface MenuBattleView {
        void update(double deltaSeconds);
    }

    public record ResumeCampaignState(boolean available, String summaryText, GameConfig config) {
        public static ResumeCampaignState unavailable(String summaryText) {
            return new ResumeCampaignState(false, summaryText, null);
        }

        public static ResumeCampaignState available(String summaryText, GameConfig config) {
            return new ResumeCampaignState(true, summaryText, config);
        }
    }
}
