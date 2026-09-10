package net.plat12.kaantaa;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Kaantaa extends Application {

    private static final String CARD_DROP = "drop";
    private static final String CARD_MAIN = "main";
    private static final String CARD_UPDATE = "update";
    private static final String CARD_GUIDE = "guide";
    private final ContextMenu langSuggestPopup = new ContextMenu();
    private final ObservableList<Map.Entry<String, Translation.Entry>> currentRows =
            FXCollections.observableArrayList();
    private boolean darkMode = true;
    private Translation translation;
    private File loadedDirectory;
    private File loadedFile;
    private volatile String lastLangCode = "";
    private volatile boolean autoSaved = false;
    private File pendingUpdateProgressFile = null;
    private String currentCard = CARD_DROP;
    private StackPane cardPane;
    private Pane dropPane;
    private Pane mainPane;
    private Pane updatePane;
    private Pane guidePane;
    private TextField searchField;
    private ComboBox<Translation.SearchLocation> searchLocationCombo;
    private ComboBox<Translation.SortingType> sortCombo;
    private ComboBox<Translation.FilterType> filterCombo;
    private CheckBox reverseCheck;
    private CheckBox placeholderOnlyCheck;
    private boolean showKeyAsFirstColumn = false;
    private Label resultCountLabel;
    private TableView<Map.Entry<String, Translation.Entry>> entryTable;
    private TableColumn<Map.Entry<String, Translation.Entry>, String> firstCol;

    private Map.Entry<String, Translation.Entry> selectedEntry = null;
    private boolean updatingDetail = false;
    private ProgressBar completionBar;
    private Label completionLabel;

    private Label detailKeyLabel;
    private TextArea detailOriginalArea;
    private TextArea detailTranslatedArea;
    private CheckBox detailFinishedCheck;
    private Label detailPlaceholderLabel;

    private TextField findField;
    private TextField replaceField;
    private CheckBox matchCaseCheck;
    private CheckBox onlyVisibleCheck;

    private TextField langCodeField;
    private Button exportButton;
    private Label exportStatusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    private static Background fill(Color color) {
        return new Background(new BackgroundFill(color, null, null));
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Kaantaa");
        primaryStage.getIcons().add(loadAppIcon());

        AppTheme.applyTheme(darkMode);

        cardPane = new StackPane();
        cardPane.setBackground(fill(AppTheme.BG_MAIN));

        rebuildAllPanes();

        Scene scene = new Scene(cardPane, 1300, 840);
        primaryStage.setMinWidth(940);
        primaryStage.setMinHeight(620);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();

        applyThemeStylesheet(scene);

        primaryStage.setOnCloseRequest(event -> {
            autoSaveQuietly();
            Platform.exit();
            System.exit(0);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::autoSaveQuietly));
        primaryStage.show();
    }

    private void applyThemeStylesheet(Scene scene) {
        String css = buildThemeCss();
        String encoded = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        scene.getStylesheets().setAll("data:text/css;base64," + encoded);
    }

    private String buildThemeCss() {
        String css = """
                .table-view {
                    -fx-table-header-border-color: transparent;
                    -fx-table-cell-border-color: transparent;
                    -fx-background-color: $bgMain;
                }
                .table-view .column-header-background { -fx-background-color: $bgPanel; }
                .table-view .column-header, .table-view .filler {
                    -fx-background-color: $bgPanel;
                    -fx-text-fill: $fgText;
                    -fx-border-color: $border;
                    -fx-border-width: 0 0 1 0;
                }
                .table-view .column-header .label { -fx-text-fill: $fgText; }
                .table-view .table-cell { -fx-text-fill: $fgText; }
                .table-view .scroll-bar:vertical,
                .table-view .scroll-bar:horizontal { -fx-background-color: $bgMain; }
                .table-view .scroll-bar .thumb { -fx-background-color: $accent; }
                .table-view .scroll-bar .track { -fx-background-color: $bgMain; }
                .check-box .box {
                    -fx-background-color: $bgTextField;
                    -fx-border-color: $border;
                    -fx-border-radius: 3;
                }
                .check-box:selected .mark { -fx-background-color: $accent; }
                .progress-bar { -fx-accent: $accent; -fx-background-color: $bgPanel; }
                .progress-bar .track { -fx-background-color: $bgPanel; }
                .progress-bar .bar { -fx-background-color: $accent; }
                .split-pane .split-pane-divider { -fx-background-color: $border; }
                .scroll-pane { -fx-background-color: $bgMain; }
                .scroll-pane .viewport { -fx-background-color: $bgMain; }
                .scroll-pane .scroll-bar .thumb { -fx-background-color: $accent; }
                .scroll-pane .scroll-bar .track { -fx-background-color: $bgMain; }
                .scroll-bar .increment-arrow,
                .scroll-bar .decrement-arrow { -fx-background-color: $fgMuted; }
                """;
        return css
                .replace("$bgMain", toRgbString(AppTheme.BG_MAIN))
                .replace("$bgPanel", toRgbString(AppTheme.BG_PANEL))
                .replace("$bgTextField", toRgbString(AppTheme.BG_TEXTFIELD))
                .replace("$fgText", toRgbString(AppTheme.FG_TEXT))
                .replace("$fgMuted", toRgbString(AppTheme.FG_MUTED))
                .replace("$accent", toRgbString(AppTheme.ACCENT))
                .replace("$border", toRgbString(AppTheme.BORDER));
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        AppTheme.applyTheme(darkMode);
        rebuildAllPanes();
        if (cardPane.getScene() != null) {
            applyThemeStylesheet(cardPane.getScene());
        }
        if (translation != null && CARD_MAIN.equals(currentCard)) {
            refreshList();
            updateCompletion();
        }
    }

    private void rebuildAllPanes() {
        pendingUpdateProgressFile = null;
        dropPane = buildDropPane();
        mainPane = buildMainPane();
        updatePane = buildUpdatePane();
        guidePane = buildGuidePane();
        cardPane.getChildren().setAll(dropPane, mainPane, updatePane, guidePane);
        showPane(currentCard);
    }

    private void showPane(String name) {
        currentCard = name;
        dropPane.setVisible(CARD_DROP.equals(name));
        mainPane.setVisible(CARD_MAIN.equals(name));
        updatePane.setVisible(CARD_UPDATE.equals(name));
        guidePane.setVisible(CARD_GUIDE.equals(name));
    }

    private Node createTiledBackground() {
        return new TiledBackgroundRegion();
    }

    private StackPane buildBannerScreen(Region contentBox) {
        StackPane outer = new StackPane();
        outer.setAlignment(Pos.CENTER);
        outer.setPadding(new Insets(20));
        outer.getChildren().add(createTiledBackground());

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(10));
        layout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        try {
            Image bannerImage = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/banner.png")));
            ImageView banner = new ImageView(bannerImage);
            banner.setPreserveRatio(true);
            banner.setFitWidth(1200);
            banner.setFitHeight(1000);
            banner.setSmooth(true);
            layout.setTop(banner);
            BorderPane.setAlignment(banner, Pos.TOP_CENTER);
        } catch (Exception ignored) {
        }

        layout.setCenter(contentBox);
        outer.getChildren().add(layout);
        return outer;
    }

    private VBox buildDropBoxShell() {
        VBox inner = new VBox(10);
        inner.setAlignment(Pos.CENTER);
        inner.setMaxWidth(450);
        inner.setMaxHeight(350);
        inner.setPadding(new Insets(20));
        inner.setStyle(
                "-fx-border-color: " + toRgbString(AppTheme.FG_MUTED) + ";"
                        + "-fx-border-style: dashed; -fx-border-width: 2;"
                        + "-fx-background-color: "
                        + toRgbString(AppTheme.BG_PANEL.deriveColor(0, 1, 1, 0.3)) + ";");
        return inner;
    }

    private Pane buildDropPane() {
        VBox inner = buildDropBoxShell();

        Label icon = new Label("📁");
        icon.setFont(Font.font(36));
        icon.setTextFill(AppTheme.FG_TEXT);

        Label instructions = new Label(
                "Drag and drop a mod's language file (like en_us.json) here\n" +
                        "to start a new translation, or a saved _kaantaa.json progress file to resume one.");
        instructions.setWrapText(true);
        instructions.setTextAlignment(TextAlignment.CENTER);
        instructions.setTextFill(AppTheme.FG_MUTED);

        Button browseBtn = createStyledButton("Or browse for a file…");
        browseBtn.setOnAction(e -> browseForLanguageFile());

        Button updateBtn = createStyledButton("Update existing translation");
        updateBtn.setOnAction(e -> showPane(CARD_UPDATE));

        Button themeToggle = createStyledButton(themeToggleLabel());
        themeToggle.setOnAction(e -> toggleTheme());

        inner.getChildren().addAll(icon, instructions, browseBtn, updateBtn, themeToggle);

        if (translation != null) {
            Button resumeBtn = createStyledButton("Resume current translation");
            resumeBtn.setOnAction(e -> showMainUi());
            inner.getChildren().add(resumeBtn);
        }

        StackPane outer = buildBannerScreen(inner);
        setDropHandlers(outer, files -> {
            handleDroppedFile(files.getFirst());
            return true;
        });
        return outer;
    }

    private void browseForLanguageFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select JSON file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON language files", "*.json"));
        File f = chooser.showOpenDialog(null);
        if (f != null) handleDroppedFile(f);
    }

    private Pane buildUpdatePane() {
        VBox inner = buildDropBoxShell();

        Label icon = new Label("🔄");
        icon.setFont(Font.font(36));
        icon.setTextFill(AppTheme.FG_TEXT);

        Label instructions = new Label(
                "Drop the old _kaantaa.json progress file and the new language .json file\n" +
                        "to merge them. Or use the button below.");
        instructions.setWrapText(true);
        instructions.setTextAlignment(TextAlignment.CENTER);
        instructions.setTextFill(AppTheme.FG_MUTED);

        Label statusLabel = new Label(
                "Drop old progress file first, then new language file (or drop both at once).");
        statusLabel.setWrapText(true);
        statusLabel.setTextAlignment(TextAlignment.CENTER);
        statusLabel.setTextFill(AppTheme.FG_MUTED);

        Button browseOldBtn = createStyledButton("Select old progress file");
        browseOldBtn.setOnAction(e -> browseForUpdateFiles(statusLabel));

        Button backBtn = createStyledButton("Back to main menu");
        backBtn.setOnAction(e -> {
            pendingUpdateProgressFile = null;
            showPane(CARD_DROP);
        });

        Button themeToggle = createStyledButton(themeToggleLabel());
        themeToggle.setOnAction(e -> toggleTheme());

        inner.getChildren().addAll(icon, instructions, statusLabel, browseOldBtn, backBtn, themeToggle);

        StackPane outer = buildBannerScreen(inner);
        setDropHandlers(outer, files -> handleUpdateDrop(files, statusLabel));
        return outer;
    }

    private void browseForUpdateFiles(Label statusLabel) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select old progress file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Kaantaa progress files", "*.json"));
        File oldFile = chooser.showOpenDialog(null);
        if (oldFile == null) return;

        pendingUpdateProgressFile = oldFile;
        statusLabel.setText("Old progress file selected. Now select the new language file.");

        FileChooser chooser2 = new FileChooser();
        chooser2.setTitle("Select new language file");
        chooser2.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Language JSON files", "*.json"));
        File newFile = chooser2.showOpenDialog(null);

        if (newFile != null) {
            handleUpdateFiles(oldFile, newFile);
            statusLabel.setText("Update complete.");
        } else {
            statusLabel.setText("Update cancelled.");
        }
        pendingUpdateProgressFile = null;
    }

    private Pane buildMainPane() {
        VBox leftPanel = buildLeftPanel();

        BorderPane rightTop = new BorderPane();
        rightTop.setMinHeight(0);
        rightTop.setPrefHeight(200);
        rightTop.setCenter(buildLogoContainer());

        VBox rightBottom = buildRightBottomPanel();
        VBox.setVgrow(rightBottom, Priority.ALWAYS);

        SplitPane rightSplit = new SplitPane();
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.getItems().addAll(rightTop, rightBottom);
        rightSplit.setDividerPositions(0.4);
        rightSplit.setBorder(null);

        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(leftPanel, rightSplit);
        mainSplit.setDividerPositions(0.5);
        mainSplit.setBorder(null);

        BorderPane wrapper = new BorderPane();
        wrapper.setCenter(mainSplit);
        StackPane bgWrapper = new StackPane();
        bgWrapper.getChildren().addAll(createTiledBackground(), wrapper);

        Button backBtn = createStyledButton("<- Back to menu");
        backBtn.setOnAction(e -> {
            dropPane = buildDropPane();
            cardPane.getChildren().set(0, dropPane);
            showPane(CARD_DROP);
        });

        Button guideBtn = createStyledButton("Guide");
        guideBtn.setOnAction(e -> showPane(CARD_GUIDE));

        Button themeToggle = createStyledButton(darkMode ? "Light Mode" : "Dark Mode");
        themeToggle.setOnAction(e -> toggleTheme());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = buildTopBar(backBtn, guideBtn, spacer, themeToggle);
        wrapper.setTop(topBar);

        return bgWrapper;
    }

    private Pane buildGuidePane() {
        BorderPane layout = new BorderPane();
        layout.setBackground(fill(AppTheme.BG_MAIN));

        Button backBtn = createStyledButton("<- Back");
        backBtn.setOnAction(e -> showPane(CARD_MAIN));
        layout.setTop(buildTopBar(backBtn));

        Text textNode = new Text(loadGuideText());
        textNode.setFont(Font.font("Segoe UI", 15));
        textNode.setFill(AppTheme.FG_TEXT);

        TextFlow textFlow = new TextFlow(textNode);
        textFlow.setLineSpacing(6);
        textFlow.setPadding(new Insets(20));
        textFlow.setBackground(fill(AppTheme.BG_MAIN));

        ScrollPane scroll = new ScrollPane(textFlow);
        scroll.setFitToWidth(true);
        scroll.setBorder(null);
        scroll.setBackground(fill(AppTheme.BG_MAIN));
        scroll.setStyle(
                "-fx-background: " + toRgbString(AppTheme.BG_MAIN) + ";"
                        + "-fx-background-color: " + toRgbString(AppTheme.BG_MAIN) + ";");
        BorderPane.setMargin(scroll, new Insets(10));
        layout.setCenter(scroll);

        return new StackPane(layout);
    }

    private HBox buildTopBar(Node... items) {
        HBox bar = new HBox();
        bar.setPadding(new Insets(5));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setBackground(fill(AppTheme.BG_PANEL));
        bar.getChildren().addAll(items);
        return bar;
    }

    private String loadGuideText() {
        try (InputStream is = getClass().getResourceAsStream("/guide.txt")) {
            if (is == null) {
                return "guide.txt was not found in the application resources.\n" +
                        "Add a guide.txt file to your resources folder to populate this screen.";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Could not load the guide: " + e.getMessage();
        }
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(10, 6, 10, 10));
        panel.setBackground(fill(AppTheme.BG_MAIN));

        panel.getChildren().add(buildSearchRow());
        panel.getChildren().add(buildControlsRow());
        panel.getChildren().add(buildControlsRow2());
        panel.getChildren().add(buildCompletionPanel());
        panel.getChildren().add(buildBulkActionRow());

        entryTable = createEntryTable();
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        panel.getChildren().add(entryTable);

        resultCountLabel = new Label(" ");
        resultCountLabel.setTextFill(AppTheme.FG_MUTED);
        panel.getChildren().add(resultCountLabel);

        wireFilterListeners();
        return panel;
    }

    private HBox buildSearchRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Search:");
        searchLabel.setTextFill(AppTheme.FG_TEXT);

        searchField = new TextField();
        searchField.setPromptText("Search entries...");
        styleTextField(searchField);
        Node searchFieldWithButtons = createTextFieldWithButtons(searchField);

        row.getChildren().addAll(searchLabel, searchFieldWithButtons);
        HBox.setHgrow(searchFieldWithButtons, Priority.ALWAYS);
        return row;
    }

    private HBox buildControlsRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().add(labeled("Sort:"));

        sortCombo = new ComboBox<>();
        sortCombo.getItems().setAll(Translation.SortingType.values());
        sortCombo.setConverter(new DisplayNameConverter<>());
        sortCombo.getSelectionModel().selectFirst();
        styleComboBox(sortCombo);
        row.getChildren().add(sortCombo);

        reverseCheck = new CheckBox("Reverse");
        styleCheckBox(reverseCheck);
        row.getChildren().add(reverseCheck);

        row.getChildren().add(labeled("In:"));

        searchLocationCombo = new ComboBox<>();
        searchLocationCombo.getItems().setAll(Translation.SearchLocation.values());
        searchLocationCombo.setConverter(new DisplayNameConverter<>());
        searchLocationCombo.getSelectionModel().selectFirst();
        styleComboBox(searchLocationCombo);
        row.getChildren().add(searchLocationCombo);

        row.getChildren().add(labeled("Show:"));

        filterCombo = new ComboBox<>();
        filterCombo.getItems().setAll(Translation.FilterType.values());
        filterCombo.setConverter(new DisplayNameConverter<>());
        filterCombo.getSelectionModel().selectFirst();
        styleComboBox(filterCombo);
        row.getChildren().add(filterCombo);

        return row;
    }

    private HBox buildControlsRow2() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        placeholderOnlyCheck = new CheckBox("Only placeholder issues");
        styleCheckBox(placeholderOnlyCheck);
        row.getChildren().add(placeholderOnlyCheck);

        row.getChildren().add(labeled("1st column:"));

        ComboBox<String> firstColumnModeCombo = new ComboBox<>();
        firstColumnModeCombo.getItems().setAll("Original", "Key");
        firstColumnModeCombo.getSelectionModel().select(showKeyAsFirstColumn ? "Key" : "Original");
        styleComboBox(firstColumnModeCombo);
        firstColumnModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            showKeyAsFirstColumn = "Key".equals(newVal);
            if (firstCol != null) {
                firstCol.setText(showKeyAsFirstColumn ? "Key" : "Original");
            }
            entryTable.refresh();
        });
        row.getChildren().add(firstColumnModeCombo);
        return row;
    }

    private HBox buildCompletionPanel() {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);

        completionBar = new ProgressBar(0);
        completionBar.setMaxWidth(Double.MAX_VALUE);
        completionBar.setStyle(
                "-fx-accent: " + toRgbString(AppTheme.ACCENT) + ";"
                        + "-fx-background-color: " + toRgbString(AppTheme.BG_PANEL) + ";");

        completionLabel = new Label("0 / 0");
        completionLabel.setTextFill(AppTheme.FG_TEXT);

        row.getChildren().addAll(completionBar, completionLabel);
        HBox.setHgrow(completionBar, Priority.ALWAYS);
        return row;
    }

    private HBox buildBulkActionRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Button markAllBtn = createStyledButton("Mark all visible as finished");
        Button unmarkAllBtn = createStyledButton("Unmark all visible");
        row.getChildren().addAll(markAllBtn, unmarkAllBtn);

        markAllBtn.setOnAction(e -> {
            for (Map.Entry<String, Translation.Entry> en : currentRows) {
                en.getValue().markAsFinished(true);
            }
            refreshList();
            updateCompletion();
        });
        unmarkAllBtn.setOnAction(e -> {
            for (Map.Entry<String, Translation.Entry> en : currentRows) {
                en.getValue().markAsFinished(false);
            }
            refreshList();
            updateCompletion();
        });
        return row;
    }

    private void wireFilterListeners() {
        searchField.textProperty().addListener((o, a, b) -> refreshList());
        sortCombo.valueProperty().addListener((o, a, b) -> refreshList());
        reverseCheck.selectedProperty().addListener((o, a, b) -> refreshList());
        searchLocationCombo.valueProperty().addListener((o, a, b) -> refreshList());
        filterCombo.valueProperty().addListener((o, a, b) -> refreshList());
        placeholderOnlyCheck.selectedProperty().addListener((o, a, b) -> refreshList());
    }

    private Label labeled(String text) {
        Label label = new Label(text);
        label.setTextFill(AppTheme.FG_TEXT);
        return label;
    }

    private TableView<Map.Entry<String, Translation.Entry>> createEntryTable() {
        TableView<Map.Entry<String, Translation.Entry>> table = new TableView<>();
        table.setItems(currentRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setBackground(fill(AppTheme.BG_MAIN));
        table.setEditable(true);

        firstCol = buildFirstColumn();
        TableColumn<Map.Entry<String, Translation.Entry>, String> translatedCol = buildTranslatedColumn();
        TableColumn<Map.Entry<String, Translation.Entry>, String> warningCol = buildWarningColumn();
        TableColumn<Map.Entry<String, Translation.Entry>, Boolean> finishedCol = buildFinishedColumn();

        table.getColumns().addAll(firstCol, translatedCol, warningCol, finishedCol);
        table.setRowFactory(tv -> buildRow());

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                selectedEntry = null;
                showEmptyDetail();
                return;
            }
            selectedEntry = newVal;
            selectEntry(newVal);
        });
        return table;
    }

    private TableColumn<Map.Entry<String, Translation.Entry>, String> buildFirstColumn() {
        TableColumn<Map.Entry<String, Translation.Entry>, String> col =
                new TableColumn<>(showKeyAsFirstColumn ? "Key" : "Original");
        col.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getValue().getOriginal()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Map.Entry<String, Translation.Entry> entry = rowEntry(this, empty);
                if (entry == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                String display = showKeyAsFirstColumn
                        ? entry.getKey()
                        : entry.getValue().getOriginal();
                setText(singleLine(display));
                setTooltip(new Tooltip(entry.getKey() + "\n" + entry.getValue().getOriginal()));
                setTextFill(AppTheme.FG_TEXT);
            }
        });
        col.setPrefWidth(500);
        return col;
    }

    private TableColumn<Map.Entry<String, Translation.Entry>, String> buildTranslatedColumn() {
        TableColumn<Map.Entry<String, Translation.Entry>, String> col = new TableColumn<>("Translation");
        col.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getValue().getTranslated()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Map.Entry<String, Translation.Entry> entry = rowEntry(this, empty);
                if (entry == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                boolean untouched = entry.getValue().getOriginal().equals(entry.getValue().getTranslated());
                setText(singleLine(entry.getValue().getTranslated()));
                setFont(Font.font("System",
                        untouched ? FontPosture.ITALIC : FontPosture.REGULAR, 12));
                setTextFill(!isSelected() && untouched ? AppTheme.FG_MUTED : AppTheme.FG_TEXT);
            }
        });
        col.setPrefWidth(250);
        return col;
    }

    private TableColumn<Map.Entry<String, Translation.Entry>, String> buildWarningColumn() {
        TableColumn<Map.Entry<String, Translation.Entry>, String> col = new TableColumn<>("!");
        col.setCellValueFactory(p ->
                new SimpleStringProperty(p.getValue().getValue().hasPlaceholderMismatch() ? "!" : ""));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                setStyle("-fx-alignment: CENTER;");
                Map.Entry<String, Translation.Entry> entry = rowEntry(this, empty);
                if (entry == null || item == null || item.isEmpty()) {
                    setTooltip(null);
                    setTextFill(AppTheme.FG_TEXT);
                    return;
                }
                setTooltip(new Tooltip(
                        "Placeholder mismatch: original has "
                                + entry.getValue().originalPlaceholderCount()
                                + ", translation has "
                                + entry.getValue().translatedPlaceholderCount()));
                setTextFill(AppTheme.DANGER);
            }
        });
        col.setPrefWidth(28);
        col.setMinWidth(28);
        col.setMaxWidth(28);
        return col;
    }

    private TableColumn<Map.Entry<String, Translation.Entry>, Boolean> buildFinishedColumn() {
        TableColumn<Map.Entry<String, Translation.Entry>, Boolean> col = new TableColumn<>("Done");
        col.setCellValueFactory(p -> new SimpleBooleanProperty(p.getValue().getValue().isFinished()));
        col.setCellFactory(c -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setOnAction(e -> {
                    if (isEmpty() || getTableRow() == null || getTableRow().getItem() == null) return;
                    Map.Entry<String, Translation.Entry> entry = getTableRow().getItem();
                    entry.getValue().markAsFinished(checkBox.isSelected());

                    if (selectedEntry != null && selectedEntry.getKey().equals(entry.getKey())) {
                        updatingDetail = true;
                        detailFinishedCheck.setSelected(checkBox.isSelected());
                        updatingDetail = false;
                    }
                    updateCompletion();
                    entryTable.refresh();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setSelected(item);
                setGraphic(checkBox);
                setAlignment(Pos.CENTER);
            }
        });
        col.setPrefWidth(56);
        col.setMinWidth(56);
        col.setMaxWidth(56);
        return col;
    }

    private TableRow<Map.Entry<String, Translation.Entry>> buildRow() {
        return new TableRow<>() {
            @Override
            protected void updateItem(Map.Entry<String, Translation.Entry> item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("-fx-background-color: " + toRgbString(AppTheme.BG_MAIN) + ";");
                    return;
                }
                String bg;
                if (isSelected()) {
                    bg = toRgbString(AppTheme.ACCENT);
                } else if (item.getValue().hasPlaceholderMismatch()) {
                    bg = toRgbString(AppTheme.DANGER);
                } else {
                    bg = toRgbString(getIndex() % 2 == 0
                            ? AppTheme.TABLE_ROW_EVEN
                            : AppTheme.TABLE_ROW_ODD);
                }
                setStyle("-fx-background-color: " + bg + ";");
            }
        };
    }

    private Map.Entry<String, Translation.Entry> rowEntry(
            TableCell<Map.Entry<String, Translation.Entry>, ?> cell, boolean empty) {
        if (empty) return null;
        int idx = cell.getIndex();
        List<Map.Entry<String, Translation.Entry>> items = cell.getTableView().getItems();
        if (idx < 0 || idx >= items.size()) return null;
        return items.get(idx);
    }

    private Pane buildLogoContainer() {
        Label logoLabel = new Label();
        logoLabel.setAlignment(Pos.CENTER);
        logoLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        logoLabel.setTextFill(AppTheme.FG_TEXT);

        Image logoImage = tryLoadImage("/logo_full.png");

        Node bounceNode;
        double bounceSize;
        if (logoImage != null) {
            ImageView staticIv = new ImageView(logoImage);
            staticIv.setPreserveRatio(true);
            staticIv.setFitWidth(300);
            staticIv.setFitHeight(300);
            logoLabel.setGraphic(staticIv);

            ImageView bounceIv = new ImageView(logoImage);
            bounceIv.setPreserveRatio(true);
            bounceIv.setFitWidth(150);
            bounceIv.setFitHeight(150);
            bounceNode = bounceIv;
            bounceSize = 150;
        } else {
            logoLabel.setText("Kaantaa");
            logoLabel.setFont(Font.font("System", FontWeight.BOLD, 40));
            logoLabel.setTextFill(AppTheme.ACCENT);

            Label bounceLabel = new Label("Kaantaa");
            bounceLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
            bounceLabel.setTextFill(AppTheme.ACCENT);
            bounceNode = bounceLabel;
            bounceSize = 100;
        }

        StackPane container = new StackPane();
        container.getChildren().add(createTiledBackground());

        StackPane staticWrapper = new StackPane(logoLabel);
        staticWrapper.setPadding(new Insets(10));
        staticWrapper.setMouseTransparent(true);
        container.getChildren().add(staticWrapper);

        bounceNode.setVisible(false);
        bounceNode.setManaged(false);
        bounceNode.setMouseTransparent(true);

        Pane freePane = new Pane();
        freePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        freePane.setPickOnBounds(true);
        freePane.getChildren().add(bounceNode);
        container.getChildren().add(freePane);

        Label cornerCounter = new Label("Corner hits: 0");
        cornerCounter.setTextFill(Color.BLACK);
        cornerCounter.setFont(Font.font("System", FontWeight.BOLD, 12));
        cornerCounter.setVisible(false);
        cornerCounter.setMouseTransparent(true);
        StackPane.setAlignment(cornerCounter, Pos.BOTTOM_LEFT);
        StackPane.setMargin(cornerCounter, new Insets(4, 0, 4, 8));
        container.getChildren().add(cornerCounter);

        new BounceController(bounceNode, freePane, staticWrapper, cornerCounter, bounceSize).install();
        return container;
    }

    private VBox buildRightBottomPanel() {
        VBox stack = new VBox(8);
        stack.setPadding(new Insets(6, 6, 10, 10));
        stack.setBackground(fill(AppTheme.BG_MAIN));
        stack.setFillWidth(true);

        VBox exportPanel = buildExportPanel();
        stack.getChildren().addAll(buildDetailPanel(), buildFindReplacePanel(), exportPanel);
        VBox.setVgrow(exportPanel, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(stack);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setBorder(null);
        scroll.setBackground(fill(AppTheme.BG_MAIN));
        scroll.setStyle(
                "-fx-background: " + toRgbString(AppTheme.BG_MAIN) + ";"
                        + "-fx-background-color: " + toRgbString(AppTheme.BG_MAIN) + ";");

        VBox wrapper = new VBox(scroll);
        wrapper.setPadding(new Insets(0));
        wrapper.setBackground(fill(AppTheme.BG_MAIN));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildDetailPanel() {
        VBox panel = borderedPanel();

        detailKeyLabel = new Label("No entry selected");
        detailKeyLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        detailKeyLabel.setTextFill(AppTheme.FG_TEXT);
        panel.getChildren().add(detailKeyLabel);

        panel.getChildren().add(mutedCaption("Original:"));

        detailOriginalArea = new TextArea();
        detailOriginalArea.setEditable(false);
        detailOriginalArea.setWrapText(true);
        detailOriginalArea.setPrefRowCount(3);
        detailOriginalArea.setPrefColumnCount(20);
        styleTextArea(detailOriginalArea);
        panel.getChildren().add(createTextAreaWithButtons(detailOriginalArea, false, false));

        panel.getChildren().add(mutedCaption("Your translation:"));

        detailTranslatedArea = new TextArea();
        detailTranslatedArea.setWrapText(true);
        detailTranslatedArea.setPrefRowCount(4);
        detailTranslatedArea.setPrefColumnCount(20);
        detailTranslatedArea.setDisable(true);
        styleTextArea(detailTranslatedArea);
        panel.getChildren().add(createTextAreaWithButtons(detailTranslatedArea, true, true));

        detailPlaceholderLabel = new Label(" ");
        detailPlaceholderLabel.setFont(Font.font("System", FontPosture.ITALIC, 11));
        detailPlaceholderLabel.setTooltip(new Tooltip(
                "Placeholders like %s are codes the game fills in later - for example an item's name, "
                        + "a player's name, or a number. Your translation must contain the exact same number of %s "
                        + "codes as the original (you can move them to fit your language's word order, but never add, "
                        + "remove, or translate the letters %s itself)."));
        panel.getChildren().add(detailPlaceholderLabel);

        detailFinishedCheck = new CheckBox("Mark as finished");
        styleCheckBox(detailFinishedCheck);
        detailFinishedCheck.setDisable(true);
        panel.getChildren().add(detailFinishedCheck);

        detailTranslatedArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingDetail || selectedEntry == null) return;
            selectedEntry.getValue().setTranslated(newVal);
            updateDetailPlaceholderLabel(selectedEntry.getValue());
            entryTable.refresh();
        });

        detailFinishedCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingDetail || selectedEntry == null) return;
            selectedEntry.getValue().markAsFinished(newVal);
            Map.Entry<String, Translation.Entry> toReselect = selectedEntry;
            refreshList();
            updateCompletion();
            reselectEntryIfPresent(toReselect);
        });

        return panel;
    }

    private VBox buildFindReplacePanel() {
        VBox panel = borderedPanel();
        panel.getChildren().add(sectionTitle("Find and Replace"));

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(4));

        grid.add(labeled("Find:"), 0, 0);
        findField = new TextField();
        styleTextField(findField);
        grid.add(createTextFieldWithButtons(findField), 1, 0);

        grid.add(labeled("Replace with:"), 0, 1);
        replaceField = new TextField();
        styleTextField(replaceField);
        grid.add(createTextFieldWithButtons(replaceField), 1, 1);

        HBox optRow = new HBox(8);
        matchCaseCheck = new CheckBox("Match case");
        onlyVisibleCheck = new CheckBox("Only replace in visible entries");
        styleCheckBox(matchCaseCheck);
        styleCheckBox(onlyVisibleCheck);
        optRow.getChildren().addAll(matchCaseCheck, onlyVisibleCheck);
        grid.add(optRow, 0, 2, 2, 1);

        Button replaceBtn = createStyledButton("Replace All");
        replaceBtn.setOnAction(e -> performReplace());
        grid.add(replaceBtn, 1, 3);
        GridPane.setHalignment(replaceBtn, HPos.RIGHT);

        panel.getChildren().add(grid);
        return panel;
    }

    private VBox buildExportPanel() {
        VBox panel = borderedPanel();
        panel.setMaxHeight(Double.MAX_VALUE);
        panel.getChildren().add(sectionTitle("Export & Save"));

        HBox langRow = new HBox(6);
        langRow.setAlignment(Pos.CENTER_LEFT);
        langRow.getChildren().add(labeled("Language code:"));
        langCodeField = new TextField();
        langCodeField.setPromptText("e.g. es_es");
        styleTextField(langCodeField);
        Node langFieldWithButtons = createTextFieldWithButtons(langCodeField);
        HBox.setHgrow(langFieldWithButtons, Priority.ALWAYS);
        langRow.getChildren().add(langFieldWithButtons);
        panel.getChildren().add(langRow);

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        Button saveButton = createStyledButton("Save Progress");
        exportButton = createStyledButton("Export Translation");
        btnRow.getChildren().addAll(saveButton, exportButton);
        panel.getChildren().add(btnRow);

        exportStatusLabel = new Label(" ");
        exportStatusLabel.setFont(Font.font("System", FontPosture.ITALIC, 11));
        exportStatusLabel.setTextFill(AppTheme.FG_MUTED);
        panel.getChildren().add(exportStatusLabel);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        panel.getChildren().add(spacer);

        saveButton.setOnAction(e -> performSaveProgress());
        exportButton.setOnAction(e -> performExport());

        setupLangAutocomplete();
        return panel;
    }

    private VBox borderedPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        panel.setStyle(
                "-fx-border-color: " + toRgbString(AppTheme.BORDER) + ";"
                        + "-fx-border-width: 1; -fx-border-radius: 4;"
                        + "-fx-background-color: " + toRgbString(AppTheme.BG_PANEL) + ";");
        return panel;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setTextFill(AppTheme.FG_TEXT);
        return label;
    }

    private Label mutedCaption(String text) {
        Label label = new Label(text);
        label.setTextFill(AppTheme.FG_MUTED);
        return label;
    }

    private void setupLangAutocomplete() {
        langCodeField.textProperty().addListener((obs, oldVal, newVal) -> {
            lastLangCode = newVal.trim();
            updateExportButtonState();
            updateLangSuggestions();
        });
        langCodeField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                new Thread(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    Platform.runLater(() -> {
                        if (!langCodeField.isFocused() && !langSuggestPopup.isShowing()) {
                            langSuggestPopup.hide();
                        }
                    });
                }).start();
            }
        });
    }

    private void updateLangSuggestions() {
        String query = langCodeField.getText();
        if (query == null || query.isBlank()) {
            langSuggestPopup.hide();
            return;
        }
        List<Map.Entry<String, String>> matches = LanguageCodes.get(query);
        if (matches.isEmpty()) {
            langSuggestPopup.hide();
            return;
        }

        ObservableList<MenuItem> items = FXCollections.observableArrayList();
        for (Map.Entry<String, String> m : matches.stream().limit(8).toList()) {
            MenuItem item = new MenuItem(m.getKey() + "  |  " + m.getValue());
            item.setStyle("-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";");
            item.setOnAction(e -> {
                langCodeField.setText(m.getValue());
                lastLangCode = m.getValue();
                langSuggestPopup.hide();
                updateExportButtonState();
            });
            items.add(item);
        }
        langSuggestPopup.getItems().setAll(items);
        langSuggestPopup.setStyle(
                "-fx-background-color: " + toRgbString(AppTheme.BG_PANEL) + ";"
                        + "-fx-border-color: " + toRgbString(AppTheme.BORDER) + ";");
        if (!langSuggestPopup.isShowing()) {
            langSuggestPopup.show(langCodeField, Side.BOTTOM, 0, 0);
        }
    }

    private Button createStyledButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(buttonStyle(false));
        btn.setOnMouseEntered(e -> btn.setStyle(buttonStyle(true)));
        btn.setOnMouseExited(e -> btn.setStyle(buttonStyle(false)));
        return btn;
    }

    private String buttonStyle(boolean hover) {
        Color bg = hover ? AppTheme.ACCENT_HOVER : AppTheme.BG_PANEL;
        return "-fx-background-color: " + toRgbString(bg) + ";"
                + "-fx-border-color: " + toRgbString(AppTheme.ACCENT) + ";"
                + "-fx-border-radius: 10; -fx-background-radius: 10;"
                + "-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";";
    }

    private Button createIconButton(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setFont(Font.font("System", FontWeight.BOLD, 12));
        btn.setStyle(
                "-fx-background-color: transparent; -fx-border-color: transparent;"
                        + "-fx-padding: 2;"
                        + "-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";");
        btn.setMinSize(24, 24);
        btn.setMaxSize(24, 24);
        btn.setFocusTraversable(false);
        return btn;
    }

    private Node createTextFieldWithButtons(TextField tf) {
        HBox box = new HBox(2);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(tf);
        HBox.setHgrow(tf, Priority.ALWAYS);

        Button copyBtn = createIconButton("📋", "Copy");
        copyBtn.setOnAction(e -> copyTextToClipboard(tf.getSelectedText().isEmpty()
                ? tf.getText() : tf.getSelectedText()));
        box.getChildren().add(copyBtn);

        Button pasteBtn = createIconButton("📥", "Paste");
        pasteBtn.setOnAction(e -> tf.paste());
        box.getChildren().add(pasteBtn);

        Button clearBtn = createIconButton("X", "Clear");
        clearBtn.setOnAction(e -> tf.clear());
        box.getChildren().add(clearBtn);

        return box;
    }

    private Node createTextAreaWithButtons(TextArea ta, boolean showPaste, boolean showClear) {
        VBox box = new VBox(2);
        HBox buttonRow = new HBox(2);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = createIconButton("📋", "Copy");
        copyBtn.setOnAction(e -> copyTextToClipboard(ta.getSelectedText().isEmpty()
                ? ta.getText() : ta.getSelectedText()));
        buttonRow.getChildren().add(copyBtn);

        if (showPaste) {
            Button pasteBtn = createIconButton("📥", "Paste");
            pasteBtn.setOnAction(e -> ta.paste());
            buttonRow.getChildren().add(pasteBtn);
        }
        if (showClear) {
            Button clearBtn = createIconButton("X", "Clear");
            clearBtn.setOnAction(e -> ta.clear());
            buttonRow.getChildren().add(clearBtn);
        }

        box.getChildren().addAll(ta, buttonRow);
        VBox.setVgrow(ta, Priority.ALWAYS);
        return box;
    }

    private void copyTextToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void styleTextField(TextField tf) {
        tf.setStyle(
                "-fx-control-inner-background: " + toRgbString(AppTheme.BG_TEXTFIELD) + ";"
                        + "-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";"
                        + "-fx-prompt-text-fill: " + toRgbString(AppTheme.FG_MUTED) + ";");
    }

    private void styleTextArea(TextArea ta) {
        ta.setStyle(
                "-fx-control-inner-background: " + toRgbString(AppTheme.BG_TEXTFIELD) + ";"
                        + "-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";"
                        + "-fx-prompt-text-fill: " + toRgbString(AppTheme.FG_MUTED) + ";"
                        + "-fx-highlight-fill: " + toRgbString(AppTheme.ACCENT) + ";");
    }

    private <T> void styleComboBox(ComboBox<T> cb) {
        cb.setStyle(
                "-fx-background-color: " + toRgbString(AppTheme.BG_TEXTFIELD) + ";"
                        + "-fx-text-fill: " + toRgbString(AppTheme.FG_TEXT) + ";");
        cb.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setBackground(fill(AppTheme.BG_PANEL));
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(displayNameOf(item));
                setTextFill(AppTheme.FG_TEXT);
            }
        });
        cb.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(displayNameOf(item));
                setTextFill(AppTheme.FG_TEXT);
            }
        });
    }

    private <T> String displayNameOf(T item) {
        return item instanceof Translation.DisplayableEnum de ? de.displayName() : item.toString();
    }

    private void styleCheckBox(CheckBox cb) {
        cb.setTextFill(AppTheme.FG_TEXT);
    }

    private void setDropHandlers(Node target, Function<List<File>, Boolean> onFiles) {
        target.setOnDragOver(event -> {
            if (event.getGestureSource() != target && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        target.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = Boolean.TRUE.equals(onFiles.apply(db.getFiles()));
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private boolean handleUpdateDrop(List<File> files, Label statusLabel) {
        if (files.size() == 1) {
            File file = files.getFirst();
            boolean isProgress = isProgressFileName(file);
            if (pendingUpdateProgressFile == null) {
                if (!isProgress) {
                    statusLabel.setText("Please drop a Kaantaa progress file first.");
                    return false;
                }
                pendingUpdateProgressFile = file;
                statusLabel.setText("Old progress file loaded. Now drop the new language file.");
                return true;
            }
            if (isProgress) {
                statusLabel.setText("Second file must be a language file (not another progress file).");
                return false;
            }
            handleUpdateFiles(pendingUpdateProgressFile, file);
            pendingUpdateProgressFile = null;
            statusLabel.setText("Update complete.");
            return true;
        }

        if (files.size() == 2) {
            File progressFile = null;
            File langFile = null;
            for (File f : files) {
                if (isProgressFileName(f)) progressFile = f;
                else langFile = f;
            }
            if (progressFile == null || langFile == null) {
                statusLabel.setText("Drop one progress file and one language file together.");
                return false;
            }
            handleUpdateFiles(progressFile, langFile);
            pendingUpdateProgressFile = null;
            statusLabel.setText("Update complete.");
            return true;
        }

        statusLabel.setText("Drop either one progress file, or both files at once.");
        return false;
    }

    private boolean isProgressFileName(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith("_kaantaa.json");
    }

    private void handleDroppedFile(File f) {
        if (f == null || !f.isFile()) {
            showError("Please drop a single file.");
            return;
        }
        String name = f.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".json")) {
            showError("Please drop a .json file. Either a mod language file or a saved Kaantaa progress file.");
            return;
        }
        try {
            Translation loaded;
            if (name.endsWith("_kaantaa.json")) {
                loaded = Translation.load(f.getAbsolutePath());
                if (loaded == null) {
                    showError("This progress file could not be read. It may be corrupted, or not a Kaantaa file.");
                    return;
                }
            } else {
                loaded = Translation.create(f.getAbsolutePath());
                if (loaded.size() == 0) {
                    showError("No translatable text was found in this file.\n" +
                            "Make sure it is a flat Minecraft language JSON file " +
                            "(key/value pairs of strings).");
                    return;
                }
            }
            this.translation = loaded;
            this.loadedDirectory = f.getParentFile();
            this.loadedFile = f;
            showMainUi();
        } catch (IOException ex) {
            showError("Could not read the file: " + ex.getMessage());
        }
    }

    private void handleUpdateFiles(File oldProgress, File newLang) {
        try {
            Translation oldTrans = Translation.load(oldProgress.getAbsolutePath());
            if (oldTrans == null) {
                showError("Could not load the old progress file.");
                return;
            }
            Translation newTrans = Translation.create(newLang.getAbsolutePath());
            if (newTrans.size() == 0) {
                showError("The new language file has no translatable entries.");
                return;
            }

            Translation.MergeStats stats = oldTrans.computeMergeStats(newTrans);

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Update");
            confirm.setHeaderText(null);
            confirm.setContentText(String.format("""
                            This will merge the new language file into your progress.
                            
                            Entries to be added: %d
                            Entries to be removed: %d
                            Entries to be marked as unfinished (changed original): %d
                            
                            Continue?""",
                    stats.added(), stats.removed(), stats.changed()));
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;

            this.translation = oldTrans.mergeFrom(newTrans);
            this.loadedDirectory = oldProgress.getParentFile();
            this.loadedFile = oldProgress;
            showMainUi();
        } catch (IOException ex) {
            showError("Could not process files: " + ex.getMessage());
        }
    }

    private void showMainUi() {
        refreshList();
        updateCompletion();
        showEmptyDetail();
        if (loadedFile != null) {
            ((Stage) cardPane.getScene().getWindow()).setTitle("Kaantaa | " + loadedFile.getName());
        }
        showPane(CARD_MAIN);
    }

    private void refreshList() {
        if (translation == null) return;

        List<Map.Entry<String, Translation.Entry>> rows = translation.getMatching(
                sortCombo.getValue(),
                reverseCheck.isSelected(),
                searchLocationCombo.getValue(),
                searchField.getText(),
                filterCombo.getValue());

        if (placeholderOnlyCheck.isSelected()) {
            rows = rows.stream()
                    .filter(e -> e.getValue().hasPlaceholderMismatch())
                    .collect(Collectors.toList());
        }
        currentRows.setAll(rows);
        entryTable.refresh();
        resultCountLabel.setText("Showing " + rows.size() + " of " + translation.size() + " entries");
    }

    private void updateCompletion() {
        if (translation == null) {
            completionBar.setProgress(0);
            completionLabel.setText("0 / 0");
            updateExportButtonState();
            return;
        }
        long finished = translation.allEntries().stream()
                .filter(Translation.Entry::isFinished).count();
        int total = translation.size();
        double pct = total == 0 ? 0 : (double) finished / total;
        completionBar.setProgress(pct);
        completionLabel.setText(finished + " / " + total);
        updateExportButtonState();
    }

    private void updateExportButtonState() {
        if (translation == null) {
            exportButton.setDisable(true);
            exportButton.setTooltip(new Tooltip("Load a file first"));
            return;
        }

        long finishedCount = translation.allEntries().stream()
                .filter(Translation.Entry::isFinished).count();
        int total = translation.size();
        boolean allFinished = total > 0 && finishedCount == total;
        boolean noMismatches = translation.allEntries().stream()
                .noneMatch(Translation.Entry::hasPlaceholderMismatch);
        String code = langCodeField.getText().trim();
        boolean validCode = LanguageCodes.isValidCode(code);

        exportButton.setDisable(false);

        StringBuilder reasons = new StringBuilder();
        if (!allFinished) {
            reasons.append("• Not all entries are marked finished (")
                    .append(finishedCount).append("/").append(total).append(")\n");
        }
        if (!noMismatches) {
            reasons.append("• Some entries have mismatched placeholders\n");
        }
        if (!validCode) {
            reasons.append("• Enter a valid language code (pick one of the suggestions)\n");
        }

        exportButton.setTooltip(new Tooltip(reasons.isEmpty()
                ? "Export the finished translation as " + code + ".json"
                : reasons.toString()));
    }

    private void selectEntry(Map.Entry<String, Translation.Entry> entry) {
        selectedEntry = entry;
        updatingDetail = true;
        Translation.Entry val = entry.getValue();
        detailKeyLabel.setText(entry.getKey());
        detailOriginalArea.setText(val.getOriginal());
        detailOriginalArea.positionCaret(0);
        detailTranslatedArea.setText(val.getTranslated());
        detailTranslatedArea.positionCaret(0);
        detailTranslatedArea.setDisable(false);
        detailFinishedCheck.setSelected(val.isFinished());
        detailFinishedCheck.setDisable(false);
        updateDetailPlaceholderLabel(val);
        updatingDetail = false;
    }

    private void showEmptyDetail() {
        selectedEntry = null;
        updatingDetail = true;
        detailKeyLabel.setText("No entry selected");
        detailOriginalArea.clear();
        detailTranslatedArea.clear();
        detailTranslatedArea.setDisable(true);
        detailFinishedCheck.setSelected(false);
        detailFinishedCheck.setDisable(true);
        detailPlaceholderLabel.setText(" ");
        updatingDetail = false;
    }

    private void updateDetailPlaceholderLabel(Translation.Entry val) {
        int origCount = val.originalPlaceholderCount();
        int transCount = val.translatedPlaceholderCount();

        if (origCount == 0 && transCount == 0) {
            detailPlaceholderLabel.setTextFill(AppTheme.FG_MUTED);
            detailPlaceholderLabel.setText("This text has no placeholders.");
        } else if (origCount == transCount) {
            detailPlaceholderLabel.setTextFill(AppTheme.SUCCESS);
            detailPlaceholderLabel.setText("✓ Placeholders match (" + transCount + " of " + origCount + ")");
        } else {
            detailPlaceholderLabel.setTextFill(AppTheme.DANGER);
            detailPlaceholderLabel.setText(
                    "! Placeholder mismatch: original has " + origCount + ", yours has " + transCount);
        }
    }

    private void reselectEntryIfPresent(Map.Entry<String, Translation.Entry> entry) {
        int idx = currentRows.indexOf(entry);
        if (idx >= 0) {
            entryTable.getSelectionModel().select(idx);
            entryTable.scrollTo(idx);
        } else {
            showEmptyDetail();
        }
    }

    private void performReplace() {
        if (translation == null) return;
        String target = findField.getText();
        String replacement = replaceField.getText();
        boolean matchCase = matchCaseCheck.isSelected();

        if (target.isEmpty()) {
            showError("Enter some text to find first.");
            return;
        }
        if (onlyVisibleCheck.isSelected()) {
            for (Map.Entry<String, Translation.Entry> e : currentRows) {
                e.getValue().replaceInTranslated(target, replacement, matchCase);
            }
        } else {
            translation.replaceInAll(target, replacement, matchCase);
        }

        Map.Entry<String, Translation.Entry> toReselect = selectedEntry;
        refreshList();
        updateCompletion();
        if (toReselect != null) {
            reselectEntryIfPresent(toReselect);
        }
    }

    private void performSaveProgress() {
        if (translation == null || loadedDirectory == null) return;

        String defaultName = langCodeField.getText().trim();
        if (defaultName.isEmpty()) defaultName = "untitled";

        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle("Save Progress");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter a name for the progress file (without extension):");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        String name = result.get().trim();
        if (name.isEmpty()) {
            showError("Name cannot be empty.");
            return;
        }
        try {
            translation.save(loadedDirectory.getAbsolutePath(), name);
            exportStatusLabel.setText("Progress saved to " + name
                    + "_kaantaa.json at " + LocalTime.now().withNano(0));
        } catch (IOException ex) {
            showError("Could not save progress: " + ex.getMessage());
        }
    }

    private void performExport() {
        if (translation == null || loadedDirectory == null) return;
        updateExportButtonState();

        long finishedCount = translation.allEntries().stream()
                .filter(Translation.Entry::isFinished).count();
        int total = translation.size();
        boolean allFinished = total > 0 && finishedCount == total;
        boolean noMismatches = translation.allEntries().stream()
                .noneMatch(Translation.Entry::hasPlaceholderMismatch);
        String code = langCodeField.getText().trim();
        boolean validCode = LanguageCodes.isValidCode(code);

        if (!allFinished || !noMismatches || !validCode) {
            showError(exportButton.getTooltip().getText());
            return;
        }
        try {
            translation.output(loadedDirectory.getAbsolutePath(), code);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export complete");
            alert.setHeaderText(null);
            alert.setContentText("Exported " + code + ".json to:\n" + loadedDirectory.getAbsolutePath());
            alert.showAndWait();
            exportStatusLabel.setText("Exported " + code + ".json at "
                    + LocalTime.now().withNano(0));
        } catch (IOException ex) {
            showError("Could not save the file: " + ex.getMessage());
        }
    }

    private synchronized void autoSaveQuietly() {
        if (autoSaved || translation == null || loadedDirectory == null) return;
        autoSaved = true;
        try {
            String code = (lastLangCode == null || lastLangCode.isBlank()) ? "autosave" : lastLangCode;
            translation.save(loadedDirectory.getAbsolutePath(), code);
        } catch (IOException ignored) {
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Kaantaa");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private Image loadAppIcon() {
        Image icon = tryLoadImage("/app_icon.png");
        if (icon != null) return icon;

        Canvas canvas = new Canvas(64, 64);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(AppTheme.ACCENT);
        gc.fillOval(0, 0, 64, 64);
        return canvas.snapshot(null, null);
    }

    private Image tryLoadImage(String resourcePath) {
        try {
            return new Image(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)));
        } catch (Exception e) {
            return null;
        }
    }

    private String themeToggleLabel() {
        return darkMode ? "Switch to Light Mode" : "Switch to Dark Mode";
    }

    private String toRgbString(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private String singleLine(String s) {
        if (s == null) return "";
        return s.replace("\n", " ⏎ ").replace("\t", " ");
    }

    private static final class BounceController {
        private final Node bounceNode;
        private final Pane freePane;
        private final StackPane staticWrapper;
        private final Label cornerCounter;
        private final double size;

        private final double[] velocity = {0.6, 0.4525};
        private final int[] cornerHits = {0};
        private final boolean[] bouncing = {false};
        private final AnimationTimer[] timerHolder = new AnimationTimer[1];

        BounceController(Node bounceNode, Pane freePane, StackPane staticWrapper,
                         Label cornerCounter, double size) {
            this.bounceNode = bounceNode;
            this.freePane = freePane;
            this.staticWrapper = staticWrapper;
            this.cornerCounter = cornerCounter;
            this.size = size;
        }

        void install() {
            timerHolder[0] = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    tick();
                }
            };
            freePane.setOnMouseClicked(e -> {
                if (bouncing[0]) stop();
                else start();
            });
            freePane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null && bouncing[0]) stop();
            });
        }

        private void tick() {
            double w = freePane.getWidth();
            double h = freePane.getHeight();
            if (w <= size || h <= size) return;

            double x = bounceNode.getLayoutX() + velocity[0];
            double y = bounceNode.getLayoutY() + velocity[1];
            boolean hitX = false;
            boolean hitY = false;

            if (x <= 0) {
                x = 0;
                velocity[0] = Math.abs(velocity[0]);
                hitX = true;
            } else if (x >= w - size) {
                x = w - size;
                velocity[0] = -Math.abs(velocity[0]);
                hitX = true;
            }
            if (y <= 0) {
                y = 0;
                velocity[1] = Math.abs(velocity[1]);
                hitY = true;
            } else if (y >= h - size) {
                y = h - size;
                velocity[1] = -Math.abs(velocity[1]);
                hitY = true;
            }

            bounceNode.setLayoutX(x);
            bounceNode.setLayoutY(y);

            if (hitX && hitY) {
                cornerHits[0]++;
                cornerCounter.setText("Corner hits: " + cornerHits[0]);
            }
        }

        private void start() {
            double w = freePane.getWidth();
            double h = freePane.getHeight();
            bounceNode.setLayoutX(Math.max(0, (w - size) / 2));
            bounceNode.setLayoutY(Math.max(0, (h - size) / 2));
            cornerHits[0] = 0;
            cornerCounter.setText("Corner hits: 0");
            cornerCounter.setVisible(true);
            staticWrapper.setVisible(false);
            bounceNode.setVisible(true);
            timerHolder[0].start();
            bouncing[0] = true;
        }

        private void stop() {
            timerHolder[0].stop();
            bounceNode.setVisible(false);
            staticWrapper.setVisible(true);
            cornerCounter.setVisible(false);
            bouncing[0] = false;
        }
    }

    private static class AppTheme {
        static Color BG_MAIN;
        static Color BG_PANEL;
        static Color BG_TEXTFIELD;
        static Color FG_TEXT;
        static Color FG_MUTED;
        static Color ACCENT;
        static Color ACCENT_HOVER;
        static Color DANGER;
        static Color SUCCESS;
        static Color TABLE_ROW_EVEN;
        static Color TABLE_ROW_ODD;
        static Color BORDER;

        static void applyTheme(boolean dark) {
            if (dark) {
                BG_MAIN = Color.rgb(13, 17, 23);
                BG_PANEL = Color.rgb(22, 27, 34);
                BG_TEXTFIELD = Color.rgb(33, 38, 45);
                FG_TEXT = Color.rgb(240, 246, 252);
                FG_MUTED = Color.rgb(139, 148, 158);
                ACCENT = Color.rgb(88, 166, 255);
                ACCENT_HOVER = Color.rgb(121, 192, 255);
                DANGER = Color.rgb(248, 81, 73);
                SUCCESS = Color.rgb(63, 185, 80);
                TABLE_ROW_EVEN = Color.rgb(22, 27, 34);
                TABLE_ROW_ODD = Color.rgb(13, 17, 23);
                BORDER = Color.rgb(48, 54, 61);
            } else {
                BG_MAIN = Color.rgb(246, 248, 250);
                BG_PANEL = Color.rgb(255, 255, 255);
                BG_TEXTFIELD = Color.rgb(255, 255, 255);
                FG_TEXT = Color.rgb(31, 35, 40);
                FG_MUTED = Color.rgb(100, 108, 118);
                ACCENT = Color.rgb(9, 105, 218);
                ACCENT_HOVER = Color.rgb(31, 111, 235);
                DANGER = Color.rgb(207, 34, 46);
                SUCCESS = Color.rgb(28, 126, 62);
                TABLE_ROW_EVEN = Color.rgb(255, 255, 255);
                TABLE_ROW_ODD = Color.rgb(246, 248, 250);
                BORDER = Color.rgb(208, 215, 222);
            }
        }
    }

    private static class TiledBackgroundRegion extends Region {
        private final TiledBackgroundCanvas canvas;

        TiledBackgroundRegion() {
            canvas = new TiledBackgroundCanvas();
            getChildren().add(canvas);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            setMouseTransparent(true);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            canvas.setWidth(w);
            canvas.setHeight(h);
            canvas.relocate(0, 0);
        }
    }

    private static class TiledBackgroundCanvas extends Canvas {
        private final Image tileImage;
        private double offsetX = 0;
        private double offsetY = 0;
        private Timeline timeline;

        TiledBackgroundCanvas() {
            Image img = null;
            try {
                img = new Image(Objects.requireNonNull(
                        getClass().getResourceAsStream("/background.png")));
            } catch (Exception ignored) {
            }
            this.tileImage = img;

            widthProperty().addListener((obs, o, n) -> draw());
            heightProperty().addListener((obs, o, n) -> draw());

            sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) startTimeline();
                else stopTimeline();
            });
        }

        private void startTimeline() {
            if (timeline == null) {
                timeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
                    offsetX += 0.5;
                    offsetY += 0.5;
                    if (tileImage != null) {
                        if (offsetX > tileImage.getWidth()) offsetX -= tileImage.getWidth();
                        if (offsetY > tileImage.getHeight()) offsetY -= tileImage.getHeight();
                    }
                    draw();
                }));
                timeline.setCycleCount(Animation.INDEFINITE);
            }
            timeline.play();
        }

        private void stopTimeline() {
            if (timeline != null) timeline.stop();
        }

        @Override
        public void resize(double width, double height) {
            super.resize(width, height);
            draw();
        }

        private void draw() {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0) return;

            GraphicsContext gc = getGraphicsContext2D();
            gc.setFill(AppTheme.BG_MAIN);
            gc.fillRect(0, 0, w, h);
            if (tileImage == null) return;

            double tileW = tileImage.getWidth();
            double tileH = tileImage.getHeight();
            double startX = -tileW + offsetX;
            double startY = -tileH + offsetY;

            for (double y = startY; y < h; y += tileH) {
                for (double x = startX; x < w; x += tileW) {
                    gc.drawImage(tileImage, x, y);
                }
            }
        }
    }

    private static class DisplayNameConverter<T extends Translation.DisplayableEnum>
            extends StringConverter<T> {
        @Override
        public String toString(T obj) {
            return obj == null ? "" : obj.displayName();
        }

        @Override
        public T fromString(String string) {
            return null;
        }
    }
}