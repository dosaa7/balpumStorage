package com.example.balpumStorage.file.resource;

import org.springframework.core.io.Resource;

public class FileResource {
    private final Resource resource;
    private final String filepath;

    public FileResource(Resource resource, String filepath) {
        this.resource = resource;
        this.filepath = filepath;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFilepath() {
        return filepath;
    }
}
