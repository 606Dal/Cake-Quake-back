package com.cakequake.cakequakeback.cake.item.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

//	private String uploadDir = "C:\\nginx-1.26.3\\html\\uploads";

	@Value("${file.upload.upload-dir}")
	private String uploadDir;

	@Override
	public String storeFile(MultipartFile file) {
		log.debug("---FileStorageServiceImpl---storeFile");
		// 원본 파일명 정리
		String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
		// 확장자 포함 랜덤 UUID 파일명 생성
		String fileExtension = "";
		int extIndex = originalFilename.lastIndexOf(".");
		if (extIndex > 0) {
			fileExtension = originalFilename.substring(extIndex);
		}
//		String newFileName = UUID.randomUUID().toString() + fileExtension;
		String newFileName = UUID.randomUUID().toString() + "_" + originalFilename;
		log.debug("---storeFile---newFileName: {}", newFileName);

		try {
			log.debug("---FileStorageServiceImpl---storeFile---파일 생성");
			Path targetLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
			Files.createDirectories(targetLocation);
			log.debug("---storeFile---targetLocation: {}", targetLocation);

			Path filePath = targetLocation.resolve(newFileName);
			log.debug("---storeFile---filePath: {}", filePath.toFile());
			file.transferTo(filePath.toFile());

		} catch (IOException e) {
			throw new RuntimeException("파일 저장 실패: " + originalFilename, e);
		}

		try {
			log.debug("---FileStorageServiceImpl---storeFile---썸네일 생성");
			File thumbnailFile = new File(uploadDir, "s_" + newFileName);
			Thumbnails.of(new File(uploadDir, newFileName))
					.size(200, 200)
					.toFile(thumbnailFile);

		} catch (Exception e) {
			log.error("썸네일 생성 실패: {}", newFileName, e);
		}

		return newFileName;
	}

	@Override
	public void deleteFile(String fileUrl) {
		try {
			log.debug("---FileStorageServiceImpl====deleteFile");
			// fileUrl 에서 파일명만 추출 (예: "/uploads/abc.jpg" -> "abc.jpg")
			String fileName = Paths.get(fileUrl).getFileName().toString();
			Path filePath = Paths.get(uploadDir).resolve(fileName).toAbsolutePath().normalize();

			Files.deleteIfExists(filePath);

			// 썸네일 삭제
			String thumbnailName = "s_" + fileName;
			Path thumbnailPath = Paths.get(uploadDir).resolve(thumbnailName).toAbsolutePath().normalize();
			Files.deleteIfExists(thumbnailPath);

		} catch (IOException e) {
			throw new RuntimeException("파일 삭제 실패: " + fileUrl, e);
		}
	}
}