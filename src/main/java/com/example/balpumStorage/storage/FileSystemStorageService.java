package com.example.balpumStorage.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import com.example.balpumStorage.file.entity.FileEntity;
import com.example.balpumStorage.file.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;
    private final FileRepository fileRepository;

    @Autowired
    public FileSystemStorageService(StorageProperties properties, FileRepository fileRepository) {
        if(properties.getLocation().trim().length() == 0){
            throw new StorageException("File upload location can not be Empty.");
        }
        this.rootLocation = Paths.get(properties.getLocation());
        this.fileRepository = fileRepository;
    }

    @Override
    public void store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            Path destinationFile = this.rootLocation.resolve(
                            Paths.get(file.getOriginalFilename()))
                    .normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                // This is a security check
                throw new StorageException(
                        "Cannot store file outside current directory.");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile,
                        StandardCopyOption.REPLACE_EXISTING);
                // 파일 정보를 데이터베이스에 저장
                FileEntity fileEntity = new FileEntity();
                fileEntity.setFilename(file.getOriginalFilename());
                fileEntity.setFilepath(destinationFile.toString());
                fileRepository.save(fileEntity);
            }
        }
        catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return fileRepository.findAll().stream()
                    .map(fileEntity -> Paths.get(fileEntity.getFilepath()));
            // rootlocation에 존재하는 파일들을 불러옴
//            return Files.walk(this.rootLocation, 1)
//                    .filter(path -> !path.equals(this.rootLocation))
//                    .map(this.rootLocation::relativize);
        }
        catch (Exception e) {
            throw new StorageException("Failed to read stored files", e);
        }

    }

    @Override
    public Path load(String filename) {
        FileEntity fileEntity = fileRepository.findByFilename(filename);
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filename);
        }
        return Paths.get(fileEntity.getFilepath());
        //return rootLocation.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            }
            else {
                throw new StorageFileNotFoundException(
                        "Could not read file: " + filename);

            }
        }
        catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public void deleteAll() {
        fileRepository.deleteAll();
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        }
        catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }
}