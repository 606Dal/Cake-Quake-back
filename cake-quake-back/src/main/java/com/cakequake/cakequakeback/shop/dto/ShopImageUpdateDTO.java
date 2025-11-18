package com.cakequake.cakequakeback.shop.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopImageUpdateDTO {

	private Long shopImageId;   // 기존 이미지면 ID 존재, 새 이미지면 null
	private String shopImageUrl; // 기존 이미지면 URL, 새 이미지면 임시 uuid 또는 preview string
	private Boolean isThumbnail; // 썸네일 여부
	private Boolean isNew;       // 새 이미지 여부
	private Integer orderIndex;  // 업로드 순서 (배열 index)

	@Override
	public String toString() {
		return "ShopImageUpdateDTO{" +
				"shopImageId=" + shopImageId +
				", isThumbnail=" + isThumbnail +
				", isNew=" + isNew +
				", orderIndex=" + orderIndex +
				'}';
	}
}