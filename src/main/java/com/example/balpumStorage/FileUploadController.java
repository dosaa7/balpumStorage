package com.example.balpumStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import com.example.balpumStorage.file.entity.FileEntity;
import com.example.balpumStorage.file.resource.FileResource;
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

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService.loadAll().map(path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class, "serveFile", path.getFileName().toString()).build().toUri().toString()).collect(Collectors.toList()));
        // 파일 절대 경로로 매핑하는 코드
//        model.addAttribute("files", storageService.loadAll()
//                .map(Path::toString)
//                .collect(Collectors.toList()));

        return "uploadForm";
    }

    @GetMapping("/image-url")
    public ResponseEntity<String> getImageUrl(@RequestParam String filepath) {
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

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileResource.getFilepath() + "\"").body(fileResource.getResource());
    }

    @PostMapping("/")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("ref") String refPath) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please select a file to upload.");
        }

        try {
            storageService.store(file, refPath);
            return ResponseEntity.status(HttpStatus.CREATED).body("You successfully uploaded " + file.getOriginalFilename() + " with reference path " + refPath + "!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
        }
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> deleteFile(HttpServletRequest request) {
        try {
            String filepath = new UrlPathHelper().getPathWithinApplication(request);
            filepath = filepath.substring("/api/files/".length());
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

    @GetMapping("/fileDetails/{filename:.+}")
    @ResponseBody
    public ResponseEntity<FileEntity> getFileDetails(@PathVariable String filename) {
        try {
            FileEntity fileEntity = storageService.getFileDetails(filename);
            return ResponseEntity.ok(fileEntity);
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/fileDetails/{filename:.+}")
    @ResponseBody
    public ResponseEntity<FileEntity> updateFileDetails(@PathVariable String filename, @RequestParam String newOriginalFilename) {
        try {
            FileEntity updatedFileEntity = storageService.updateFileDetails(filename, newOriginalFilename);
            return ResponseEntity.ok(updatedFileEntity);
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}