package com.agentwriter.service;

import com.agentwriter.config.ImageProperties;
import com.agentwriter.entity.ArticleImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedImageStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndReadsImageWithinConfiguredDirectory() throws Exception {
        ImageProperties properties = new ImageProperties();
        properties.setStorageDirectory(temporaryDirectory.toString());
        GeneratedImageStorage storage = new GeneratedImageStorage(properties);

        var stored = storage.store(1L, 2L, new ComfyGeneratedImage("task", "result.png", new byte[]{1, 2, 3}, "image/png"));
        ArticleImage image = new ArticleImage();
        image.setStorageKey(stored.storageKey());

        assertThat(stored.storageKey()).isEqualTo("1/2.png");
        assertThat(storage.readBytes(image)).containsExactly(1, 2, 3);
        assertThat(storage.load(image).contentLength()).isEqualTo(3);
    }
}
