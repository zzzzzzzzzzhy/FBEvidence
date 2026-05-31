package com.evidence.exception;

public class RepositoryNotFoundException extends RuntimeException {
    
    public RepositoryNotFoundException(String message) {
        super(message);
    }
    
    public RepositoryNotFoundException(Long repositoryId) {
        super("Repository not found: " + repositoryId);
    }
}