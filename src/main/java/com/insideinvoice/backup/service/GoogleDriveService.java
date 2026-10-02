package com.insideinvoice.backup.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.GenericData;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.http.InputStreamContent;
import com.insideinvoice.backup.config.BackupProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "Inside Invoice Backup";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BackupProperties backupProperties;

    private Drive driveService;

    private synchronized Drive getDriveService() throws IOException, GeneralSecurityException {
        if (driveService != null) {
            return driveService;
        }

        NetHttpTransport httpTransport = new NetHttpTransport();

        String clientId = backupProperties.getGoogleDrive().getClientId();
        String clientSecret = backupProperties.getGoogleDrive().getClientSecret();
        String refreshToken = backupProperties.getGoogleDrive().getRefreshToken();

        GenericData requestData = new GenericData();
        requestData.put("client_id", clientId);
        requestData.put("client_secret", clientSecret);
        requestData.put("refresh_token", refreshToken);
        requestData.put("grant_type", "refresh_token");

        HttpContent content = new UrlEncodedContent(requestData);

        HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
        HttpRequest request = requestFactory.buildPostRequest(
                new GenericUrl("https://oauth2.googleapis.com/token"),
                content);

        String responseBody;
        try {
            HttpResponse response = request.execute();
            responseBody = response.parseAsString();
        } catch (HttpResponseException e) {
            log.error("Failed to get access token: {}", e.getMessage());
            throw new IOException("Failed to get access token: " + e.getMessage(), e);
        }

        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("access_token").asText();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
                .build();
        credential.setAccessToken(accessToken);

        driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        log.info("Google Drive service initialized successfully");
        return driveService;
    }

    public String findOrCreateFolder(String parentId, String folderName, String mimeType) throws IOException, GeneralSecurityException {
        Drive drive = getDriveService();

        String query = String.format(
                "'%s' in parents and name = '%s' and mimeType = '%s' and trashed = false",
                parentId, folderName, mimeType);

        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Found existing folder: {} (id: {})", folderName, folderId);
            return folderId;
        }

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType(mimeType);
        folderMetadata.setParents(Collections.singletonList(parentId));

        File createdFolder = drive.files().create(folderMetadata)
                .setFields("id")
                .execute();

        log.info("Created folder: {} (id: {})", folderName, createdFolder.getId());
        return createdFolder.getId();
    }

    public boolean fileExists(String parentId, String fileName) throws IOException, GeneralSecurityException {
        Drive drive = getDriveService();

        String query = String.format(
                "'%s' in parents and name = '%s' and trashed = false",
                parentId, fileName);

        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();

        return result.getFiles() != null && !result.getFiles().isEmpty();
    }

    public String uploadFile(String parentId, String fileName, Path filePath, String mimeType) throws IOException, GeneralSecurityException {
        Drive drive = getDriveService();

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentId));

        InputStreamContent mediaContent = new InputStreamContent(mimeType, new FileInputStream(filePath.toFile()));
        mediaContent.setLength(Files.size(filePath));

        File uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size")
                .execute();

        log.info("Uploaded file: {} (id: {}, size: {} bytes)", fileName, uploadedFile.getId(), uploadedFile.getSize());
        return uploadedFile.getId();
    }

    public void deleteFile(String fileId) throws IOException, GeneralSecurityException {
        Drive drive = getDriveService();
        drive.files().delete(fileId).execute();
        log.info("Deleted file with id: {}", fileId);
    }

    public List<File> listFiles(String parentId) throws IOException, GeneralSecurityException {
        Drive drive = getDriveService();

        String query = String.format("'%s' in parents and trashed = false", parentId);

        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, size)")
                .execute();

        return result.getFiles() != null ? result.getFiles() : Collections.emptyList();
    }
}
