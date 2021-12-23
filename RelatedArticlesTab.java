package org.jabref.gui.entryeditor;

import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.font.KumoFont;
import com.kennycason.kumo.font.scale.LinearFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.palette.ColorPalette;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BubbleChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;
import javafx.util.StringConverter;

import org.jabref.Globals;
import org.jabref.gui.DialogService;
import org.jabref.gui.desktop.JabRefDesktop;
import org.jabref.gui.util.BackgroundTask;
import org.jabref.logic.importer.fetcher.MrDLibFetcher;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.layout.format.Number;
import org.jabref.logic.net.URLDownload;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;
import org.jabref.preferences.JabRefPreferences;

import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Int;

/**
 * GUI for tab displaying article recommendations based on the currently selected BibEntry
 */
public class RelatedArticlesTab extends EntryEditorTab {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelatedArticlesTab.class);
    private final EntryEditorPreferences preferences;
    private final DialogService dialogService;
    private BooleanProperty contextInfoProperty = new SimpleBooleanProperty();
    private BooleanProperty resultsLoadingProperty = new SimpleBooleanProperty();
    private HashMap<String,String> papers;
    private String clickedAuthorName;
    ProgressIndicator progress;

    public RelatedArticlesTab(EntryEditorPreferences preferences, DialogService dialogService) {
        setText(Localization.lang("Related articles"));
        setTooltip(new Tooltip(Localization.lang("Related articles")));
        this.preferences = preferences;
        this.dialogService = dialogService;
    }

    /**
     * Gets a StackPane of related article information to be displayed in the Related Articles tab
     * @param entry The currently selected BibEntry on the JabRef UI.
     * @return A StackPane with related article information to be displayed in the Related Articles tab.
     */
    private StackPane getRelatedArticlesPane(BibEntry entry) {
        StackPane root = new StackPane();
        root.getStyleClass().add("related-articles-tab");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(100, 100);

        MrDLibFetcher fetcher = new MrDLibFetcher(Globals.prefs.get(JabRefPreferences.LANGUAGE),
                                                  Globals.BUILD_INFO.getVersion());
        BackgroundTask
                      .wrap(() -> fetcher.performSearch(entry))
                      .onRunning(() -> progress.setVisible(true))
                      .onSuccess(relatedArticles -> {
                          progress.setVisible(false);
                          root.getChildren().add(getRelatedArticleInfoEx(relatedArticles, entry));
                      })
                      .executeWith(Globals.TASK_EXECUTOR);

        root.getChildren().add(progress);

        return root;
    }

    /**
     * Creates a VBox of the related article information to be used in the StackPane displayed in the Related Articles tab
     * @param list List of BibEntries of related articles
     * @return VBox of related article descriptions to be displayed in the Related Articles tab
     */
    private VBox getRelatedArticleInfo(List<BibEntry> list) {
        VBox vBox = new VBox();
        vBox.setSpacing(20.0);

        for (BibEntry entry : list) {
            HBox hBox = new HBox();
            hBox.setSpacing(5.0);

            String title = entry.getTitle().orElse("");
            String journal = entry.getField(FieldName.JOURNAL).orElse("");
            String authors = entry.getField(FieldName.AUTHOR).orElse("");
            String year = entry.getField(FieldName.YEAR).orElse("");

            Hyperlink titleLink = new Hyperlink(title);
            Text journalText = new Text(journal);
            journalText.setFont(Font.font(Font.getDefault().getFamily(), FontPosture.ITALIC, Font.getDefault().getSize()));
            Text authorsText = new Text(authors);
            Text yearText = new Text("(" + year + ")");
            titleLink.setOnAction(event -> {
                if (entry.getField(FieldName.URL).isPresent()) {
                    try {
                        JabRefDesktop.openBrowser(entry.getField(FieldName.URL).get());
                    } catch (IOException e) {
                        LOGGER.error("Error opening the browser to: " + entry.getField(FieldName.URL).get(), e);
                        dialogService.showErrorDialogAndWait(e);
                    }
                }
            });

            hBox.getChildren().addAll(titleLink, journalText, authorsText, yearText);
            vBox.getChildren().add(hBox);
        }
        return vBox;
    }

    /**
     * Creates a VBox of the related article information to be used in the StackPane displayed in the Related Articles tab
     * @param list List of BibEntries of related articles
     * @param paper Selected BibEntry
     * @return VBox of related article descriptions to be displayed in the Related Articles tab
     */
    private VBox getRelatedArticleInfoEx(List<BibEntry> list, BibEntry paper) {
        List<String> text = new ArrayList<>();

        boolean abstractPresent = false;
        String abstractText = paper.getField(FieldName.ABSTRACT).orElse("");
        if(!abstractText.isEmpty())
        {
            text.add(abstractText);
            abstractPresent = true;
        }
        else
            text.add(paper.getTitle().orElse(""));

        VBox vBox = new VBox();
        vBox.setSpacing(20.0);

        GridPane tableGrid = getRelatedArticleGridPane(20, 10,70);
        GridPane contentGrid = getRelatedArticleGridPane(0,40,60);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setContent(contentGrid);

        TableView<RelatedArticleRecommendation> table = getRelatedArticleTable(list.size(), paper.getTitle().orElse(""));
        ObservableList<RelatedArticleRecommendation> data = FXCollections.observableArrayList();

        ObservableList<XYChart.Series<Number, Number>> plotData = FXCollections.observableArrayList();
        XYChart.Series<Number, Number> series = new XYChart.Series<Number, Number>();

        ScrollPane content = getContextInformationPane();

        HashMap<String,Integer> authorsMap = new HashMap();
        HashMap<Integer,Integer> frequencyMap = new HashMap();
        populateAuthorAndFrequencyMaps(list,authorsMap,frequencyMap);

        int rank = 0;
        for (BibEntry entry : list) {

            String title = entry.getTitle().orElse("");
            String authors = entry.getField(FieldName.AUTHOR).orElse("");
            String year = entry.getField(FieldName.YEAR).orElse("");
            rank++;

            Hyperlink titleLink = new Hyperlink(title);
            Text authorsText = new Text(authors);
            Text yearText = new Text("(" + year + ")");
            titleLink.setOnAction(event -> {
                if (entry.getField(FieldName.URL).isPresent()) {
                    try {
                        JabRefDesktop.openBrowser(entry.getField(FieldName.URL).get());
                    } catch (IOException e) {
                        LOGGER.error("Error opening the browser to: " + entry.getField(FieldName.URL).get(), e);
                        dialogService.showErrorDialogAndWait(e);
                    }
                }
            });
            if(!abstractPresent)
                text.add(title);

            data.add(new RelatedArticleRecommendation(rank, rank, titleLink, authorsText,yearText));
            series.getData().add(new XYChart.Data(rank,authorsMap.get(authors),frequencyMap.get(authorsMap.get(authors))*0.2));
        }

        table.setItems(data);
        tableGrid.add(getWorldCloud(text),0,0);
        tableGrid.add(getUpDownButtons(table),1,0);
        tableGrid.add(table,2,0);

        plotData.add(series);
        BubbleChart bubbleChart = getBubbleChart(list.size(),authorsMap.size(),authorsMap, paper.getTitle().orElse(""));
        bubbleChart.setData(plotData);
        contentGrid.add(content,1,0);
        ScrollPane scrollScatterChart = new ScrollPane();
        scrollScatterChart.setFitToWidth(true);
        scrollScatterChart.setFitToHeight(true);
        scrollScatterChart.setContent(bubbleChart);
        contentGrid.add(scrollScatterChart,2,0);

        vBox.getChildren().addAll(tableGrid, scroll);
        return vBox;
    }

    /**
     * Creates a Label containing WordCloud
     * @param text List of strings to use to create the world cloud
     * @return Label containing the WordCloud
     */
    private static Label getWorldCloud(List<String> text)
    {
        final Label wordCloudLbl = new Label();
        try {
            WordCloud wordCloud = buildWordCloud(text);
            Image image = SwingFXUtils.toFXImage(wordCloud.getBufferedImage(), null);
            wordCloudLbl.setGraphic(new ImageView(image));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return wordCloudLbl;
    }

    /**
     * Creates a WorldCloud
     * @param text List of strings to use to create the world cloud
     * @return WordCloud formed using the given parameters
     */
    private static WordCloud buildWordCloud(List<String> text) throws IOException {
        final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
        final List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(text);
        final Dimension dimension = new Dimension(200, 200);
        final WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
        wordCloud.setPadding(1);
        wordCloud.setBackgroundColor(new Color(0xffffff));
        wordCloud.setKumoFont(new KumoFont(new java.awt.Font("Verdana", java.awt.Font.BOLD, 12)));
        /*wordCloud.setColorPalette(new ColorPalette
                (new Color(0x35235D),
                        new Color(0xDB2464),
                        new Color(0x40AAF1),
                        new Color(0xCB2402),
                        new Color(0xB8DC3C),
                        new Color(0xA31A48)));*/
        wordCloud.setColorPalette(new ColorPalette
                (new Color(0xe37222),
                        new Color(0x07889b),
                        new Color(0x66b9bf),
                        new Color(0xeeaa7b)));
        wordCloud.setFontScalar(new LinearFontScalar(5, 24));
        wordCloud.build(wordFrequencies);
        return wordCloud;
    }

    /**
     * Populates the provided authorsMap and frequencyMap with information
     * @param list List of BibEntry to get author information from
     * @param authorsMap Map to populate authors against a serial number
     * @param frequencyMap Map to populate frequency of authors against authors name
     */
    private static void populateAuthorAndFrequencyMaps(List<BibEntry> list, HashMap<String,Integer> authorsMap, HashMap<Integer, Integer> frequencyMap)
    {
        int authorNumber=0;
        for(BibEntry entry : list)
        {
            String authors = entry.getField(FieldName.AUTHOR).orElse("");
            if(!authorsMap.containsKey(authors))
            {
                authorNumber++;
                authorsMap.put(authors,authorNumber);
                frequencyMap.put(authorNumber,1);
            }
            else
            {
                frequencyMap.put(authorsMap.get(authors),frequencyMap.get(authorsMap.get(authors))+1);
            }
        }
    }

    public static class RelatedArticleRecommendation {

        private final int sno;
        private final Hyperlink title;
        private final Text authors;
        private final Text year;
        private int rno;

        private RelatedArticleRecommendation(int _rno, int _sno, Hyperlink _titleLink, Text _authorsText, Text _yearText) {
            this.sno = _sno;
            this.title = _titleLink;
            this.authors = _authorsText;
            this.year = _yearText;
            this.rno = _rno;
        }

        public Hyperlink getTitle() {
            return title;
        }

        public Text getAuthors() {
            return authors;
        }

        public Text getYear() {
            return year;
        }

        public int getSno() {
            return sno;
        }

        public int getRno() {
            return rno;
        }

        public  void setRno(int _rno)
        {
            this.rno = _rno;
        }
    }

    /**
     * Creates a TableView to hold related article recommendations
     * @param size Size of list of BibEntries of related articles
     * @return TableView of related article descriptions to be displayed in the Related Articles tab
     */
    private TableView<RelatedArticleRecommendation> getRelatedArticleTable(int size, String paper)
    {
        TableView<RelatedArticleRecommendation> table = new TableView<RelatedArticleRecommendation>();
        table.setEditable(false);
        table.setTooltip(new Tooltip(Localization.lang("You are shown the recommendations because you clicked \n\"" + paper + "\"")));

        TableColumn serialColumn = new TableColumn(Localization.lang("S.no."));
        TableColumn titleColumn = new TableColumn(Localization.lang("Title"));
        TableColumn authorsColumn = new TableColumn(Localization.lang("Authors"));
        TableColumn yearColumn = new TableColumn(Localization.lang("Year"));

        serialColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        titleColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.6));
        authorsColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.2));
        yearColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.1));

        serialColumn.setCellValueFactory( new PropertyValueFactory<RelatedArticleRecommendation,Hyperlink>("sno"));
        titleColumn.setCellValueFactory( new PropertyValueFactory<RelatedArticleRecommendation,Hyperlink>("title"));
        authorsColumn.setCellValueFactory( new PropertyValueFactory<RelatedArticleRecommendation, Text>("authors"));
        yearColumn.setCellValueFactory( new PropertyValueFactory<RelatedArticleRecommendation,Text>("year"));

        table.getColumns().addAll(serialColumn, titleColumn, authorsColumn, yearColumn);

        authorsColumn.setCellFactory(tc -> {
            TableCell<RelatedArticleRecommendation, Text> cell = new TableCell<RelatedArticleRecommendation, Text>() {
                @Override
                protected void updateItem(Text item, boolean empty) {
                    super.updateItem(item, empty) ;
                    setText(empty ? null : item.getText());
                }
            };
            cell.setOnMouseClicked(e -> {
                if (! cell.isEmpty()) {
                    String userId = cell.getItem().getText();
                    if(!userId.isEmpty())
                    {
                        resultsLoadingProperty.set(!resultsLoadingProperty.get());
                        BackgroundTask
                                .wrap(() -> SearchPapersByAuthor(userId))
                                .onSuccess(tempPapers -> {
                                    papers = tempPapers;
                                    clickedAuthorName = userId;
                                    contextInfoProperty.set(!contextInfoProperty.get());
                                })
                                .executeWith(Globals.TASK_EXECUTOR);
                    }
                }
                 });
            return cell ;
        });

        PseudoClass lowerOrderRows = PseudoClass.getPseudoClass("lowerOrderRows");

        final int min = 3 > size ? size : 3;
        table.setRowFactory(tableView -> {
            TableRow<RelatedArticleRecommendation> row = new TableRow<RelatedArticleRecommendation>();
            row.itemProperty().addListener((Observable observable)-> {
                if (row.itemProperty().get() != null)
                {
                    row.pseudoClassStateChanged(lowerOrderRows, (size - row.itemProperty().get().getRno()) < min);
                }
            });

            return row;
        });
        return table;
    }

    /**
     * Creates a HashMap containing articles name and their url
     * @param author Name of the author of whose articles are to be retrieved
     * @return HasMap<String,String> where key is Article Name and Value is URL
     */
    private HashMap<String,String> SearchPapersByAuthor(String author) throws IOException {
        URIBuilder uriBuilder = null;
        HashMap<String,String> papers=new HashMap<>();
        String[] authors = author.split(";");

        for(int j = 0; j < authors.length; j++)
        {
            try {
                uriBuilder = new URIBuilder("https://scholar.google.com//scholar?");
            } catch (URISyntaxException e2) {
                e2.printStackTrace();
            }
            uriBuilder.addParameter("as_q", "");
            uriBuilder.addParameter("as_epq", authors[j]);
            uriBuilder.addParameter("as_occt", "author");

            String url = uriBuilder.toString();
            System.out.println(url);

            Document doc = Jsoup.connect(url).userAgent(URLDownload.USER_AGENT).get();
            int stop = 10/authors.length;
            for (int i = 0; i < stop; i++) {
                Elements link = doc.select(String.format("div[data-rp=%S] div.gs_ri a", i));
                if (link.first() != null) {
                    String paper="";
                    if(link.first().childNode(0)!=null)
                    {
                        paper=link.first().childNode(0).toString();
                        if(paper.contains("gs_or_svg"))
                        {
                            stop++;
                            continue;
                        }
                    }
                    String target = link.first().attr("href");
                    papers.put(paper,target);
                }
            }
        }
        return papers;
    }

    /**
     * Creates a BubbleChart of Recommendation rank against authors
     * @param xAxisUpperBound Upper bound of x-axis
     * @param yAxisUpperBound Upper bound of y-axis
     * @param authorsMap HashMap containing authors name agaist serial number
     * @param title Name of the currently selected BibEntry
     * @return BubbleChart of Recommendation rank against authors
     */
    private BubbleChart getBubbleChart(int xAxisUpperBound, int yAxisUpperBound, HashMap<String,Integer> authorsMap, String title)
    {
        NumberAxis xAxis = new NumberAxis(Localization.lang("Ranking"),1,xAxisUpperBound, 1);
        NumberAxis yAxis = new NumberAxis(Localization.lang("Authors"),1,yAxisUpperBound,1);

        yAxis.setTickLabelFormatter(new StringConverter<java.lang.Number>() {
            @Override
            public String toString(java.lang.Number object) {
                for (Map.Entry<String,Integer> entry : authorsMap.entrySet()) {
                    if (entry.getValue().equals(object.intValue())) {
                        return entry.getKey();
                    }
                }
                return "";
            }

            @Override
            public java.lang.Number fromString(String string) {
                return authorsMap.get(string);
            }
        });


        BubbleChart bubbleChart = new BubbleChart(xAxis, yAxis);
        bubbleChart.setLegendVisible(false);
        bubbleChart.setTitle(Localization.lang("Recommendation Rank against Authors"));
        Tooltip.install(bubbleChart, new Tooltip(Localization.lang("You are shown this chart because you clicked \n\"" + title+"\"")));

        return bubbleChart;
    }

    /**
     * Creates a GridPane with two columns
     * @param col1Percentage Width in percentage for first column
     * @param col2Percentage Width in percentage for second column
     * @param col3Percentage Width in percentage for third column
     * @return GridPane
     */
    private GridPane getRelatedArticleGridPane(double col1Percentage, double col2Percentage, double col3Percentage)
    {
        GridPane grid = new GridPane();

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(col1Percentage);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(col2Percentage);

        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(col3Percentage);

        grid.getColumnConstraints().addAll(col1,col2,col3);
        return grid;
    }

    /**
     * Creates a VBox for holding re-rank buttons
     * @param table TableView object to be used in button actions
     * @return VBox
     */
    private VBox getUpDownButtons(TableView<RelatedArticleRecommendation> table)
    {
        Button btnUp = new Button("▲");
        btnUp.setStyle("-fx-text-fill: #07889b;");
        btnUp.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
                ObservableList<RelatedArticleRecommendation> modifiedData = FXCollections.observableArrayList();
                for (int i = 1; i < table.getItems().size(); i++)
                {
                    table.getItems().get(i).setRno(i);
                    modifiedData.add(table.getItems().get(i));
                }
                table.getItems().get(0).setRno(table.getItems().size());
                modifiedData.add(table.getItems().get(0));
                table.setItems(modifiedData);
            }
        });

        Button btnDown = new Button("▼");
        btnDown.setStyle("-fx-text-fill: #07889b;");
        btnDown.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {

                ObservableList<RelatedArticleRecommendation> modifiedData = FXCollections.observableArrayList();
                table.getItems().get(table.getItems().size() - 1).setRno(1);
                modifiedData.add(table.getItems().get(table.getItems().size() - 1));
                for (int i = 0, j = 2; i < table.getItems().size() - 1; i++, j++)
                {
                    table.getItems().get(i).setRno(j);
                    modifiedData.add(table.getItems().get(i));
                }
                table.setItems(modifiedData);
            }
        });

        VBox buttons = new VBox();
        buttons.setSpacing(20);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(btnUp,btnDown);
        return buttons;
    }

    /**
     * Creates a ScrollPane for holding context information
     * @return ScrollPane
     */
    private ScrollPane getContextInformationPane()
    {
        ScrollPane content = new ScrollPane();
        content.setFitToWidth(true);
        content.setFitToHeight(true);
        content.setStyle("-fx-border-color: #e6d4f7");
        Tooltip.install(content, new Tooltip(Localization.lang("Context Information")));

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(5,10,5,10));
        vBox.setSpacing(10);
        vBox.setAlignment(Pos.CENTER_LEFT);

        Label temp = new Label(Localization.lang("Click on author name in the grid to retrieve more articles."));
        temp.setStyle("-fx-text-fill: #07889b;");
        temp.setFont(Font.font(Font.getDefault().getFamily(), FontPosture.ITALIC, 20));
        temp.setWrapText(true);
        vBox.getChildren().add(temp);

        contextInfoProperty.addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue o, Object oldVal, Object newVal) {
                Tooltip.install(content, new Tooltip(Localization.lang("You are shown the social context because you clicked \"" + clickedAuthorName + "\"")));
                vBox.getChildren().clear();
                Label temp = new Label(Localization.lang("Context Information"));
                temp.setStyle("-fx-text-fill: #e37272;");
                temp.setFont(Font.font(Font.getDefault().getFamily(), FontPosture.ITALIC, 20));
                vBox.getChildren().add(temp);
                for (Map.Entry<String,String> entry : papers.entrySet()) {
                    Hyperlink h = new Hyperlink(entry.getKey());
                    h.setOnAction(event -> {
                        try {
                            JabRefDesktop.openBrowser(entry.getValue());
                        } catch (IOException e) {
                            LOGGER.error("Error opening the browser to: " + entry.getValue());
                            dialogService.showErrorDialogAndWait(e);
                        }
                    });
                    vBox.setAlignment(Pos.TOP_LEFT);
                    vBox.getChildren().add(h);
                }
            }
        });
        resultsLoadingProperty.addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue o, Object oldVal, Object newVal) {
                vBox.getChildren().clear();
                Label temp = new Label(Localization.lang("Loading results..."));
                temp.setStyle("-fx-text-fill: #07889b;");
                temp.setFont(Font.font(Font.getDefault().getFamily(), FontPosture.ITALIC, 20));
                temp.setWrapText(true);
                vBox.setAlignment(Pos.CENTER);
                vBox.getChildren().add(temp);
            }
        });
        content.setContent(vBox);

        return content;
    }

    /**
     * Returns a consent dialog used to ask permission to send data to Mr. DLib.
     * @param entry Currently selected BibEntry. (required to allow reloading of pane if accepted)
     * @return StackPane returned to be placed into Related Articles tab.
     */
    private ScrollPane getPrivacyDialog(BibEntry entry) {
        ScrollPane root = new ScrollPane();
        root.getStyleClass().add("related-articles-tab");
        VBox vbox = new VBox();
        vbox.getStyleClass().add("gdpr-dialog");
        vbox.setSpacing(20.0);

        Button button = new Button(Localization.lang("I Agree"));
        button.setDefaultButton(true);

        Text line1 = new Text(Localization.lang("JabRef requests recommendations from Mr. DLib, which is an external service. To enable Mr. DLib to calculate recommendations, some of your data must be shared with Mr. DLib. Generally, the more data is shared the better recommendations can be calculated. However, we understand that some of your data in JabRef is sensitive, and you may not want to share it. Therefore, Mr. DLib offers a choice of which data you would like to share."));
        line1.setWrappingWidth(1300.0);
        Text line2 = new Text(Localization.lang("Whatever option you choose, Mr. DLib may share its data with research partners to further improve recommendation quality as part of a 'living lab'. Mr. DLib may also release public datasets that may contain anonymized information about you and the recommendations (sensitive information such as metadata of your articles will be anonymised through e.g. hashing). Research partners are obliged to adhere to the same strict data protection policy as Mr. DLib."));
        line2.setWrappingWidth(1300.0);
        Text line3 = new Text(Localization.lang("This setting may be changed in preferences at any time."));
        Hyperlink mdlLink = new Hyperlink(Localization.lang("Further information about Mr DLib. for JabRef users."));
        mdlLink.setOnAction(event -> {
            try {
                JabRefDesktop.openBrowser("http://mr-dlib.org/information-for-users/information-about-mr-dlib-for-jabref-users/");
            } catch (IOException e) {
                LOGGER.error("Error opening the browser to Mr. DLib information page.", e);
                dialogService.showErrorDialogAndWait(e);
            }
        });
        VBox vb = new VBox();
        CheckBox cbTitle = new CheckBox(Localization.lang("Entry Title (Required to deliver recommendations.)"));
        cbTitle.setSelected(true);
        cbTitle.setDisable(true);
        CheckBox cbVersion = new CheckBox(Localization.lang("JabRef Version (Required to ensure backwards compatibility with Mr. DLib's Web Service)"));
        cbVersion.setSelected(true);
        cbVersion.setDisable(true);
        CheckBox cbLanguage = new CheckBox(Localization.lang("JabRef Language (Provides for better recommendations by giving an indication of user's preferred language.)"));
        CheckBox cbOS = new CheckBox(Localization.lang("Operating System (Provides for better recommendations by giving an indication of user's system set-up.)"));
        CheckBox cbTimezone = new CheckBox(Localization.lang("Timezone (Provides for better recommendations by indicating the time of day the request is being made.)"));
        vb.getChildren().addAll(cbTitle, cbVersion, cbLanguage, cbOS, cbTimezone);
        vb.setSpacing(10);

        button.setOnAction(event -> {
            JabRefPreferences prefs = JabRefPreferences.getInstance();
            prefs.putBoolean(JabRefPreferences.ACCEPT_RECOMMENDATIONS, true);
            prefs.putBoolean(JabRefPreferences.SEND_LANGUAGE_DATA, cbLanguage.isSelected());
            prefs.putBoolean(JabRefPreferences.SEND_OS_DATA, cbOS.isSelected());
            prefs.putBoolean(JabRefPreferences.SEND_TIMEZONE_DATA, cbTimezone.isSelected());
            dialogService.showWarningDialogAndWait(Localization.lang("Restart"), Localization.lang("Please restart JabRef for preferences to take effect."));
            setContent(getRelatedArticlesPane(entry));
        });

        vbox.getChildren().addAll(line1, line2, mdlLink, line3, vb, button);
        root.setContent(vbox);

        return root;
    }

    @Override
    public boolean shouldShow(BibEntry entry) {
        return preferences.shouldShowRecommendationsTab();
    }

    @Override
    protected void bindToEntry(BibEntry entry) {
        // Ask for consent to send data to Mr. DLib on first time to tab
        if (preferences.isMrdlibAccepted()) {
            setContent(getRelatedArticlesPane(entry));
        } else {
            setContent(getPrivacyDialog(entry));
        }
    }
}
