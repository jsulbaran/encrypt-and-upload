package com.jsulbaran.file.encryptor.model;

import com.jsulbaran.file.encryptor.service.DropboxUploaderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.nio.file.FileVisitResult.CONTINUE;

@Slf4j
@Component
public class ExtensionFileVisitor extends SimpleFileVisitor<Path> {
    private static final String WORKING_EXTENSION = ".work";
    private static final String ENCRYPTED_EXTENSION = ".gpg";

    @Value("#{'${extensions}'.split(',')}")
    private Set<String> extensions = new HashSet<>();
    @Value("${input.path}")
    private String inputPath;
    @Value("${working.path}")
    private String workingPath;

    @Value("${output.path}")
    private String outputPath;
    @Value("${default_key}")
    private String defaultKey;
    @Autowired
    private DropboxUploaderService dropboxUploader;
    private final FileEncryptor fileEncryptor = new FileEncryptor();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
        if (attr.isSymbolicLink()) {
            log.info("Symbolic link: {} ", file);
        } else if (attr.isRegularFile()) {
            log.info("visiting file: {} ", file);
            final String extension = FilenameUtils.getExtension(file.getFileName().toString());
            if (extensions.contains(extension)) {
                final Optional<File> movedFile = moveFileToWorking(file.toFile());
                if (movedFile.isPresent()) {
                    final Optional<File> encrypted = encryptFile(movedFile.get());
                    if (encrypted.isPresent()) {
                        try {
                            dropboxUploader.upload(encrypted.get());
                            remove(movedFile.get());
                            remove(encrypted.get());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
//            extensions.add(extension);
        } else {
            log.info("Other file: {} ", file);
        }
        log.info("size: {}", humanReadableByteCountBin(attr.size()));
        return CONTINUE;
    }

    private void remove(File file) {
        FileUtils.deleteQuietly(file);
    }

    private Optional<File> encryptFile(File file) {
        log.info("Encrypting file : {}", file.getName());
        final String inputFileName = file.getName();
        final File newFile = new File(outputPath + File.separator + inputFileName.replace(WORKING_EXTENSION, ENCRYPTED_EXTENSION));
        try {
            fileEncryptor.encryptFile(newFile.getAbsolutePath(), file.getAbsolutePath(), defaultKey.toCharArray(), true, true);
            return Optional.of(newFile);
        } catch (Exception e) {
            log.info("Error encrypting file : {}", file.getName());
        }
        return Optional.empty();
    }

    private Optional<File> moveFileToWorking(File file) {
        log.info("moving file to work folder: {} ", file);
        final String newFilename = file.getName() + WORKING_EXTENSION;
        final File newFile = new File(workingPath + File.separator + newFilename);
        try {
            FileUtils.copyFile(file, newFile);
            FileUtils.deleteQuietly(file);
            return Optional.of(newFile);
        } catch (IOException e) {
            log.info("Error on file : ", e);
        }
        return Optional.empty();
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        log.info("Directory: {}", dir);
        if (!inputPath.equals(dir.toFile().getAbsolutePath())) {
            deleteIfEmpty(dir);
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path path, IOException exc) {
        log.info("Error: {} ", exc);

        return CONTINUE;
    }

    private void deleteIfEmpty(Path path) {

        try (Stream<Path> entries = Files.list(path)) {
            if (entries.findAny().isEmpty()) {
                path.toFile().delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

    public Set<String> getExtensions() {
        return extensions;
    }
}