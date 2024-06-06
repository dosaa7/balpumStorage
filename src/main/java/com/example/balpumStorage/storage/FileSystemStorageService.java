package com.example.balpumStorage.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.balpumStorage.file.entity.FileEntity;
import com.example.balpumStorage.file.repository.FileRepository;
import com.example.balpumStorage.file.resource.FileResource;
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
        if (properties.getLocation().trim().length() == 0) {
            throw new StorageException("File upload location can not be Empty.");
        }
        this.rootLocation = Paths.get(properties.getLocation());
        this.fileRepository = fileRepository;
    }

    @Override
    public void store(MultipartFile file, String refPath) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            String originalFilename = file.getOriginalFilename();

            Path path = Paths.get(refPath).normalize();
            if (path.isAbsolute()) {
                throw new StorageException("Absolute paths are not allowed.");
            }

            Path destinationFilePath = this.rootLocation.resolve(path).normalize().toAbsolutePath();

            if (!destinationFilePath.startsWith(this.rootLocation.toAbsolutePath())) {
                throw new StorageException("Cannot store file outside current directory.");
            }

            Path directory = destinationFilePath.getParent();
            if (directory != null && !Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            // 파일 저장 전에 DB에서 동일한 파일이 있는지 확인
            Optional<FileEntity> existingFile = fileRepository.findByFilepath(destinationFilePath.toString());
            if (existingFile.isPresent()) {
                // 기존 파일이 존재한다면 덮어쓰기
                existingFile.get().setOriginalFilename(originalFilename);
                fileRepository.save(existingFile.get());
            } else {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, destinationFilePath, StandardCopyOption.REPLACE_EXISTING);
                }

                FileEntity fileEntity = new FileEntity();
                fileEntity.setOriginalFilename(originalFilename);
                fileEntity.setStoredFilename(destinationFilePath.getFileName().toString());
                fileEntity.setFilepath(destinationFilePath.toString());
                fileRepository.save(fileEntity);
            }

        } catch (IOException e) {
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
        } catch (Exception e) {
            throw new StorageException("Failed to read stored files", e);
        }
    }

    @Override
    public Path load(String filename) {
        FileEntity fileEntity = fileRepository.findByStoredFilename(filename);
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filename);
        }
        return Paths.get(fileEntity.getFilepath());
        //return rootLocation.resolve(filename);
    }

    @Override
    public FileResource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                FileEntity fileEntity = fileRepository.findByStoredFilename(filename);
                return new FileResource(resource, fileEntity.getOriginalFilename());
            } else {
                throw new StorageFileNotFoundException(
                        "Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public void deleteAll() {
        fileRepository.deleteAll();
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }

    public FileEntity getFileDetails(String filename) {
        FileEntity fileEntity = fileRepository.findByStoredFilename(filename);
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filename);
        }
        return fileEntity;
    }

    public FileEntity updateFileDetails(String filename, String newOriginalFilename) {
        FileEntity fileEntity = fileRepository.findByStoredFilename(filename);
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filename);
        }
        fileEntity.setOriginalFilename(newOriginalFilename);
        return fileRepository.save(fileEntity);
    }

    public void deleteFile(String filename) {
        FileEntity fileEntity = fileRepository.findByStoredFilename(filename);
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filename);
        }
        Path filePath = Paths.get(fileEntity.getFilepath());
        try {
            Files.deleteIfExists(filePath);
            fileRepository.delete(fileEntity);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + filename, e);
        }
    }
}