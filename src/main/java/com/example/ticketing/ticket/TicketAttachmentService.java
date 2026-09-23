package com.example.ticketing.ticket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

@Service
public class TicketAttachmentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "text/plain",
        "application/pdf"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final Path uploadDir;

    public TicketAttachmentService(
            TicketAttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            @Value("${app.upload.dir:uploads}") String uploadDirPath) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }
    
    public TicketAttachment uploadAttachment(
            Long ticketId,
            MultipartFile file,
            String uploadedBy) {
        
        validateFile(file);
        
        // Fetch the actual ticket from database
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        
        // Generate unique filename
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        
        // Create ticket-specific subdirectory
        Path ticketDir = uploadDir.resolve("tickets").resolve(ticketId.toString());
        try {
            Files.createDirectories(ticketDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create ticket directory", e);
        }
        
        Path targetPath = ticketDir.resolve(uniqueFileName);
        
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
        
        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileName(uniqueFileName);
        attachment.setOriginalName(originalName != null ? originalName : "unknown");
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setStoragePath(targetPath.toString());
        attachment.setUploadedBy(uploadedBy);
        
        return attachmentRepository.save(attachment);
    }

    public Resource downloadAttachment(Long attachmentId) {
        TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        
        Path filePath = Paths.get(attachment.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
        }
        
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not read file");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }
    }

    public TicketAttachment getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }

    public List<TicketAttachment> getAttachmentsByTicketId(Long ticketId) {
        return attachmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "File size exceeds maximum allowed size of 10MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "File type not allowed. Allowed types: JPG, PNG, GIF, WEBP, TXT, PDF");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }
}
