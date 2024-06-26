package com.example.balpumStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.balpumStorage.file.entity.FileEntity;
import com.example.balpumStorage.file.resource.FileResource;
import com.example.balpumStorage.storage.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.balpumStorage.storage.StorageFileNotFoundException;
import com.example.balpumStorage.storage.StorageService;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;

@RequestMapping("/api/files")
@RestController
public class FileUploadController {

    private final StorageService storageService;

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/image-url")
    public ResponseEntity<String> getImageUrl(@RequestBody Map<String, String> requestBody) {
        String filepath = requestBody.get("ref");
        String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/images/")
                .path(filepath)
                .toUriString();

        return ResponseEntity.ok(fileUrl);
    }

    @GetMapping("/images/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String filepath = new UrlPathHelper().getPathWithinApplication(request);
        filepath = filepath.substring("/api/files/images/".length());
        FileResource fileResource = storageService.loadAsResource(filepath);

        if (fileResource == null) return ResponseEntity.notFound().build();

        String contentType;
        try {
            Path path = Paths.get(fileResource.getFilepath());
            contentType = Files.probeContentType(path);

            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE; // 기본값
            }
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE; // 예외 발생 시 기본값
        }

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileResource.getFilepath() + "\"").contentType(MediaType.parseMediaType(contentType)).body(fileResource.getResource());
    }

    @GetMapping("/image-url-list")
    public ResponseEntity<List<String>> getAllImageUrls(@RequestBody Map<String, String> requestBody) {
        String directory = requestBody.get("ref");
        Stream<String> files = storageService.loadAllFilesUnderPath(directory);
        List<String> fileUrls = files.map(path ->
                ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/files/images/")
                    .path(path)
                    .toUriString()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(fileUrls);
    }

    @PostMapping("/")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("ref") String refPath) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please select a file to upload.");
        }

        try {
            storageService.store(file, refPath);
            return ResponseEntity.status(HttpStatus.CREATED).body("You successfully uploaded " + file.getOriginalFilename() + " with reference path " + refPath + "!");
        } catch (StorageException e) {
            if (e.getMessage().contains("File with the same name already exists.")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
        }
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteFile(@RequestBody Map<String, String> requestBody) {
        String filepath = requestBody.get("ref");
        try {
            storageService.deleteFile(filepath);
            return ResponseEntity.noContent().build();
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }
}