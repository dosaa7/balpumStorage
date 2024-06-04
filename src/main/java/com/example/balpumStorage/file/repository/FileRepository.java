package com.example.balpumStorage.file.repository;

import com.example.balpumStorage.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    FileEntity findByFilename(String filename);
}
