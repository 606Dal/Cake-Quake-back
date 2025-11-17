package com.cakequake.cakequakeback.cake.item.service;

import com.cakequake.cakequakeback.common.exception.BusinessException;
import com.cakequake.cakequakeback.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
		String newFileName = UUID.randomUUID().toString() + "_" + originalFilename;

		Path targetLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
		Path filePath = targetLocation.resolve(newFileName);

		try {
			Files.createDirectories(targetLocation);

			String contentType = file.getContentType();
			if (contentType == null || !contentType.startsWith("image")) {
				throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
			}

			BufferedImage originalImage = ImageIO.read(file.getInputStream());
			if (originalImage == null) {
				throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
			}
			log.debug("---storeFile--- 이미지 해상도: {}x{}", originalImage.getWidth(), originalImage.getHeight());

			file.transferTo(filePath.toFile());

			// 썸네일 생성
			File thumbnailFile = new File(uploadDir, "s_" + newFileName);
			log.debug("---storeFile---썸네일 생성 시작");

			Thumbnails.of(filePath.toFile())
					.size(200, 200)
					.outputQuality(0.9)
					.toFile(thumbnailFile);

		} catch (IOException e) {
			throw new RuntimeException("파일 저장 실패: " + originalFilename, e);
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