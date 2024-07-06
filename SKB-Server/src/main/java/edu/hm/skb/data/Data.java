package edu.hm.skb.data;

import edu.hm.skb.config.Config;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Interface for handling Data on the file system
 */
public interface Data {

    /**
     * @return the data instance
     */
    static Data getData() {
        return DataInstance.DATA;
    }

    // ##### File system ####

    /**
     * @return The size in bytes of usable data for files (so 1/3 of the file system normally)
     */
    long getTotalSize();

    /**
     * @return The size of the files saved
     */
    long getUsedSize();

    // ##### Files #####

    /**
     * Create a new file. The file will not be saved at the Path specified in
     * {@link Config.File}{@code .path}.
     *
     * @param in           The Byte Stream of the file
     * @param file         The file Instance
     * @param lastModified The last modified date
     * @return List of Blocks that were updated or created. Empty if the file couldn't be created
     * @throws FileAlreadyExistsException if the file already exists
     */
    @NotNull
    List<Config.Block> createFile(@NotNull InputStream in, @NotNull Config.File file,
            @NotNull Instant lastModified) throws FileAlreadyExistsException;

    /**
     * Update the data of a file.
     *
     * @param in           The Byte Stream of the file
     * @param file         The file Instance
     * @param lastModified The last modified date of the file
     * @return List of Blocks that were updated or created. Empty if the file couldn't be updated
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     */
    @NotNull
    List<Config.Block> updateFile(@NotNull InputStream in, @NotNull Config.File file,
            @NotNull Instant lastModified) throws FileNotFoundException;

    /**
     * Delete a file from the local file system.
     *
     * @param file The file Instance
     * @return List of Blocks that were updated or deleted. Empty if the file couldn't be deleted
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     */
    @NotNull
    List<Config.Block> deleteFile(@NotNull Config.File file) throws FileNotFoundException;

    /**
     * Returns the InputStream of a file. It is Important that this output stream is closed.
     *
     * @param file The file Instance
     * @return The Input stream of the file. The stream must be closed in any case.
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     * @throws java.io.IOException   if an I/O error occurs
     */
    @NotNull
    InputStream getFile(@NotNull Config.File file) throws FileNotFoundException, IOException;

    /**
     * Returns the local last modified date of a file.
     *
     * @param file The file Instance
     * @return The Instant representing the Last Modified Date.
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     * @throws IOException           if the lastModified could not be extracted
     */
    @NotNull
    Instant getLastModified(@NotNull Config.File file) throws FileNotFoundException, IOException;

    // ##### Internal Blocks #####

    /**
     * Returns a List of all blocks containing data for a specific file.
     *
     * @param file The file Instance
     * @return List of blocks that contains some data from the file.
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     */
    @NotNull
    List<Config.Block> getBlocks(@NotNull Config.File file) throws FileNotFoundException;

    /**
     * Returns the InputStream of a block. It is Important that this output stream is closed.
     * This data is already encrypted.
     *
     * @param block The block Instance
     * @return The Input stream of the block. The stream must be closed in any case.
     * @throws FileNotFoundException if the file in the block doesn't exist on the local file system
     * @throws IOException           if InputStream could not be opened
     */
    InputStream getBlock(@NotNull Config.Block block) throws FileNotFoundException, IOException;

    /**
     * Calculate hash of a block with the given salt
     *
     * @param block        The Block Instance
     * @param salt         The Salt to put at the end of the block
     * @param hashFunction The function that gives the Hash from an InputStream
     * @return The calculated Hash
     * @throws FileNotFoundException if the block doesn't exist on the local file system
     * @throws IOException           if files couldn't be read
     */
    @NotNull
    String getHash(@NotNull Config.Block block, byte[] salt,
            @NotNull Function<InputStream, String> hashFunction) throws FileNotFoundException,
            IOException;

    // ##### External Blocks #####

    /**
     * Returns the amount of external blocks that can still be saved.
     * Reserved Blocks are also taken in considerations for the calculation.
     *
     * @return the amount of external blocks
     */
    int getFreeExternalBlocks();

    /**
     * Create a new external Block to save
     *
     * @param in            The Byte Stream of the Block
     * @param externalBlock The external Block Instance
     * @return if the block was created successfully.
     * @throws FileAlreadyExistsException if the block already exist.
     */
    boolean createExternalBlock(@NotNull InputStream in,
            @NotNull Config.ExternalBlock externalBlock) throws FileAlreadyExistsException;

    /**
     * Update the data of an external Block
     *
     * @param in            The Byte Stream of the Block
     * @param externalBlock the external Block Instance
     * @return if the block was updated successfully
     * @throws FileNotFoundException if the block doesn't exist locally.
     */
    boolean updateExternalBlock(@NotNull InputStream in,
            @NotNull Config.ExternalBlock externalBlock) throws FileNotFoundException;

    /**
     * Delete an external Block
     *
     * @param externalBlock The external Block Instance
     * @return if the block was deleted successfully
     * @throws FileNotFoundException if the block doesn't exist locally.
     */
    boolean deleteExternalBlock(@NotNull Config.ExternalBlock externalBlock)
            throws FileNotFoundException;

    /**
     * Returns the OutputStream of a block. It is Important that this output stream is closed.
     *
     * @param externalBlock The external block Instance
     * @return The Output stream of the block. The stream must be closed in any case.
     * @throws FileNotFoundException if the block doesn't exist on the local file system
     * @throws IOException           if Outputstream could not be opened
     */
    @NotNull
    InputStream getExternalBlock(@NotNull Config.ExternalBlock externalBlock)
            throws FileNotFoundException, IOException;

    /**
     * Returns the local last modified date of an external Block.
     *
     * @param externalBlock The external Block Instance
     * @return The Instant representing the Last Modified Date.
     * @throws FileNotFoundException if the file doesn't exist on the local file system
     * @throws IOException           if the lastModified time couldn't be extracted
     */
    @NotNull
    Instant getLastModified(@NotNull Config.ExternalBlock externalBlock)
            throws FileNotFoundException, IOException;

    /**
     * Calculate hash of a block with the given salt
     *
     * @param externalBlock The external Block Instance
     * @param salt          The Salt to put at the end of the block
     * @param hashFunction  The function that gives the Hash from an InputStream
     * @return The calculated Hash
     * @throws FileNotFoundException if the block doesn't exist on the local file system
     * @throws IOException           if hash could not be calculated
     */
    @NotNull
    String getHash(@NotNull Config.ExternalBlock externalBlock, byte[] salt,
            @NotNull Function<InputStream, String> hashFunction) throws FileNotFoundException,
            IOException;

}
