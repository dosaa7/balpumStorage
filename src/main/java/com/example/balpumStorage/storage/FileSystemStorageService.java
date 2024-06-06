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
import org.springframework.transaction.annotation.Transactional;
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

            Path destinationFilePath = loadSafe(refPath);
            Path directory = destinationFilePath.getParent();
            if (directory != null && !Files.exists(directory)) {
                Files.createDirectories(directory);
                // 디렉토리 권한 설정: 로컬에서 테스트할 때는 주석처리
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxrwxrwx");
                Files.setPosixFilePermissions(directory, permissions);
            }
            // 파일 저장 전에 DB에서 동일한 파일이 있는지 확인
            FileEntity existingFile = fileRepository.findByFilepath(destinationFilePath.toString());
            if (existingFile != null) {
                // 기존 파일이 존재한다면 DB 업데이트
                existingFile.setOriginalFilename(originalFilename);
                fileRepository.save(existingFile);
            } else {
                FileEntity fileEntity = new FileEntity();
                fileEntity.setOriginalFilename(originalFilename);
                fileEntity.setStoredFilename(destinationFilePath.getFileName().toString());
                fileEntity.setFilepath(destinationFilePath.toString());
                fileRepository.save(fileEntity);
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFilePath, StandardCopyOption.REPLACE_EXISTING);
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
    public Path load(String filepath) {
        Path destinationFilePath = loadSafe(filepath);

        if (!Files.exists(destinationFilePath)) {
            throw new StorageFileNotFoundException("File not found: " + filepath);
        }
        return destinationFilePath;
    }

    @Override
    public FileResource loadAsResource(String filepath) {
        try {
            Path file = load(filepath);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return new FileResource(resource, filepath);
            } else {
                throw new StorageFileNotFoundException(
                        "Could not read file: " + filepath);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filepath, e);
        }
    }

    @Transactional
    public void deleteFile(String filepath) {
        Path destinationFilePath = loadSafe(filepath);

        FileEntity fileEntity = fileRepository.findByFilepath(destinationFilePath.toString());
        if (fileEntity == null) {
            throw new StorageFileNotFoundException("File not found: " + filepath);
        }
        try {
            Files.deleteIfExists(destinationFilePath);
            fileRepository.delete(fileEntity);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + filepath, e);
        }
    }

    public Path loadSafe(String filepath) throws StorageException {
        Path path = Paths.get(filepath).normalize();
        if (path.isAbsolute()) {
            throw new StorageException("Absolute paths are not allowed.");
        }

        Path destinationFilePath = this.rootLocation.resolve(path).normalize().toAbsolutePath();
        if (!destinationFilePath.startsWith(this.rootLocation.toAbsolutePath())) {
            throw new StorageException("Cannot access files/folders outside the root directory.");
        }

        return destinationFilePath;
    }

//    @Override
//    public void deleteAll() {
//        fileRepository.deleteAll();
//        FileSystemUtils.deleteRecursively(rootLocation.toFile());
//    }
}