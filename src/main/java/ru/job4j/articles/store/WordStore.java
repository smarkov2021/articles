package ru.job4j.articles.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.job4j.articles.model.Word;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class WordStore implements Store<Word>, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordStore.class.getSimpleName());

    private final Properties properties;

    private Connection connection;

    public WordStore(Properties properties) {
        this.properties = properties;
        initConnection();
        initScheme();
        initWords();
    }

    private void initConnection() {
        LOGGER.info("Подключение к базе данных слов");
        try {
            connection = DriverManager.getConnection(
                    properties.getProperty("url"),
                    properties.getProperty("username"),
                    properties.getProperty("password")
            );
        } catch (SQLException e) {
            LOGGER.error("Не удалось выполнить операцию: { }", e.getCause());
            e.printStackTrace();
        }
    }

    private void initScheme() {
        LOGGER.info("Создание схемы таблицы слов");
        try (var statement = connection.createStatement()) {
            var sql = Files.readString(Path.of("db/scripts", "dictionary.sql"));
            statement.execute(sql);
        } catch (SQLException e) {
            LOGGER.error("Не удалось выполнить операцию: { }", e.getCause());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initWords() {
        LOGGER.info("Заполнение таблицы слов");
        try (var statement = connection.createStatement()) {
            var sql = Files.readString(Path.of("db/scripts", "words.sql"));
            statement.executeLargeUpdate(sql);
        } catch (IOException | SQLException e) {
            LOGGER.error("Не удалось выполнить операцию: { }", e.getCause());
            e.printStackTrace();
        }
    }

    @Override
    public Word save(Word model) {
        LOGGER.info("Добавление слова в базу данных");
        var sql = "insert into dictionary(word) values(?);";
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, model.getValue());
            statement.executeUpdate();
            var key = statement.getGeneratedKeys();
            if (key.next()) {
                model.setId(key.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("Не удалось выполнить операцию: { }", e.getCause());
            e.printStackTrace();
        }
        return model;
    }

    @Override
    public List<Word> findAll() {
        LOGGER.info("Загрузка всех слов");
        var sql = "select * from dictionary";
        var words = new ArrayList<Word>();
        try (var statement = connection.prepareStatement(sql)) {
            var selection = statement.executeQuery();
            while (selection.next()) {
                words.add(new Word(
                        selection.getInt("id"),
                        selection.getString("word")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Не удалось выполнить операцию: { }", e.getCause());
            e.printStackTrace();
        }
        return words;
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

}
