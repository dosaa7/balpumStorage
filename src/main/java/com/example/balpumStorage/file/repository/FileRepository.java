package com.example.balpumStorage.file.repository;

import com.example.balpumStorage.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    FileEntity findByStoredFilename(String filename);
    FileEntity findByFilepath(String filepath);
}
