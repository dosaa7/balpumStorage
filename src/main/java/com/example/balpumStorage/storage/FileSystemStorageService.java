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
    public void store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file.");
            }
            // UUID를 사용하여 저장할 파일명 생성
            String originalFilename = file.getOriginalFilename();
            String storedFilename = UUID.randomUUID().toString() + "-" + originalFilename;
            // 디렉토리 생성 (UUID 앞 두자리 기준)
            Path directory = this.rootLocation.resolve(Paths.get(storedFilename.substring(0, 2)))
                    .normalize().toAbsolutePath();
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                // 디렉토리 권한 설정
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxrwxrwx");
                Files.setPosixFilePermissions(directory, permissions);
            }

            Path destinationFile = directory.resolve(Paths.get(storedFilename)).normalize().toAbsolutePath();
            if (!destinationFile.getParent().startsWith(this.rootLocation.toAbsolutePath())) {
                // This is a security check
                throw new StorageException("Cannot store file outside current directory.");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile,
                        StandardCopyOption.REPLACE_EXISTING);
                // 파일 정보를 데이터베이스에 저장
                FileEntity fileEntity = new FileEntity();
                fileEntity.setOriginalFilename(originalFilename);
                fileEntity.setStoredFilename(storedFilename);
                fileEntity.setFilepath(destinationFile.toString());
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

    @Override
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
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