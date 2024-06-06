package com.example.balpumStorage.storage;

import com.example.balpumStorage.file.entity.FileEntity;
import com.example.balpumStorage.file.resource.FileResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface StorageService {

    void store(MultipartFile file);

    Stream<Path> loadAll();

    Path load(String filename);

    FileResource loadAsResource(String filename);

    void deleteAll();

    FileEntity getFileDetails(String filename);

    FileEntity updateFileDetails(String filename, String newOriginalFilename);

    void deleteFile(String filename);
}