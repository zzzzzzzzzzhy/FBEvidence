package com.evidence.exception;

public class BranchNotFoundException extends RuntimeException {
    
    public BranchNotFoundException(String message) {
        super(message);
    }
    
    public BranchNotFoundException(String branchName, Long repositoryId) {
        super("Branch not found: " + branchName + " in repository: " + repositoryId);
    }
}