package edu.hm.skb.util;

import edu.hm.skb.config.Config;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * List from <a href="https://gist.github.com/dracos/dd0668f281e685bad51479e5acaadb93">
 * gist.github.com/dracos</a>
 */
@Startup
@ApplicationScoped
public class WordListBean {

    /**
     * The log instance
     */
    @NotNull
    private static final Logger LOG = Logger.getLogger(WordListBean.class);

    /**
     * Cached list of words
     */
    private final List<String> wordList;

    /**
     * Generate backup Code
     *
     * @param config      the config Instance
     * @param amountWords the amount of words to use for the backup code
     * @return the backup code string if successful, null if not
     */
    @Nullable
    public String generateBackupCode(Config config, int amountWords) {
        StringBuilder backupCode = new StringBuilder();
        List<String> wordListCopy = new ArrayList<>(getWordList());
        long maxTries = (long) Math.pow(2, wordListCopy.size());
        long counter = 0;
        do {
            Collections.shuffle(wordListCopy);
            backupCode.setLength(0);
            backupCode.append(wordListCopy.stream()
                    .limit(amountWords)
                    .reduce((s, s2) -> s + " " + s2)
                    .orElse(""));
            counter++;
        } while (counter < maxTries && backupCode.isEmpty() && config.getServers()
                .stream()
                .anyMatch(server -> server.backupCode().contentEquals(backupCode)));
        if (counter == maxTries) {
            return null;
        }
        return backupCode.toString();
    }

    /**
     * Creates a Word List.
     */
    /* default */ WordListBean () {
        File wordListFile = new File("./wordlist.txt");
        if (!wordListFile.exists() || !wordListFile.canRead()) {
            throw new IllegalStateException(
                    "Word file doesn't exists or isn't readable at " + wordListFile
                            .getAbsolutePath());
        }
        try (BufferedReader reader = Files.newBufferedReader(wordListFile.toPath())) {
            wordList = reader.lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (wordList.size() < 5) {
            throw new IllegalStateException("Not enough words in word list. Need at least 5");
        }
    }

    /**
     * @return the word list, not shuffled
     */
    public List<String> getWordList() {
        return wordList;
    }
}
