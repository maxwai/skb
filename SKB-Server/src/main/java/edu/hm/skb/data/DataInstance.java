package edu.hm.skb.data;

import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.util.model.Field;
import io.quarkus.arc.Arc;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.NullInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Implementation of the Data Instance
 */
class DataInstance implements Data {

    /**
     * Config instance
     */
    @NotNull
    public static final Data DATA = new DataInstance();
    /**
     * The folder on the mount path where external blocks are saved
     */
    @NotNull
    public static final String EXTERNAL_BLOCK_FOLDER = "External Blocks";
    /**
     * Max retries in a try loop
     */
    private static final int MAX_RETRIES = 10;
    /**
     * The log instance
     */
    @NotNull
    private static final Logger LOG = Logger.getLogger(DataInstance.class);
    /**
     * The folder on the mount path where files are saved
     */
    @NotNull
    private static final String FILE_FOLDER = "Files";
    /**
     * The Config instance
     */
    private final ConfigInjector config = Arc.container().select(ConfigInjector.class).get();


    @Override
    public long getTotalSize() {
        long totalSize = 0;
        try {
            FileStore store = Files.getFileStore(Paths.get(config.getConfig().getMountPath()));
            totalSize += store.getTotalSpace();
        } catch (IOException e) {
            LOG.error("Can't get total size of ", e);
        }
        // convert size in bytes and take a third
        return totalSize / 3;
    }

    @Override
    public long getUsedSize() {
        long usedSize = 0;
        try {
            Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER);
            Files.createDirectories(path);
            try (Stream<Path> files = Files.walk(path)) {
                usedSize = files.map(Path::toFile)
                        .filter(File::isFile)
                        .mapToLong(File::length)
                        .sum();
            }
        } catch (IOException e) {
            LOG.error("failed to get usedSize", e);
        }

        return usedSize;
    }

    /**
     * Generates a byte array containing the block header
     *
     * @param fileRangeList the range of byte of files
     * @return a byte array containing the header
     */
    private Byte[] getHeader(@NotNull List<Config.FileRange> fileRangeList) {
        List<Byte> header = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);

        fileRangeList.forEach((fileRange) -> {
            buffer.putLong(0, fileRange.start());
            for (byte b : buffer.array()) {
                header.add(b);
            }
            buffer.putLong(0, fileRange.stop());
            for (byte b : buffer.array()) {
                header.add(b);
            }
            String filePath = config.getConfig().getFilePath(fileRange.fileId());
            if (filePath == null) {
                throw new IllegalStateException("Should never happen");
            }
            String fileName = Paths.get(filePath).getFileName().toString();
            for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
                header.add(b);
            }
            header.add((byte) 0x1E);
        });
        header.set(header.size() - 1, (byte) 0x1D);

        return header.toArray(new Byte[0]);
    }

    @Override
    @NotNull
    public List<Config.Block> createFile(@NotNull InputStream in, @NotNull Config.File file,
            @NotNull Instant lastModified) throws FileAlreadyExistsException {
        List<Config.Block> updatedBlocks = new ArrayList<>();

        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException("File already exists");
        }

        try {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);

            FileTime fileTime = FileTime.from(lastModified);
            Files.setLastModifiedTime(path, fileTime);

            long size = Files.size(path);
            // the header size is always the same for new blocks only containing one and the same file
            long headerSize = getHeader(List.of(new Config.FileRange(file.id(), 0, 10))).length;
            long blockDataSize = config.getConfig().getBlockSize() - headerSize;
            for (long i = 0; i < size; i += blockDataSize) {
                int retries = 0;
                Config.Block newBlock;
                do {
                    newBlock = new Config.Block(new Field.Uuid64(UUID.randomUUID().toString()).id(),
                            Map.of(), List.of(new Config.FileRange(file.id(), i, Math.min(
                                    i + blockDataSize, size))));
                    retries++;
                } while (retries <= MAX_RETRIES && !config.getConfig().addNewBlock(newBlock));
                if (retries > MAX_RETRIES) {
                    throw new IllegalStateException("Error creating file: too many retries");
                }
                updatedBlocks.add(newBlock);
            }
            // TODO: use already existing blocks that aren't full
        } catch (IOException e) {
            LOG.error("failed to create file", e);
        }

        return updatedBlocks;
    }

    @Override
    @NotNull
    public List<Config.Block> updateFile(@NotNull InputStream in, @NotNull Config.File file,
            @NotNull Instant lastModified) throws FileNotFoundException {

        List<Config.Block> updatedBlocks = new ArrayList<>();

        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist");
        }

        try {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);

            FileTime fileTime = FileTime.from(lastModified);
            Files.setLastModifiedTime(path, fileTime);

            // FIXME: create new blocks if file is bigger
            List<Config.Block> blocks = config.getConfig().getBlocks();
            for (Config.Block block : blocks) {
                if (block.fileToByteRange()
                        .stream()
                        .anyMatch(range -> range.fileId().equals(file.id()))) {
                    updatedBlocks.add(block);
                }
            }
        } catch (IOException e) {
            LOG.error("failed to update file", e);
        }

        return updatedBlocks;
    }

    @Override
    @NotNull
    public List<Config.Block> deleteFile(@NotNull Config.File file) throws FileNotFoundException {

        List<Config.Block> updatedBlocks = new ArrayList<>();

        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist");
        }

        try {
            Files.delete(path);

            List<Config.Block> blocks = config.getConfig().getBlocks();
            for (Config.Block block : blocks) {
                if (block.fileToByteRange()
                        .stream()
                        .anyMatch(range -> range.fileId().equals(file.id()))) {
                    updatedBlocks.add(block);
                    block.fileToByteRange().removeIf(range -> range.fileId().equals(file.id()));
                    if (block.fileToByteRange().isEmpty()) {
                        config.getConfig().deleteBlock(block.id());
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("failed to delete file", e);
        }

        return updatedBlocks;
    }

    @Override
    @NotNull
    public InputStream getFile(@NotNull Config.File file) throws IOException {
        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist");
        }

        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new IOException("Failed to open Output stream", e);
        }
    }

    @Override
    @NotNull
    public Instant getLastModified(@NotNull Config.File file) throws IOException {
        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist");
        }

        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return fileTime.toInstant();
        } catch (IOException e) {
            throw new IOException("Failed to get last modified date", e);
        }
    }

    @Override
    @NotNull
    public List<Config.Block> getBlocks(@NotNull Config.File file) throws FileNotFoundException {
        Path path = Paths.get(config.getConfig().getMountPath(), FILE_FOLDER, file.id());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File does not exist");
        }

        List<Config.Block> blocks = config.getConfig().getBlocks();
        List<Config.Block> fileBlocks = new ArrayList<>();

        for (Config.Block block : blocks) {
            if (block.fileToByteRange()
                    .stream()
                    .anyMatch(range -> range.fileId().equals(file.id()))) {
                fileBlocks.add(block);
            }
        }

        return fileBlocks;
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public InputStream getBlock(@NotNull Config.Block block) throws IOException {
        Config.Block foundBlock = config.getConfig().getBlock(block.id());
        if (foundBlock == null) {
            throw new FileNotFoundException("Block not found in the list of blocks");
        }

        InputStream fileContentIs = null;
        for (Config.FileRange fileRange : foundBlock.fileToByteRange()) {
            InputStream fileContent = getFile(new Config.File(fileRange.fileId(), ""));
            if (fileContent.skip(fileRange.start()) != fileRange.start()) {
                fileContent.close();
                if (fileContentIs != null) {
                    fileContentIs.close();
                }
                throw new IOException("Couldn't skip starting bytes");
            }
            fileContent = BoundedInputStream.builder()
                    .setInputStream(fileContent)
                    .setMaxCount(fileRange.stop() - fileRange.start())
                    .get();
            if (fileContentIs == null) {
                fileContentIs = fileContent;
            } else {
                fileContentIs = new SequenceInputStream(fileContentIs, fileContent);
            }
        }

        // TODO: encrypt block content

        return BoundedInputStream.builder()
                .setInputStream(new SequenceInputStream(new SequenceInputStream(
                        new ByteArrayInputStream(ArrayUtils.toPrimitive(getHeader(foundBlock
                                .fileToByteRange()))), fileContentIs), new NullInputStream(config
                                        .getConfig()
                                        .getBlockSize()) {
                                    @Override
                                    protected void processBytes(byte[] bytes, int offset,
                                            int length) {
                                        for (int i = offset; i < length; i++) {
                                            bytes[i] = 0;
                                        }
                                    }
                                }))
                .setMaxCount(config.getConfig().getBlockSize())
                .get();
    }

    @Override
    @NotNull
    public String getHash(@NotNull Config.Block block, byte[] salt,
            @NotNull Function<InputStream, String> hashFunction) throws IOException {
        return hashFunction.apply(new SequenceInputStream(getBlock(block), new ByteArrayInputStream(
                salt)));
    }


    @Override
    public int getFreeExternalBlocks() {
        long sizeForBlocks = getTotalSize() * 2;
        int amountOfBlocks = (int) (sizeForBlocks / config.getConfig().getBlockSize());
        amountOfBlocks -= config.getConfig().getExternalBlocks().size();
        return amountOfBlocks;
    }

    @Override
    public boolean createExternalBlock(@NotNull InputStream in,
            @NotNull Config.ExternalBlock externalBlock) throws FileAlreadyExistsException {
        Path path = Paths.get(config.getConfig().getMountPath(), EXTERNAL_BLOCK_FOLDER,
                externalBlock.id());

        // Check if the file already exists
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException("External Block already exists");
        }

        try {
            Files.createDirectories(path.getParent());
            Files.copy(in, path);
            return true;
        } catch (FileAlreadyExistsException e) { // NOPMD
            throw e; // NOPMD
        } catch (IOException e) {
            LOG.warn("Failed to create external block", e);
            return false;
        }
    }

    @Override
    public boolean updateExternalBlock(@NotNull InputStream in,
            @NotNull Config.ExternalBlock externalBlock) throws FileNotFoundException {
        Path path = Paths.get(config.getConfig().getMountPath(), EXTERNAL_BLOCK_FOLDER,
                externalBlock.id());

        // Check if the file exists
        if (!Files.exists(path)) {
            throw new FileNotFoundException("External Block does not exist");
        }

        try {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.warn("Failed to update external block", e);
            return false;
        }
    }

    @Override
    public boolean deleteExternalBlock(@NotNull Config.ExternalBlock externalBlock)
            throws FileNotFoundException {
        Path path = Paths.get(config.getConfig().getMountPath(), EXTERNAL_BLOCK_FOLDER,
                externalBlock.id());

        // check if the file exists
        if (!Files.exists(path)) {
            throw new FileNotFoundException("External Block does not exist");
        }

        try {
            // delete file
            Files.delete(path);
            return true;
        } catch (IOException e) {
            LOG.warn("Failed to delete external Block", e);
            return false;
        }
    }


    @Override
    public @NotNull InputStream getExternalBlock(Config.@NotNull ExternalBlock externalBlock)
            throws IOException {
        Path path = Paths.get(config.getConfig().getMountPath(), EXTERNAL_BLOCK_FOLDER,
                externalBlock.id());

        // check if the file exists
        if (!Files.exists(path)) {
            throw new FileNotFoundException("External Block does not exist");
        }
        return Files.newInputStream(path);
    }


    @Override
    public @NotNull Instant getLastModified(Config.@NotNull ExternalBlock externalBlock)
            throws IOException {
        Path path = Paths.get(config.getConfig().getMountPath(), EXTERNAL_BLOCK_FOLDER,
                externalBlock.id());

        // check if the file exists
        if (!Files.exists(path)) {
            throw new FileNotFoundException("External Block does not exist");
        }

        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return fileTime.toInstant();
        } catch (IOException e) {
            throw new IOException("Failed to get last modified date", e);
        }
    }

    @Override
    public @NotNull String getHash(@NotNull Config.ExternalBlock externalBlock, byte[] salt,
            @NotNull Function<InputStream, String> hashFunction) throws IOException {
        return hashFunction.apply(new SequenceInputStream(getExternalBlock(externalBlock),
                new ByteArrayInputStream(salt)));
    }
}
