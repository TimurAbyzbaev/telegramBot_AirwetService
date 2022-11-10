import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.security.GeneralSecurityException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.SneakyThrows;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Необходим JSON файл для авторизации в гугл таблицах.
 *
 * Бот проектировался для внутреннего использования в фирме для учета заявок на тех. обслуживание оборудования.
 * Пользоваться им предназначалось сервисным инженерам для упрощения заполнения таблицы с заявками.
 */
public class TelegramBot extends TelegramLongPollingBot {
    private final UsersList usersList = new UsersList();

    private static Credential autorize() throws IOException, GeneralSecurityException {
        InputStream in = TelegramBot.class.getResourceAsStream("/json.json"); //файл для авторизации должен быть в таргете/классы
        if (in == null) throw new AssertionError();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), new InputStreamReader(in)
        );
        List<String> scopes = List.of(SheetsScopes.SPREADSHEETS);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver())
                .authorize("admin5");
    }

    public static Sheets getSheetsService() throws IOException, GeneralSecurityException {
        Credential credential = autorize();
        String APPLICATION_NAME = "telegram bot app"; // имя приложения для авторизации
        return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public TelegramBot(DefaultBotOptions options) {
        super(options);
    }

    @Override
    public String getBotUsername() {
        return "@AirwetServiceBot"; //имя бота
    }

    @Override
    public String getBotToken() {
        return returnBotToken(); //токен бота

    }

    /**
     * Необходимо добавить файл с токеном для бота в корневую папку
     */
    private String returnBotToken(){
        String token;
        try{
            File file = new File("BOT_TOKEN.txt");
            FileReader fr = new FileReader(file);
            BufferedReader reader = new BufferedReader(fr);
            token = reader.readLine();

        } catch (IOException e) {
            e.printStackTrace();
            token = "";
        }
        return token;
    }

    /**
     * Проверка введенного номера заявки с имеющимися заявками в списке. Ложь если нет, истина если номер есть
     *
     * @param number Номер заявки String
     * @return true если такой номер есть.
     * @throws GeneralSecurityException эксепшн основной защиты
     * @throws IOException              эксепшн ввода вывода
     */
    public static boolean checkApplicationNumber(String number) throws GeneralSecurityException, IOException {
        try {
            Sheets sheetsService = getSheetsService();
            String SPREADSHEET_ID = returnSpreadSheetsId();
            ValueRange response = sheetsService.spreadsheets().values().get(SPREADSHEET_ID, "Лист1!A1:A").execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                System.out.println("No data found.");
            } else {
                return values.stream().map(row -> row.get(0)).noneMatch(a -> a.equals(number));
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return true;
    }

    /**
     * Необходимо добавить файл с ID гугл таблицы в корневую папку
     */
    private static String returnSpreadSheetsId(){
        String id;
        try{
            File file = new File("SPREADSHEET_ID.txt");
            FileReader fr = new FileReader(file);
            BufferedReader reader = new BufferedReader(fr);
            id = reader.readLine();

        } catch (IOException e) {
            e.printStackTrace();
            id = "";
        }
        return id;
    }


    /**
     *
     * @param listString - массив данных
     * @param solveRequest - булева переменная для выбора поведения метода (внести или закрыть заявку)
     */
    public static void appendDataInSheet(String[] listString, boolean solveRequest) throws GeneralSecurityException, IOException {
        Sheets sheetsService;
        sheetsService = getSheetsService();
        String SPREADSHEET_ID = returnSpreadSheetsId();

        if (solveRequest) //для закрытия заявки
        {
            ValueRange appendBody1 = new ValueRange()
                    .setValues(List.of(
                            List.of(listString[2])));
            ValueRange appendBody2 = new ValueRange()
                    .setValues(List.of(
                            List.of(listString[1])));
            //отправка запроса на изменение данных в таблице
            int applicationNumber = Integer.parseInt(listString[0]) + 1;
            sheetsService.spreadsheets().values()
                    .update(SPREADSHEET_ID, "Лист1!H" + applicationNumber, appendBody1)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
            sheetsService.spreadsheets().values()
                    .update(SPREADSHEET_ID, "Лист1!I" + applicationNumber, appendBody2)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        } else //для создания заявки
        {
            ValueRange response = sheetsService.spreadsheets().values().get(SPREADSHEET_ID, "Лист1!A1:A").execute();
            List<List<Object>> values = response.getValues();
            int currentRow = 0;
            if (values == null || values.isEmpty()) {
                System.out.println("No data found.");
            } else {
                currentRow = Integer.parseInt(values.get(values.size() - 1).get(0).toString()) + 1;
            }
            ValueRange appendBody = new ValueRange()
                    .setValues(List.of(
                            Arrays.asList(currentRow, listString[0], listString[1], listString[2], listString[3], listString[4], listString[5])
                    ));
            //отправка запроса на изменение данных в таблице
            try (FileWriter writer = new FileWriter("log.txt", true)) {
                writer.append("Внесение заявки\n");
                Date date = new Date();
                writer.append(date.toString());
                writer.append('\n');
                for (String s : listString) writer.write(s + '\n');
                writer.append('\n');
                writer.flush();
            } catch (IOException ex) {

                System.out.println(ex.getMessage());
            }
            sheetsService.spreadsheets().values()
                    .append(SPREADSHEET_ID, "Лист1", appendBody)
                    .setValueInputOption("USER_ENTERED")
                    .setInsertDataOption("INSERT_ROWS")
                    .setIncludeValuesInResponse(true)
                    .execute();
        }
    }

    /**
     * Метод проверяет статус заявки (закрыта или нет).
     *
     * @param numberApp Номер заявки в String
     * @return Возвращает "0" если заявка не закрыта, иначе возвращает содержимое ячеек
     * @throws GeneralSecurityException эксепшн основной защиты
     * @throws IOException              эксепшн ввода вывода
     */
    public static String applicationNumberNotClosed(String numberApp) throws GeneralSecurityException, IOException {

        Sheets sheetsService = getSheetsService();
        String SPREADSHEET_ID = returnSpreadSheetsId();
        int number = Integer.parseInt(numberApp) + 1;
        ValueRange response = sheetsService.spreadsheets().values().get(SPREADSHEET_ID, "Лист1!H" + number + ":I" + number).execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return "0";
        } else {
            return values.toString();
        }
    }

    /**
     * Метод возвращает данные из гугл таблицы
     *
     * @param range Параметр диапазона считываемых данных String
     * @return Возвращает данные из таблицы "List<List<Object>>"
     * @throws GeneralSecurityException эксепшн основной защиты
     * @throws IOException              эксепшн ввода вывода
     */
    public static List<List<Object>> getDataFromSheet(String range) throws GeneralSecurityException, IOException { //range format Лист1!А1:B1
        Sheets sheetsService = getSheetsService();
        String SPREADSHEET_ID = returnSpreadSheetsId();
        ValueRange response = sheetsService.spreadsheets().values().get(SPREADSHEET_ID, range).execute();
        return response.getValues();
    }


    @SneakyThrows
    public static void main(String[] args) {
        TelegramBot bot = new TelegramBot(new DefaultBotOptions());
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
    }


    @Override
    @SneakyThrows
    public void onUpdateReceived(Update update) {
        User currentUser;
        if (update.hasMessage()) {
            Message message = update.getMessage();

            currentUser = new User(message.getFrom().getId(), message.getFrom().getUserName());

            if (!usersList.getUserList().containsKey(currentUser.getUserId())) { //провека на наличие этого пользователя в списке
                usersList.addNewUser(currentUser);
            }
            currentUser = usersList.getUserList().get(currentUser.getUserId());

            String textMessage = message.getText().toLowerCase(Locale.ROOT);

            if (!currentUser.isStatusRequest() && !currentUser.isNewRequestFlag() && !currentUser.isSolveRequestFlag()) {
                if (!textMessage.isEmpty()) {
                    switch (textMessage) {
                        case "stop", "стоп" -> {
                            usersList.deleteUser(currentUser);
                            String[] buttonsText = new String[1];
                            buttonsText[0] = "Старт";
                            sendMessage(message.getChatId(), "Заполнение заявки прервано.\nВведите Старт для создания новой.\n ", true, buttonsText);
                        }
                        case "внести" -> {
                            currentUser.setNewRequest();
                            sendMessage(message.getChatId(), "Принято!\nВведите название название города, в котором расположен объект.\n");
                        }
                        case "закрыть" -> {
                            currentUser.setSolveRequest();
                            sendMessage(message.getChatId(), "Принято!\nВведите номер заявки, которую необходимо закрыть. \n ");
                        }
                        case "статус" -> {
                            currentUser.setStatusRequest();
                            sendMessage(message.getChatId(), "Принято!\nВведите номер заявки, статус которой вы хотите узнать. \n ");
                        }
                        default -> {
                            String[] buttonsText = getButtonsText(3, "Внести", "Закрыть", "Статус");
                            sendMessage(message.getChatId(), """
                                    Для внесения заявки введите Внести
                                    Для закрытия заявки введите Закрыть
                                    Для просмотра заявки введите Статус""", true, buttonsText);
                        }
                    }
                }
            }
            else if (currentUser.isStatusRequest()) {
                int requestNumber;
                try {
                    requestNumber = Integer.parseInt(textMessage) + 1;
                    List<List<Object>> dataFromSheet = getDataFromSheet("A" + requestNumber + ":I" + requestNumber);
                    if (checkApplicationNumber(textMessage)) {
                        sendMessage(message.getChatId(), """
                                Извините, не могу найти такую заявку в журнале.
                                Проверьте номер заявки и отправьте номер еще раз.
                                Для того чтобы прервать заполнение заявки отправьте Стоп""");
                    } else {
                        execute(SendMessage.builder()
                                .chatId(message.getChatId().toString())
                                .text("Данные в таблице:\n").build());
                        String answer;
                        String[] ans = new String[9];
                        for (int j = 0; j < dataFromSheet.get(0).size(); j++) {
                            ans[j] = dataFromSheet.get(0).get(j).toString();
                        }
                        answer = "Номер заявки - " + ans[0] + "\n\n";
                        answer += "Внес - " + ans[1] + "\n\n";
                        answer += "Дата внесения - " + ans[2] + "\n\n";
                        answer += "Город - " + ans[3] + "\n\n";
                        answer += "Объект - " + ans[4] + "\n\n";
                        answer += "Тип проблемы - " + ans[5] + "\n\n";
                        answer += "Описание проблемы - " + ans[6] + "\n\n";
                        answer += "Дата исправления - " + ans[7] + "\n\n";
                        answer += "Что было сделано - " + ans[8] + "\n\n";
                        sendMessage(message.getChatId(), answer);
                        usersList.deleteUser(currentUser);
                    }
                } catch (Exception e) {
                    System.out.println(e);
                    sendMessage(message.getChatId(), "Введите пожалуйста номер заявки");
                }
            }
            else if (currentUser.isSolveRequestFlag() && currentUser.solveRequest[0] == null) {
                String temp = applicationNumberNotClosed(textMessage);
                if (checkApplicationNumber(textMessage)) {
                    sendMessage(message.getChatId(), """
                            Извините, не могу найти такую заявку в журнале.
                            Проверьте номер заявки и отправьте номер еще раз.
                            Для того чтобы прервать заполнение заявки отправьте Стоп""");
                }
                else if (!temp.equalsIgnoreCase("0")) {
                    sendMessage(message.getChatId(), "Данная заявка уже имеет статус закрытой. Данные в таблице:\n");

                    sendMessage(message.getChatId(), temp);

                    sendMessage(message.getChatId(), "Проверьте номер заявки и повторно отправьте его. Отправьте Стоп для того чтобы " +
                            "прервать закрытие заявки и начать сначала\n");
                }
                else {
                    currentUser.solveRequest[0] = message.getText();//номер заявки
                    sendMessage(message.getChatId(), "Принято!\nЧто было сделано для решения проблемы? \n ");
                }
            }
            else if (currentUser.isSolveRequestFlag() && currentUser.solveRequest[1] == null) {
                currentUser.solveRequest[1] = textMessage;//решение
                sendMessage(message.getChatId(), "Принято!\nВведите дату, когда проблема была устранена. \n ");
            }
            else if (currentUser.isSolveRequestFlag() && currentUser.solveRequest[2] == null) {
                currentUser.solveRequest[2] = textMessage;//дата исправления
                appendDataInSheet(currentUser.solveRequest, true);
                sendMessage(message.getChatId(), "Данные успешно занесены в журнал");

                String[] buttonsText = getButtonsText(3, "Внести", "Закрыть", "Статус");
                sendMessage(message.getChatId(), """
                        Для внесения заявки введите Внести
                        Для закрытия заявки введите Закрыть
                        Для просмотра заявки введите Статус""", true, buttonsText);

                usersList.deleteUser(currentUser);
            }
            else if (currentUser.isNewRequestFlag() && currentUser.newRequest[2] == null) {
                currentUser.newRequest[2] = textMessage;
                sendMessage(message.getChatId(), "Принято!\nВведите название объекта. \n ");
            }
            else if (currentUser.isNewRequestFlag() && currentUser.newRequest[3] == null) {
                currentUser.newRequest[3] = textMessage;
                String[] buttonsText = getButtonsText(4, "Гидравлическая", "Электрическая", "Программная", "Другое");
                sendMessage(message.getChatId(), "Принято!\nК какому типу относится заявка (гидравлическая, электрическая, программная или другое). \n ",
                        true, buttonsText);
            } else if (currentUser.isNewRequestFlag() && currentUser.newRequest[4] == null) {
                currentUser.newRequest[4] = textMessage;
                sendMessage(message.getChatId(), "Принято!\nОпишите суть проблемы. \n ");
            }
            else if (currentUser.isNewRequestFlag() && currentUser.newRequest[5] == null) {
                currentUser.newRequest[5] = textMessage;

                usersNameAndDateToData(message, currentUser);

                appendDataInSheet(currentUser.newRequest, false);

                List<List<Object>> dataA = getDataFromSheet("Лист1!A1:A");
                int appNumber = Integer.parseInt(dataA.get(dataA.size() - 1).get(0).toString());
                sendMessage(message.getChatId(), "Заявка внесена в журнал\nНомер заявки " + appNumber + "\n");

                String[] buttonsText = getButtonsText(3, "Внести", "Закрыть", "Статус");
                sendMessage(message.getChatId(), """
                        Для внесения заявки введите Внести
                        Для закрытия заявки введите Закрыть
                        Для просмотра заявки введите Статус""", true, buttonsText);
                usersList.deleteUser(currentUser);
            }
        }

    }

    private String[] getButtonsText(int x, String first, String second, String third, String fourth) {
        String[] buttonsText = new String[x];
        buttonsText[0] = first;
        buttonsText[1] = second;
        buttonsText[2] = third;
        buttonsText[3] = fourth;
        return buttonsText;
    }

    private String[] getButtonsText(int x, String first, String second, String third) {
        String[] buttonsText = new String[x];
        buttonsText[0] = first;
        buttonsText[1] = second;
        buttonsText[2] = third;
        return buttonsText;
    }

    /**
     * Метод заполняет имя и дату внесения заявки в классе User
     *
     * @param message сообщение из метода update
     */
    private void usersNameAndDateToData(Message message, User user) { // заполнение имени пользователя и даты в массив пользователя
        try {
            user.newRequest[0] = message.getFrom().getFirstName(); //кто
            execute(SendMessage.builder()
                    .chatId(message.getChatId().toString())
                    .text("Данные для заявки заполнены. Скоро информация будет добавлена в журнал.\n ").build());

            DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
            Date date = new Date();
            user.newRequest[1] = dateFormat.format(date); //дата
        } catch (Exception e) {
            System.out.println(e);
            sendMessage(message.getChatId(), "Непредвиденная ошибка. \n Начните, пожалуйста, сначала");
            usersList.deleteUser(user);
        }
    }


    private void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text).build());
        } catch (TelegramApiException e) {
            System.out.println(e);
        }
    }

    private void sendMessage(Long chatId, String text, boolean addKeyboard, String[] buttonsText) {
        try {
            if (!addKeyboard) {
                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text).build());
            } else {
                execute(SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text).replyMarkup(keyboard(buttonsText)).build());

            }

        } catch (TelegramApiException e) {
            System.out.println(e);
        }
    }

    /**
     * Метод создания и редактирования всплывающей клавиатуры
     *
     * @param textButtons Массив текста которым заполнятся кнопки
     * @return возвращает ReplyKeyboardMarkup
     */
    private static ReplyKeyboardMarkup keyboard(String[] textButtons) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        if (textButtons.length != 0)
            replyKeyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        // Create a keyboard row
        KeyboardRow row = new KeyboardRow();
        // Set each button, you can also use KeyboardButton objects if you need something else than text
        row.addAll(Arrays.asList(textButtons));
        keyboard.add(row);
        replyKeyboardMarkup.setKeyboard(keyboard);
        return replyKeyboardMarkup;
    }
}