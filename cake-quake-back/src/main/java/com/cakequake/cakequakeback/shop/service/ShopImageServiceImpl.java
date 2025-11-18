package com.cakequake.cakequakeback.shop.service;

import com.cakequake.cakequakeback.cake.item.service.FileStorageService;
import com.cakequake.cakequakeback.shop.dto.ImageResponseDTO;
import com.cakequake.cakequakeback.shop.dto.ShopImageDTO;
import com.cakequake.cakequakeback.shop.dto.ShopImageUpdateDTO;
import com.cakequake.cakequakeback.shop.entities.Shop;
import com.cakequake.cakequakeback.shop.entities.ShopImage;
import com.cakequake.cakequakeback.shop.repo.ShopImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j

public class ShopImageServiceImpl implements ShopImageService {
    private final ShopImageRepository shopImageRepository;
    private final FileStorageService fileStorageService;

    //매장 이미지 저장
    @Override
    public ImageResponseDTO saveShopImages(Shop shop, List<ShopImageDTO> shopImageDTOs, List<MultipartFile> imageFiles, String thumbnailFile) {
        List<ShopImageDTO> savedShopImages = new ArrayList<>();
        String thumbnailUrl = null;

        if(shopImageDTOs != null) {
            for(ShopImageDTO dto : shopImageDTOs) {
                ShopImage shopImage = ShopImage.builder()
                        .shop(shop)
                        .shopImageUrl(dto.getShopImageUrl())
                        .isThumbnail(dto.getIsThumbnail())
                        .build();

                shopImageRepository.save(shopImage);

                savedShopImages.add(new ShopImageDTO(shopImage.getShopImageId(), dto.getShopImageUrl(), dto.getIsThumbnail()));

                if(Boolean.TRUE.equals(dto.getIsThumbnail())) {
                    thumbnailUrl=dto.getShopImageUrl();
                } //end if
            } //end for
        } //end if

        if(imageFiles != null) {
            for(MultipartFile file : imageFiles) {
                String originalFilename = file.getOriginalFilename();
                String url = saveFileAndGetUrl(file);

                boolean isThumbnail = originalFilename != null && originalFilename.equals(thumbnailFile);

                ShopImage shopImage = ShopImage.builder()
                        .shop(shop)
                        .shopImageUrl(url)
                        .isThumbnail(isThumbnail)
                        .build();
                shopImageRepository.save(shopImage);

                savedShopImages.add(new ShopImageDTO(shopImage.getShopImageId(), url, isThumbnail));

                if(isThumbnail) {
                    thumbnailUrl=url;
                }//end if

            }//end for
        }//end if

        return new ImageResponseDTO(savedShopImages, thumbnailUrl);
    }

    //이미지 정보 업데이트 (개선된 로직)
    @Override
    public ImageResponseDTO updateShopImages(
            Shop shop,
            List<MultipartFile> newImageFiles,
            List<ShopImageUpdateDTO> imageDTOList
    ) {
//		log.debug("DEBUG: [ShopImageService] updateShopImages 메서드 시작.");

        List<ShopImage> existingImages = shopImageRepository.findByShop(shop);

        // 1. 기존 이미지 삭제 로직 (유지할 목록에 없는 이미지는 삭제)
        Set<Long> keepIds = imageDTOList.stream()
                .map(ShopImageUpdateDTO::getShopImageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ShopImage img : existingImages) {
            if (!keepIds.contains(img.getShopImageId())) {
                fileStorageService.deleteFile(img.getShopImageUrl());
                shopImageRepository.delete(img);
            }
        }

        // 2. 새 이미지 저장 + 기존 이미지 썸네일 false 처리

        // 기존 이미지 썸네일 모두 초기화
        List<ShopImage> remainImagesAfterDeletion = shopImageRepository.findByShop(shop); // 삭제 후 남아있는 이미지 다시 조회 (정확성을 위해)

        for (ShopImage shopImage : remainImagesAfterDeletion) {
            if (shopImage.getIsThumbnail()) {
                shopImage.deleteThumbnail(); // isThumbnail을 false로 설정하는 메서드
                shopImageRepository.save(shopImage);
            }
        }

        Map<Integer, ShopImage> savedNewImageMap = new HashMap<>();
        List<ShopImageDTO> savedShopImageDTOs = new ArrayList<>(); // 새로 저장된 이미지의 DTO 목록 (응답용)

        // index 로 새 파일 매칭
        for (ShopImageUpdateDTO dto : imageDTOList) {

            // 새 이미지인 경우
            if (Boolean.TRUE.equals(dto.getIsNew())) {

                MultipartFile file = newImageFiles.get(dto.getOrderIndex());
                String savedUrl = saveFileAndGetUrl(file);

                ShopImage newImg = ShopImage.builder()
                        .shop(shop)
                        .shopImageUrl(savedUrl)
                        .isThumbnail(false)
                        .build();

                shopImageRepository.save(newImg);
                savedNewImageMap.put(dto.getOrderIndex(), newImg);

                savedShopImageDTOs.add(ShopImageDTO.builder()
                        .shopImageId(newImg.getShopImageId())
                        .shopImageUrl(savedUrl)
                        .isThumbnail(false)
                        .build());
            } // end if
        } // end for

        // 3. 썸네일 다시 설정
        ShopImage finalThumbnail = null;

        for (int i = 0; i < imageDTOList.size(); i++) {
            ShopImageUpdateDTO dto = imageDTOList.get(i);

            if (Boolean.TRUE.equals(dto.getIsThumbnail())) {
                if (Boolean.TRUE.equals(dto.getIsNew())) {
                    finalThumbnail = savedNewImageMap.get(dto.getOrderIndex());
                } else {
                    finalThumbnail = shopImageRepository.findById(dto.getShopImageId()).orElse(null);
                }
                break;
            }
        } // end for

        if (finalThumbnail != null) {
            finalThumbnail.changeThumbnail();
            shopImageRepository.save(finalThumbnail);
        }

        String thumbnailUrl = finalThumbnail != null ? finalThumbnail.getShopImageUrl() : null;

        return ImageResponseDTO.builder()
                .shopImageDTOS(savedShopImageDTOs)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    @Override
    public String saveFileAndGetUrl(MultipartFile file){
        return fileStorageService.storeFile(file);
    }


    @Override
    @Transactional(readOnly = true)
    public List<ShopImageDTO> findShop(Shop shop, Long shopId){
        return shopImageRepository.findShopImages(shopId);
    }
}