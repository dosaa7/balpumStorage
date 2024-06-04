package com.example.balpumStorage.file.resource;

import org.springframework.core.io.Resource;

public class FileResource {
    private final Resource resource;
    private final String originalFilename;

    public FileResource(Resource resource, String originalFilename) {
        this.resource = resource;
        this.originalFilename = originalFilename;
    }

    public Resource getResource() {
        return resource;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }
}
