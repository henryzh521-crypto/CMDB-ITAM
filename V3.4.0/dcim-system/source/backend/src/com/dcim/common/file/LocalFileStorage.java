package com.dcim.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"dcim.storage.type"},
    havingValue = "local",
    matchIfMissing = true
)
public class LocalFileStorage implements FileStorage {
    private final Path baseDir;

    public LocalFileStorage(@Value("${dcim.upload-dir:./data/uploads}") String dir) {
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    private Path resolve(String key) throws IOException {
        Path p = this.baseDir.resolve(key).normalize();
        if (!p.startsWith(this.baseDir)) {
            throw new IOException("非法存储键：" + key);
        } else {
            return p;
        }
    }

    @Override
    public void store(String key, InputStream in, long size, String contentType) throws IOException {
        Path target = this.resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String key) throws IOException {
        Path target = this.resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new IOException("文件不存在：" + key);
        } else {
            return Files.newInputStream(target);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Path target = this.resolve(key);
        Files.deleteIfExists(target);
        Path parent = target.getParent();
        if (parent != null && !parent.equals(this.baseDir)) {
            try (Stream<Path> stream = Files.list(parent)) {
                if (stream.findAny().isEmpty()) {
                    Files.deleteIfExists(parent);
                }
            }
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.isRegularFile(this.resolve(key));
        } catch (IOException var3) {
            return false;
        }
    }
}
